package net.corda.mappedschemademo.workflows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.mappedschemademo.contracts.contract.InvoiceFinanceDealContract
import net.corda.mappedschemademo.contracts.state.Invoice
import net.corda.mappedschemademo.contracts.state.InvoiceFinanceDealState
import java.util.*

object AcceptInvoiceFinanceDealFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(
            val txId: SecureHash
    ): FlowLogic<SecureHash>() {

        companion object {
            object GENERATING_ACCEPTANCE: ProgressTracker.Step("Generating transaction based on new invoice finance deal.")
            object VERIFYING_ACCEPTANCE : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_ACCEPTANCE : ProgressTracker.Step("Signing transaction with our private key.")

            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_ACCEPTANCE : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_ACCEPTANCE,
                    VERIFYING_ACCEPTANCE,
                    SIGNING_ACCEPTANCE,
                    GATHERING_SIGS,
                    FINALISING_ACCEPTANCE
            )
        }

        override fun call(): SecureHash {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            //progressTracker.currentStep = GENERATING_ACCEPTANCE

            // get invoice finance deal state attached to provided transaction id

            // Generate an unsigned transaction.
//
//            val iouState = InvoiceFinanceDealState(
//                    reference = reference,
//                    borrower = serviceHub.myInfo.legalIdentities.first(),
//                    lender = lender,
//                    loan = loan,
//                    fee = fee,
//                    invoiceList = invoiceList)
//            val txCommand = Command(InvoiceFinanceDealContract.Commands.Propose(), listOf(iouState.borrower.owningKey))
//            val txBuilder = TransactionBuilder(notary)
//                    .addOutputState(iouState, InvoiceFinanceDealContract.ID)
//                    .addCommand(txCommand)

            return SecureHash.allOnesHash
        }
    }
}

object ProposeInvoiceFinanceDealFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(
            val reference: String,
            val lender: Party,
            val loan: Amount<Currency>,
            val fee: Amount<Currency>,
            val invoiceList: List<Invoice>
    ): FlowLogic<UniqueIdentifier>() {
        companion object {
            object GENERATING_PROPOSAL: ProgressTracker.Step("Generating transaction based on new invoice finance deal.")
            object VERIFYING_PROPOSAL : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_PROPOSAL : ProgressTracker.Step("Signing transaction with our private key.")

            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_PROPOSAL : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_PROPOSAL,
                    VERIFYING_PROPOSAL,
                    SIGNING_PROPOSAL,
                    GATHERING_SIGS,
                    FINALISING_PROPOSAL
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): UniqueIdentifier {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = GENERATING_PROPOSAL
            // Generate an unsigned transaction.
            val iouState = InvoiceFinanceDealState(
                    reference = reference,
                    borrower = serviceHub.myInfo.legalIdentities.first(),
                    lender = lender,
                    loan = loan,
                    fee = fee,
                    invoiceList = invoiceList)
            val txCommand = Command(InvoiceFinanceDealContract.Commands.Propose(), listOf(iouState.borrower.owningKey))
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(iouState, InvoiceFinanceDealContract.ID)
                    .addCommand(txCommand)

            // Stage 2.
            progressTracker.currentStep = VERIFYING_PROPOSAL
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_PROPOSAL
            // Sign the transaction.
            val fullySignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4.
            progressTracker.currentStep = GATHERING_SIGS
            val lenderSession = initiateFlow(lender)
            //val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(lenderSession), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_PROPOSAL
            // Notarise and record the transaction in both parties' vaults.
            subFlow(FinalityFlow(fullySignedTx, setOf(lenderSession), FINALISING_PROPOSAL.childProgressTracker()))

            return iouState.linearId
        }
    }

    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            // No need for the proposal to be signed by the lender, they just need to save the transaction
//            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
//                override fun checkTransaction(stx: SignedTransaction) = requireThat {
//                    val output = stx.tx.outputs.single().data
//                    "This must be an Asset transaction." using (output is InvoiceFinanceDealState)
//                }
//            }
//            val txId = subFlow(signTransactionFlow).id

            subFlow(ReceiveFinalityFlow(otherPartySession))
        }
    }
}