package net.corda.chain.flows.move

import co.paralleluniverse.fibers.Suspendable
import net.corda.chain.contracts.EmptyContract
import net.corda.chain.flows.chainsnipping.ChainSnippingFlow
import net.corda.chain.states.SnippingStatus
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlowNoNotary
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlowNoNotary
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@StartableByService
@InitiatingFlow
@StartableByRPC
class TransactFlow (
        private val str: String,
        private val linearId: UniqueIdentifier,
        private val parties: List<Party>
) : FlowLogic<SignedTransaction>()  {

    override val progressTracker: ProgressTracker = tracker()

    companion object {
        object START : ProgressTracker.Step("Starting")
        object END : ProgressTracker.Step("Ending") {
            override fun childProgressTracker() = FinalityFlowNoNotary.tracker()
        }

        fun tracker() = ProgressTracker(START, END)
    }

    @Suspendable
    override fun call(): SignedTransaction {
        val inputStateAndRef= subFlow(ChainSnippingFlow (linearId = linearId))
        val outputState= inputStateAndRef.state.data
                .copy(str = str, status = SnippingStatus.ISSUE, parties = parties + ourIdentity)

        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        val command =
                Command(EmptyContract.Commands.Move(),parties.map { it.owningKey } + ourIdentity.owningKey)

        val utx = TransactionBuilder (notary)
                .addInputState(inputStateAndRef)
                .addOutputState(outputState, EmptyContract.ID)
                .addCommand(command)

        val ptx = serviceHub.signInitialTransaction(utx,
                listOf(ourIdentity.owningKey)
        )

        val sessions = parties.map { initiateFlow(it) }

        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        // sessions with the non-local participants
        return subFlow(FinalityFlowNoNotary(stx, sessions,
                doNotNotarise = true, progressTracker = END.childProgressTracker()))
    }
}


@InitiatedBy(TransactFlow::class)
class TransactFlowResponder(
        private val counterpartySession: FlowSession
) : FlowLogic <Unit> () {

    @Suspendable
    override fun call() {

        val transactionSigner: SignTransactionFlow
        transactionSigner = object : SignTransactionFlow(counterpartySession) {
            @Suspendable override fun checkTransaction(stx: SignedTransaction) = requireThat {

            }
        }
        val transaction= subFlow(transactionSigner)
        val expectedId = transaction.id
        val whitelistedNotary = transaction.notary ?: throw FlowException ("The notary is null")
        val txRecorded = subFlow(ReceiveFinalityFlowNoNotary(counterpartySession, expectedId, whitelistedNotary))
    }
}




