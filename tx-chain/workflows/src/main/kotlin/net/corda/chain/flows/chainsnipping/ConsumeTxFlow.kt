package net.corda.chain.flows.chainsnipping

import co.paralleluniverse.fibers.Suspendable
import net.corda.chain.contracts.ChainContractWithChecks
import net.corda.chain.states.ChainStateAllParticipants
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker


@StartableByService
@InitiatingFlow
@StartableByRPC
class ConsumeTxFlow (
        private val currentState: StateAndRef<ChainStateAllParticipants>,
        private val partyA: Party,
        private val partyB: Party
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

        // Create Transaction
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        // list of signers
        val command =
                Command(ChainContractWithChecks.Commands.ConsumeState(),
                        listOf(partyA.owningKey, partyB.owningKey, ourIdentity.owningKey))

//        val state = ChainStateAllParticipants(partyA = partyA,
//                partyB = partyB, me = ourIdentity, id = UniqueIdentifier())

        // with input state with command
        val utx = TransactionBuilder(notary = notary)
                .addInputState(currentState)
                //.addOutputState(state, ChainContractWithChecks.ID)
                .addCommand(command)

        val ptx = serviceHub.signInitialTransaction(utx,
                listOf(ourIdentity.owningKey)
        )

        val sessionA = initiateFlow(partyA)
        val sessionB = initiateFlow(partyB)

        val stx = subFlow(CollectSignaturesFlow(ptx, listOf(sessionA, sessionB)))

        // sessions with the non-local participants
        return subFlow(FinalityFlowNoNotary(stx, listOf(sessionA, sessionB), true, END.childProgressTracker()))

    }
}


@InitiatedBy(ConsumeTxFlow::class)
class ConsumeTxResponder (
        private val counterpartySession: FlowSession
) : FlowLogic <Unit> () {

    @Suspendable
    override fun call() {

        val transactionSigner: SignTransactionFlow
        transactionSigner = object : SignTransactionFlow(counterpartySession) {
            @Suspendable override fun checkTransaction(stx: SignedTransaction) = requireThat {
//                Example:
//                val tx = stx.tx
//                val commands = tx.commands
//                "There must be exactly one command" using (commands.size == 1)
            }
        }
        val transaction= subFlow(transactionSigner)
        val expectedId = transaction.id
        val whitelistedNotary = transaction.notary ?: throw FlowException ("The notary is null")
        val txRecorded = subFlow(ReceiveFinalityFlowNoNotary(counterpartySession, expectedId, whitelistedNotary))
    }
}




