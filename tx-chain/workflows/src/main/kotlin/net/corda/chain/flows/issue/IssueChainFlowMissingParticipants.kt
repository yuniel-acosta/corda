package net.corda.chain.flows.issue

import co.paralleluniverse.fibers.Suspendable
import net.corda.chain.contracts.ChainContractEmpty
import net.corda.chain.states.ChainStateMissingParticipants
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker


/**
 * Some Flow
 *
 */

@StartableByService
@InitiatingFlow
@StartableByRPC
class IssueChainFlowMissingParticipants (
        private val partyA: Party,
        private val partyB: Party
) : FlowLogic<SignedTransaction>()  {

    override val progressTracker: ProgressTracker = tracker()

    companion object {
        object START : ProgressTracker.Step("Starting")
        object END : ProgressTracker.Step("Ending") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(START, END)
    }

    @Suspendable
    override fun call(): SignedTransaction {

        // Create Transaction
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        // list of signers
        val command =
                Command(ChainContractEmpty.Commands.Issue(),
                        listOf(partyA.owningKey, partyB.owningKey, ourIdentity.owningKey))

        val state = ChainStateMissingParticipants (partyA = partyA,
                partyB = partyB, me = ourIdentity, id = UniqueIdentifier())

        // with input state with command
        val utx = TransactionBuilder(notary = notary)
                .addOutputState(state, ChainContractEmpty.ID)
                .addCommand(command)

        val ptx = serviceHub.signInitialTransaction(utx,
                listOf(ourIdentity.owningKey)
        )

        val sessionA = initiateFlow(partyA)
        val sessionB = initiateFlow(partyB)

        val stx = subFlow(CollectSignaturesFlow(ptx, listOf(sessionA, sessionB)))

        // sessions with the non-local participants
        return subFlow(FinalityFlow(stx, listOf(sessionA, sessionB), END.childProgressTracker()))

    }
}


@InitiatedBy(IssueChainFlowMissingParticipants::class)
class IssueChainFlowMissingParticipantsResponder (
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
        val txRecorded = subFlow(ReceiveFinalityFlow(counterpartySession, expectedId))
    }
}




