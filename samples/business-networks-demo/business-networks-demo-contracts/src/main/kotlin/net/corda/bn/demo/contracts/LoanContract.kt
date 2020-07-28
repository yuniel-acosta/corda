package net.corda.bn.demo.contracts

import net.corda.bn.states.MembershipState
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException

/**
 * Contract that verifies an evolution of [LoanState].
 */
class LoanContract : Contract {

    companion object {
        const val CONTRACT_NAME = "net.corda.bn.demo.contracts.LoanContract"
    }

    /**
     * Each new [LoanContract] command must be wrapped and extend this class.
     */
    open class Commands : TypeOnlyCommandData() {
        /**
         * Command responsible for [LoanState] issuance.
         */
        class Issue : Commands()

        /**
         * Command responsible for [LoanState] partial settlement.
         */
        class Settle : Commands()

        /**
         * Command responsible for [LoanState] full settlement and exit.
         */
        class Exit : Commands()
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val input = if (tx.inputStates.isNotEmpty()) tx.inputs.single() else null
        val inputState = input?.state?.data as? LoanState
        val output = if (tx.outputStates.isNotEmpty()) tx.outputs.single() else null
        val outputState = output?.data as? LoanState

        requireThat {
            input?.apply {
                "Input state has to be validated by $CONTRACT_NAME" using (state.contract == CONTRACT_NAME)
            }
            inputState?.apply {
                "Input state's participants list should contain lender and borrower only" using (participants.toSet() == setOf(lender, borrower))
            }
            output?.apply {
                "Output state has to be validated by $CONTRACT_NAME" using (contract == CONTRACT_NAME)
            }
            outputState?.apply {
                "Output state's participants list should contain lender and borrower only" using (participants.toSet() == setOf(lender, borrower))
            }
            if (inputState != null && outputState != null) {
                "Input and output state should have same lender" using (inputState.lender == outputState.lender)
                "Input and output state should have same borrower" using (inputState.borrower == outputState.borrower)
                "Input and output state should have same network ID" using (inputState.networkId == outputState.networkId)
                "Input and output state should have same linear ID" using (inputState.linearId == outputState.linearId)
                "Input and output state should have same set of participants" using (inputState.participants.toSet() == outputState.participants.toSet())
                "Output state should have lower amount than input state" using (outputState.amount < inputState.amount)
            }
        }

        when (command.value) {
            is Commands.Issue -> verifyIssue(tx)
            is Commands.Settle -> verifySettle(tx, inputState!!.lender, inputState.borrower)
            is Commands.Exit -> verifyExit(tx, inputState!!.lender, inputState.borrower)
            else -> throw IllegalArgumentException("Unsupported command ${command.value}")
        }
    }

    /**
     * Contract verification check specific to [Commands.Issue] command.
     *
     * @param tx Ledger transaction over which contract performs verification.
     */
    private fun verifyIssue(tx: LedgerTransaction) = requireThat {
        "Loan issuance transaction shouldn't contain any inputs" using (tx.inputs.isEmpty())
    }

    /**
     * Contract verification check specific to [Commands.Settle] command.
     *
     * @param tx Ledger transaction over which contract performs verification.
     */
    private fun verifySettle(tx: LedgerTransaction, lender: Party, borrower: Party) = verifyMemberships(tx, lender, borrower)

    /**
     * Contract verification check specific to [Commands.Exit] command.
     *
     * @param tx Ledger transaction over which contract performs verification.
     */
    private fun verifyExit(tx: LedgerTransaction, lender: Party, borrower: Party) {
        requireThat {
            "Loan exit transaction shouldn't contain any outputs" using (tx.outputs.isEmpty())
        }
        verifyMemberships(tx, lender, borrower)
    }

    /**
     * Contract verification check over reference [MembershipState]s.
     *
     * @param tx Ledger transaction over which contract performs verification.
     * @param lender Party issuing the loan.
     * @param borrower Party paying of the loan.
     */
    private fun verifyMemberships(tx: LedgerTransaction, lender: Party, borrower: Party) = requireThat {
        "Loan settlement transaction should have 2 reference states" using (tx.referenceStates.size == 2)
        "Loan settlement transaction should contain only reference MembershipStates" using (tx.referenceStates.all { it is MembershipState })
        val membershipReferenceStates = tx.referenceStates.map { it as MembershipState }
        val lenderMembership = membershipReferenceStates.find { it.identity.cordaIdentity == lender }
        val borrowerMembership = membershipReferenceStates.find { it.identity.cordaIdentity == borrower }
        "Loan settlement transaction should have lender's reference membership state" using (lenderMembership != null)
        "Loan settlement transaction should have borrowers's reference membership state" using (borrowerMembership != null)
        "Lender should be active member of Business Network with ${lenderMembership!!.networkId}" using (lenderMembership.isActive())
        "Borrower should be active member of Business Network with ${borrowerMembership!!.networkId}" using (borrowerMembership.isActive())
    }
}