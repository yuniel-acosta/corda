package net.corda.mappedschemademo.contracts.test

import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.mappedschemademo.contracts.contract.InvoiceFinanceDealContract
import net.corda.mappedschemademo.contracts.state.Invoice
import net.corda.mappedschemademo.contracts.state.InvoiceFinanceDealState
import net.corda.mappedschemademo.contracts.state.InvoiceFinanceDealStatus
import net.corda.testing.node.ledger
import org.junit.Test

class InvoiceFinanceDealContractProposalTests: InvoiceFinanceDealContractTestsBase() {

    @Test
    fun `valid invoice deal proposal verifies`() {
        ledgerServices.ledger {
            transaction {
                output(InvoiceFinanceDealContract.ID, InvoiceFinanceDealState(
                        borrower = borrower.party,
                        lender = lender.party,
                        loan= 50.POUNDS,
                        fee = 5.POUNDS,
                        invoiceList = listOf(Invoice(value = 100.POUNDS, paid = 0.POUNDS)),
                        status = InvoiceFinanceDealStatus.PROPOSAL
                ))
                command(listOf(borrower.publicKey), InvoiceFinanceDealContract.Commands.Propose())
                verifies()
            }
        }
    }

    @Test
    fun `loan amount must be less than invoice amount`() {
        ledgerServices.ledger {
            transaction {
                output(InvoiceFinanceDealContract.ID, InvoiceFinanceDealState(
                        borrower = borrower.party,
                        lender = lender.party,
                        loan = 100.POUNDS,
                        fee = 5.POUNDS,
                        invoiceList = listOf(Invoice(value = 80.POUNDS, paid = 0.POUNDS))))
                command(listOf(borrower.publicKey), InvoiceFinanceDealContract.Commands.Propose())
                failsWith("Invoice total must be greater than or equal to loan total")
            }
        }
    }

    @Test
    fun `loan amount must be greater than zero`() {
        ledgerServices.ledger {
            transaction {
                output(InvoiceFinanceDealContract.ID, InvoiceFinanceDealState(
                        borrower = borrower.party,
                        lender = lender.party,
                        loan = 0.POUNDS,
                        fee = 5.POUNDS,
                        invoiceList = listOf(Invoice(value = 80.POUNDS, paid = 0.POUNDS))))
                command(listOf(borrower.publicKey), InvoiceFinanceDealContract.Commands.Propose())
                failsWith("The loan amount must be greater than zero")
            }
        }
    }

    @Test
    fun `invoices must have no payment amount on deal creation`() {
        ledgerServices.ledger {
            transaction {
                output(InvoiceFinanceDealContract.ID, InvoiceFinanceDealState(
                        borrower = borrower.party,
                        lender = lender.party,
                        loan = 50.POUNDS,
                        fee = 5.POUNDS,
                        invoiceList = listOf(Invoice(value = 80.POUNDS, paid = 20.POUNDS))))
                command(listOf(borrower.publicKey), InvoiceFinanceDealContract.Commands.Propose())
                failsWith("Invoices must not have any paid amount")
            }
        }
    }

    @Test
    fun `invoices must have value greater than or equal to zero`() {
        ledgerServices.ledger {
            transaction {
                output(InvoiceFinanceDealContract.ID, InvoiceFinanceDealState(
                        borrower = borrower.party,
                        lender = lender.party,
                        loan = 50.POUNDS,
                        fee = 5.POUNDS,
                        invoiceList = listOf(Invoice(value = 0.POUNDS, paid = 0.POUNDS))))
                command(listOf(borrower.publicKey), InvoiceFinanceDealContract.Commands.Propose())
                failsWith("Invoice values must be greater than zero")
            }
        }
    }

    @Test
    fun `invoice deal proposal must have correct status`() {
        ledgerServices.ledger {
            transaction {
                output(InvoiceFinanceDealContract.ID, InvoiceFinanceDealState(
                        borrower = borrower.party,
                        lender = lender.party,
                        loan= 50.POUNDS,
                        fee = 5.POUNDS,
                        invoiceList = listOf(Invoice(value = 100.POUNDS, paid = 0.POUNDS)),
                        status = InvoiceFinanceDealStatus.ACCEPTED
                ))
                command(listOf(borrower.publicKey), InvoiceFinanceDealContract.Commands.Propose())
                failsWith("Invoice deal must be created with PROPOSED status")
            }
        }
    }

    @Test
    fun `invoice deal fee and loan should have same currency`() {
        ledgerServices.ledger {
            transaction {
                output(InvoiceFinanceDealContract.ID, InvoiceFinanceDealState(
                        borrower = borrower.party,
                        lender = lender.party,
                        loan= 50.POUNDS,
                        fee = 5.DOLLARS,
                        invoiceList = listOf(Invoice(value = 100.POUNDS, paid = 0.POUNDS))

                ))
                command(listOf(borrower.publicKey), InvoiceFinanceDealContract.Commands.Propose())
                failsWith("Loan currency must be the same currency as the fee")

            }
        }
    }

    @Test
    fun `invoice value and paid should have same currency`() {
        ledgerServices.ledger {
            transaction {
                output(InvoiceFinanceDealContract.ID, InvoiceFinanceDealState(
                        borrower = borrower.party,
                        lender = lender.party,
                        loan = 50.POUNDS,
                        fee = 5.POUNDS,
                        invoiceList = listOf(Invoice(value = 100.POUNDS, paid = 0.DOLLARS))
                ))
                command(listOf(borrower.publicKey), InvoiceFinanceDealContract.Commands.Propose())
                failsWith("Invoice value and paid amounts must be same currency")
            }
        }
    }

    @Test
    fun `invoice currency must be same as loan currency`() {
        ledgerServices.ledger {
            transaction {
                output(InvoiceFinanceDealContract.ID, InvoiceFinanceDealState(
                        borrower = borrower.party,
                        lender = lender.party,
                        loan = 50.POUNDS,
                        fee = 5.POUNDS,
                        invoiceList = listOf(Invoice(value = 100.DOLLARS, paid = 0.DOLLARS))
                ))
                command(listOf(lender.publicKey, borrower.publicKey), InvoiceFinanceDealContract.Commands.Propose())
                failsWith("Invoices must be same currency as loan")
            }
        }
    }

}