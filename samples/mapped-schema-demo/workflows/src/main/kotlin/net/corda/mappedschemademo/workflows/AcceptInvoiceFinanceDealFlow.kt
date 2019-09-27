package net.corda.mappedschemademo.workflows

import co.paralleluniverse.fibers.Suspendable
import com.google.common.collect.ImmutableList
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.contracts.asset.PartyAndAmount
import net.corda.finance.workflows.asset.CashUtils
import net.corda.finance.workflows.getCashBalance
import net.corda.mappedschemademo.contracts.contract.InvoiceFinanceDealContract
import net.corda.mappedschemademo.contracts.state.InvoiceFinanceDealState
import net.corda.mappedschemademo.contracts.state.InvoiceFinanceDealStatus
import java.util.*

object AcceptInvoiceFinanceDealFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(
            val linearId: UniqueIdentifier
    ): FlowLogic<UniqueIdentifier>() {

        override val progressTracker: ProgressTracker = tracker()

        companion object {
            object PREPARATION: ProgressTracker.Step("Preparing.")
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
                    PREPARATION,
                    GENERATING_ACCEPTANCE,
                    VERIFYING_ACCEPTANCE,
                    SIGNING_ACCEPTANCE,
                    GATHERING_SIGS,
                    FINALISING_ACCEPTANCE
            )
        }

        //@Suspendable
//        fun getDealByLinearId(linearId: UniqueIdentifier): StateAndRef<InvoiceFinanceDealState> {
//            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
//                    null,
//                    ImmutableList.of(linearId),
//                    Vault.StateStatus.UNCONSUMED, null)
//
//            return serviceHub.vaultService.queryBy<InvoiceFinanceDealState>(queryCriteria).states.singleOrNull()
//                    ?: throw FlowException("Obligation with id $linearId not found.")
//        }

        /**
         * A version of check that throws a FlowException rather than an IllegalArgumentException
         */
        inline fun flowCheck(value: Boolean, msg: () -> String) {
            if (!value) {
                throw FlowException(msg())
            }
        }

        @Suspendable
        override fun call(): UniqueIdentifier {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = PREPARATION
            //val input = getDealByLinearId(linearId)

            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
                    null,
                    ImmutableList.of(linearId),
                    Vault.StateStatus.UNCONSUMED, null)

            val input = serviceHub.vaultService.queryBy<InvoiceFinanceDealState>(queryCriteria).states.singleOrNull()
                    ?: throw FlowException("Obligation with id $linearId not found.")


            val dealState = input.state.data

            flowCheck(dealState.lender == ourIdentity) { "Invoice Finance Deal can only be accepted by Lender" }

            val cashBalance = serviceHub.getCashBalance(dealState.loan.token)

            flowCheck(cashBalance > dealState.loan) { "Balance is $cashBalance but loan amount is ${dealState.loan}"}

            val acceptCommand = Command(
                    InvoiceFinanceDealContract.Commands.Accept(),
                    dealState.participants.map { it.owningKey }
            )

            progressTracker.currentStep = GENERATING_ACCEPTANCE
            val outputState = dealState.copy(status = InvoiceFinanceDealStatus.ACCEPTED)
            //outputState.invoiceList.forEach { it.invoiceId = UUID.randomUUID()}

            val builder = TransactionBuilder(notary)
                    .addInputState(input)
                    .addCommand(acceptCommand)
                    .addOutputState(outputState)

            val (_, cashSigningKeys) = CashUtils.generateSpend(serviceHub, builder, listOf(PartyAndAmount(dealState.borrower, dealState.loan)), ourIdentityAndCert, anonymous = false)

            progressTracker.currentStep = SIGNING_ACCEPTANCE
            builder.verify(serviceHub)
            val ptx = serviceHub.signInitialTransaction(builder, cashSigningKeys + dealState.lender.owningKey)

            progressTracker.currentStep = GATHERING_SIGS
            val session = initiateFlow(dealState.borrower)
            val stx = subFlow(CollectSignaturesFlow(
                    ptx,
                    setOf(session),
                    cashSigningKeys + dealState.lender.owningKey,
                    GATHERING_SIGS.childProgressTracker()
            ))

            progressTracker.currentStep = FINALISING_ACCEPTANCE
            subFlow(FinalityFlow(stx, setOf(session), FINALISING_ACCEPTANCE.childProgressTracker()))

            return dealState.linearId
        }
    }
    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputsOfType<InvoiceFinanceDealState>().singleOrNull()
                    "This must be an Invoice Finance Deal transaction." using (output != null)
                }
            }
            val txId = subFlow(signTransactionFlow).id

            subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }

}