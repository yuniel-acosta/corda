package net.corda.confidential

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IdentitySyncFlowTests {
    private lateinit var mockNet: InternalMockNetwork

    @Before
    fun before() {
        // We run this in parallel threads to help catch any race conditions that may exist.
        mockNet = InternalMockNetwork(
                cordappsForAllNodes = FINANCE_CORDAPPS,
                networkSendManuallyPumped = false,
                threadPerNode = true
        )
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    /**
     * Very lightweight wrapping flow to trigger the counterparty flow that receives the identities.
     */
    @InitiatingFlow
    class Initiator(private val otherSide: Party, private val tx: WireTransaction) : FlowLogic<Boolean>() {
        @Suspendable
        override fun call(): Boolean {
            val session = initiateFlow(otherSide)
            subFlow(IdentitySyncFlow.Send(session, tx))
            // Wait for the counterparty to indicate they're done
            return session.receive<Boolean>().unwrap { it }
        }
    }

    @InitiatedBy(IdentitySyncFlowTests.Initiator::class)
    class Receive(private val otherSideSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(IdentitySyncFlow.Receive(otherSideSession))
            // Notify the initiator that we've finished syncing
            otherSideSession.send(true)
        }
    }
}