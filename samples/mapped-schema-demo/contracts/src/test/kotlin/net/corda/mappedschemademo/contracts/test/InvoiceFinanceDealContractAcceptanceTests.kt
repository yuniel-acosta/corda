package net.corda.mappedschemademo.contracts.test

import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.mappedschemademo.contracts.contract.InvoiceFinanceDealContract
import net.corda.mappedschemademo.contracts.state.Invoice
import net.corda.mappedschemademo.contracts.state.InvoiceFinanceDealState
import net.corda.mappedschemademo.contracts.state.InvoiceFinanceDealStatus
import net.corda.testing.node.ledger
import org.junit.Test

class InvoiceFinanceDealContractAcceptanceTests: InvoiceFinanceDealContractTestsBase() {
    @Test
    fun `valid invoice deal acceptance verifies`() {
        val loanAmount = 50.POUNDS
        val lenderCash = createCashState(loanAmount, owner = lender.party)
        val borrowerCash = lenderCash.withNewOwner(borrower.party).ownableState
        ledgerServices.ledger {
            transaction {
                val state = InvoiceFinanceDealState(
                        borrower = borrower.party,
                        lender = lender.party,
                        loan = loanAmount,
                        fee = 5.POUNDS,
                        invoiceList = listOf(Invoice(value = 100.POUNDS, paid = 0.POUNDS)),
                        status = InvoiceFinanceDealStatus.PROPOSAL
                )
                input(InvoiceFinanceDealContract.ID, state)
                input(Cash.PROGRAM_ID, lenderCash)
                output(InvoiceFinanceDealContract.ID, state.copy(status = InvoiceFinanceDealStatus.ACCEPTED))
                output(Cash.PROGRAM_ID, borrowerCash)
                command(listOf(lender.publicKey, borrower.publicKey), InvoiceFinanceDealContract.Commands.Accept())
                command(lender.publicKey, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test
    fun `input invoice deal must be similar to output invoice deal`() {
        ledgerServices.ledger {
            transaction {
                val state = InvoiceFinanceDealState(
                        borrower = borrower.party,
                        lender = lender.party,
                        loan = 50.POUNDS,
                        fee = 5.POUNDS,
                        invoiceList = listOf(Invoice(value = 100.POUNDS, paid = 0.POUNDS)),
                        status = InvoiceFinanceDealStatus.PROPOSAL
                )
                input(InvoiceFinanceDealContract.ID, state)
                output(InvoiceFinanceDealContract.ID, state.copy(status = InvoiceFinanceDealStatus.ACCEPTED, loan = 55.POUNDS))
                command(listOf(lender.publicKey, borrower.publicKey), InvoiceFinanceDealContract.Commands.Accept())
                failsWith("Input state and output state should be the same apart from an updated status")
            }
        }
    }

}