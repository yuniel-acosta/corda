package net.corda.bn.demo.workflows

import net.corda.bn.demo.contracts.LoanContract
import net.corda.bn.demo.contracts.LoanState
import net.corda.bn.flows.IllegalMembershipStatusException
import net.corda.bn.flows.MembershipAuthorisationException
import net.corda.bn.flows.MembershipNotFoundException
import net.corda.bn.states.MembershipState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IssueLoanFlowTest : LoanFlowTest(numberOfLenders = 1, numberOfBorrowers = 1) {

    @Test(timeout = 300_000)
    fun `issue loan flow should fail if one of the parties is not member of business network`() {
        val lender = lenders.first()
        val borrower = borrowers.first()

        // both lender and borrower are not members of a business network
        val illegalNetworkId = "network-id"
        assertFailsWith<MembershipNotFoundException> { runIssueLoanFlow(lender, illegalNetworkId, borrower.identity(), 10) }

        // now only borrower is not member of a business network
        val networkId = (runCreateBusinessNetworkFlow(lender).tx.outputStates.single() as MembershipState).networkId
        assertFailsWith<MembershipNotFoundException> { runIssueLoanFlow(lender, networkId, borrower.identity(), 10) }
    }

    @Test(timeout = 300_000)
    fun `issue loan flow should fail if one of the parties is not active member of business network`() {
        val lender = lenders.first()
        val borrower = borrowers.first()

        // borrower's membership is in pending status
        val networkId = (runCreateBusinessNetworkFlow(lender).tx.outputStates.single() as MembershipState).networkId
        runRequestMembershipFlow(borrower, lender.identity(), networkId)
        assertFailsWith<IllegalMembershipStatusException> { runIssueLoanFlow(lender, networkId, borrower.identity(), 10) }
    }

    @Test(timeout = 300_000)
    fun `issue loan flow should fail if one of the parties does not have bank identity`() {
        val lender = lenders.first()
        val borrower = borrowers.first()

        // both lender and borrower don't have bank identity
        val networkId = (runCreateBusinessNetworkFlow(lender).tx.outputStates.single() as MembershipState).networkId
        runRequestMembershipFlow(borrower, lender.identity(), networkId).apply {
            val membership = tx.outputStates.single() as MembershipState
            runActivateMembershipFlow(lender, membership.linearId)
        }
        assertFailsWith<IllegalMembershipBusinessIdentityException> { runIssueLoanFlow(lender, networkId, borrower.identity(), 10) }

        // now only borrower doesn't have bank identity
        val bic = "BANKGB00"
        runAssignBICFlow(lender, networkId, bic)
        assertFailsWith<IllegalMembershipBusinessIdentityException> { runIssueLoanFlow(lender, networkId, borrower.identity(), 10) }
    }

    @Test(timeout = 300_000)
    fun `issue loan flow should fail if lender is not authorised to run issue loans`() {
        val lender = lenders.first()
        val borrower = borrowers.first()

        val networkId = (runCreateBusinessNetworkFlow(lender).tx.outputStates.single() as MembershipState).networkId
        runRequestMembershipFlow(borrower, lender.identity(), networkId).apply {
            val membership = tx.outputStates.single() as MembershipState
            runActivateMembershipFlow(lender, membership.linearId)
        }
        val bic = "BANKGB00"
        listOf(lender, borrower).forEach { runAssignBICFlow(it, networkId, bic) }

        assertFailsWith<MembershipAuthorisationException> { runIssueLoanFlow(lender, networkId, borrower.identity(), 10) }
    }

    @Test(timeout = 300_000)
    fun `issue loan flow happy path`() {
        val lender = lenders.first()
        val borrower = borrowers.first()

        val networkId = (runCreateBusinessNetworkFlow(lender).tx.outputStates.single() as MembershipState).networkId
        runRequestMembershipFlow(borrower, lender.identity(), networkId).apply {
            val membership = tx.outputStates.single() as MembershipState
            runActivateMembershipFlow(lender, membership.linearId)
        }
        val bic = "BANKGB00"
        listOf(lender, borrower).forEach { runAssignBICFlow(it, networkId, bic) }
        runAssignLoanIssuerRole(lender, networkId)

        val (loan, command) = runIssueLoanFlow(lender, networkId, borrower.identity(), 10).run {
            assertTrue(tx.inputs.isEmpty())
            verifyRequiredSignatures()
            tx.outputs.single() to tx.commands.single()
        }

        loan.apply {
            assertEquals(LoanContract.CONTRACT_NAME, loan.contract)
            assertTrue(data is LoanState)
            val data = data as LoanState
            assertEquals(lender.identity(), data.lender)
            assertEquals(borrower.identity(), data.borrower)
            assertEquals(10, data.amount)
            assertEquals(networkId, data.networkId)
        }
    }
}