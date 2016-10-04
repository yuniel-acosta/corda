package com.r3corda.node.services

import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.core.contracts.Command
import com.r3corda.core.contracts.DummyContract
import com.r3corda.core.contracts.TransactionType
import com.r3corda.core.crypto.DigitalSignature
import com.r3corda.core.node.services.ServiceInfo
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.core.utilities.DUMMY_NOTARY
import com.r3corda.core.utilities.DUMMY_NOTARY_KEY
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.transactions.ValidatingNotaryService
import com.r3corda.protocols.NotaryError
import com.r3corda.protocols.NotaryException
import com.r3corda.protocols.NotaryProtocol
import com.r3corda.testing.MEGA_CORP_KEY
import com.r3corda.testing.MINI_CORP_KEY
import com.r3corda.testing.node.MockNetwork
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValidatingNotaryServiceTests {
    lateinit var net: MockNetwork
    lateinit var notaryNode: MockNetwork.MockNode
    lateinit var clientNode: MockNetwork.MockNode

    @Before fun setup() {
        net = MockNetwork()
        notaryNode = net.createNode(
                legalName = DUMMY_NOTARY.name,
                keyPair = DUMMY_NOTARY_KEY,
                advertisedServices = *arrayOf(ServiceInfo(NetworkMapService.Type), ServiceInfo(ValidatingNotaryService.Type))
        )
        clientNode = net.createNode(networkMapAddress = notaryNode.info.address, keyPair = MINI_CORP_KEY)
        net.runNetwork() // Clear network map registration messages
    }

    @Test fun `should report error for invalid transaction dependency`() {
        val stx = run {
            val inputState = issueInvalidState(clientNode)
            val tx = TransactionType.General.Builder(DUMMY_NOTARY).withItems(inputState)
            tx.signWith(clientNode.keyPair!!)
            tx.toSignedTransaction(false)
        }

        val future = runClient(stx)

        val ex = assertFailsWith(ExecutionException::class) { future.get() }
        val notaryError = (ex.cause as NotaryException).error
        assertThat(notaryError).isInstanceOf(NotaryError.TransactionInvalid::class.java)
    }

    @Test fun `should report error for missing signatures`() {
        val expectedMissingKey = MEGA_CORP_KEY.public
        val stx = run {
            val inputState = issueState(clientNode)

            val command = Command(DummyContract.Commands.Move(), expectedMissingKey)
            val tx = TransactionType.General.Builder(DUMMY_NOTARY).withItems(inputState, command)
            tx.signWith(clientNode.keyPair!!)
            tx.toSignedTransaction(false)
        }

        val future = runClient(stx)

        val ex = assertFailsWith(ExecutionException::class) { future.get() }
        val notaryError = (ex.cause as NotaryException).error
        assertThat(notaryError).isInstanceOf(NotaryError.SignaturesMissing::class.java)

        val missingKeys = (notaryError as NotaryError.SignaturesMissing).missingSigners
        assertEquals(setOf(expectedMissingKey), missingKeys)
    }

    private fun runClient(stx: SignedTransaction): ListenableFuture<DigitalSignature.LegallyIdentifiable> {
        val protocol = NotaryProtocol.Client(stx)
        val future = clientNode.services.startProtocol("notary", protocol)
        net.runNetwork()
        return future
    }
}