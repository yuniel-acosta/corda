package net.corda.mappedschemademo.contracts.contract

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.contextLogger
import net.corda.finance.contracts.asset.Cash
import net.corda.mappedschemademo.contracts.state.InvoiceFinanceDealState
import net.corda.mappedschemademo.contracts.state.InvoiceFinanceDealStatus
import java.security.PublicKey

class InvoiceFinanceDealContract : Contract {
    companion object {
        @JvmStatic
        val ID = "net.corda.mappedschemademo.contracts.contract.InvoiceFinanceDealContract"
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val setOfSigners = command.signers.toSet()
        verifyAll(tx)
        when (command.value) {
            is Commands.Propose -> verifyPropose(tx, setOfSigners)
            is Commands.Accept -> verifyAccept(tx, setOfSigners)
            is Commands.PayInvoice -> verifyPayInvoice()
            is Commands.Discharge -> verifyDischarge()
        }
    }

    private fun keysFromParticipants(deal: InvoiceFinanceDealState): Set<PublicKey> {
        return deal.participants.map {
            it.owningKey
        }.toSet()
    }

    private fun verifyAll(tx: LedgerTransaction) = requireThat {
        val deal = tx.outputsOfType<InvoiceFinanceDealState>().single()
        "The loan amount must be greater than zero" using (deal.loan.quantity > 0)
        "The lender and the borrower cannot be the same identity" using (deal.borrower != deal.lender)

        "Loan currency must be the same currency as the fee" using (deal.loan.token == deal.fee.token)
        val loanTotal = deal.loan.quantity + deal.fee.quantity
        "Invoices must be same currency as loan" using (deal.invoiceList.all { it.value.token == deal.loan.token })
        "Invoice value and paid amounts must be same currency" using  (deal.invoiceList.all { it.value.token == it.paid.token })
        val invoiceTotal = deal.invoiceList.map { it.value.quantity }.sum()
        "Invoice values must be greater than zero" using (deal.invoiceList.all { it.value.quantity > 0 })
        "Invoice total must be greater than or equal to loan total" using (invoiceTotal >= loanTotal)
    }

    private fun verifyPropose(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "No inputs should be consumed when creating an invoice finance deal" using (tx.inputStates.isEmpty())
        "Only one Invoice Finance Deal state should be created" using (tx.outputStates.size == 1)
        val deal = tx.outputsOfType<InvoiceFinanceDealState>().single()
        "Invoice deal must be created with PROPOSED status" using (deal.status == InvoiceFinanceDealStatus.PROPOSAL)
        "Both lender and borrower together must sign the transaction" using (signers == setOf(deal.borrower.owningKey))
        "Invoices must not have any paid amount" using (deal.invoiceList.all { it.paid == Amount.zero(it.value.token) })
    }

    private fun verifyAccept(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "Only one Invoice Finance Deal state should be input" using (tx.inputsOfType<InvoiceFinanceDealState>().size == 1)
        val inputDeal = tx.inputsOfType<InvoiceFinanceDealState>().single()
        "Input state should have status of PROPOSAL" using (inputDeal.status == InvoiceFinanceDealStatus.PROPOSAL)


        "Only one Invoice Finance Deal state should be output" using (tx.outputsOfType<InvoiceFinanceDealState>().size == 1)
        val outputDeal = tx.outputsOfType<InvoiceFinanceDealState>().single()
        "Both lender and borrower together must sign the transaction" using (signers == keysFromParticipants(outputDeal))
        "Output state should have status of ACCEPTED" using (outputDeal.status == InvoiceFinanceDealStatus.ACCEPTED)
        "Input state and output state should be the same apart from an updated status" using inputDeal.copy(status = InvoiceFinanceDealStatus.ACCEPTED).equals(outputDeal)

        val cashInputs = tx.inputsOfType<Cash.State>()
        "Inputs must contain at least one Cash state" using (cashInputs.isNotEmpty())
        "Inputs must only include Cash and InvoiceFinanceDeal states" using ((cashInputs.size + 1) == tx.inputs.size)

        val cashOutputs = tx.outputsOfType<Cash.State>()
        "Outputs must contain at least one Cash state" using (cashOutputs.isNotEmpty())
        "Outputs must only include Cash and InvoiceFinanceDeal states" using ((cashOutputs.size + 1) == tx.outputs.size)

        "All cash must come from the lender" using (cashInputs.all { it.owner == inputDeal.lender })
        "All cash must go to the borrower" using (cashOutputs.all { it.owner == outputDeal.borrower })
    }

    private fun verifyPayInvoice() { //tx: LedgerTransaction, signers: Set<PublicKey>) {
    }

    private fun verifyDischarge() { //tx: LedgerTransaction, signers: Set<PublicKey>) {
    }

    interface Commands : CommandData {
        class Propose : TypeOnlyCommandData(), Commands
        class Accept : TypeOnlyCommandData(), Commands
        //class Reject : Commands
        class PayInvoice : TypeOnlyCommandData(), Commands
        class Discharge : TypeOnlyCommandData(), Commands
    }
}