package net.corda.bn.custom.contracts

import net.corda.bn.contracts.MembershipContract
import net.corda.bn.states.BNIdentity
import net.corda.bn.states.BNORole
import net.corda.bn.states.MembershipIdentity
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class DummyContract : Contract {

    companion object {
        const val CONTRACT_NAME = "net.corda.bn.contracts.DummyContract"
    }

    override fun verify(tx: LedgerTransaction) {}
}

class DummyCommand : TypeOnlyCommandData()

@CordaSerializable
private data class DummyIdentity(val name: String) : BNIdentity

class MembershipContractTest {

    private val ledgerServices = MockServices(listOf("net.corda.bn.contracts", "net.corda.bn.custom.contracts"))

    private val memberIdentity = TestIdentity(CordaX500Name.parse("O=Member,L=London,C=GB")).party
    private val bnoIdentity = TestIdentity(CordaX500Name.parse("O=BNO,L=London,C=GB")).party

    private val membershipState = MembershipState(
            identity = MembershipIdentity(memberIdentity),
            networkId = "network-id",
            status = MembershipStatus.PENDING,
            participants = listOf(memberIdentity, bnoIdentity)
    )

    @Test(timeout = 300_000)
    fun `test common contract verification`() {
        ledgerServices.ledger {
            transaction {
                output(CustomMembershipContract.CONTRACT_NAME, membershipState)
                command(listOf(memberIdentity.owningKey, bnoIdentity.owningKey), MembershipContract.Commands.Request(listOf(memberIdentity.owningKey, bnoIdentity.owningKey)))
                this `fails with` "Output membership must have non trivial business identity since it is important for BNO for activation decision"
            }
        }
    }
}
