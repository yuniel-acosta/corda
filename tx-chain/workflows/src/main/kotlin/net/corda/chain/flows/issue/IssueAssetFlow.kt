package net.corda.chain.flows.issue

import co.paralleluniverse.fibers.Suspendable
import net.corda.chain.contracts.EmptyContract
import net.corda.chain.states.AssetState
import net.corda.chain.states.SnippingStatus
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker


@StartableByService
@InitiatingFlow
@StartableByRPC
class IssueChainFlow (
        private val str: String,
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

        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        val command =
                Command(EmptyContract.Commands.Issue(),
                        parties.map { it.owningKey } + ourIdentity.owningKey)

        val state = AssetState(str = str, parties = parties + ourIdentity, id = UniqueIdentifier(), status = SnippingStatus.ISSUE)

        val utx = TransactionBuilder (notary = notary)
                .addOutputState(state, EmptyContract.ID)
                .addCommand(command)

        val ptx = serviceHub.signInitialTransaction(utx,
                listOf(ourIdentity.owningKey)
        )
        val sessions = parties.map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))
        return subFlow(FinalityFlowNoNotary(stx, sessions, true, END.childProgressTracker()))
    }
}


@InitiatedBy(IssueChainFlow::class)
class IssueChainResponder (
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
        val whitelistedNotary = transaction.notary ?: throw FlowException ("Why is the notary null ??")
        val txRecorded = subFlow(ReceiveFinalityFlowNoNotary(counterpartySession, expectedId, whitelistedNotary))
    }
}




