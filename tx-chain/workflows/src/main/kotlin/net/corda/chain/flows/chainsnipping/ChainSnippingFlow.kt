package net.corda.chain.flows.chainsnipping

import co.paralleluniverse.fibers.Suspendable
import net.corda.chain.states.AssetState
import net.corda.chain.states.SnippingStatus
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.FinalityFlowNoNotary
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlowNoNotary
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
class ChainSnippingFlow (
        private val linearId: UniqueIdentifier
) : FlowLogic<StateAndRef<AssetState>>()  {

    override val progressTracker: ProgressTracker = tracker()

    companion object {
        object START : ProgressTracker.Step("Starting")
        object END : ProgressTracker.Step("Ending") {
            override fun childProgressTracker() = FinalityFlowNoNotary.tracker()
        }

        fun tracker() = ProgressTracker(START, END)
    }

    @Suspendable
    override fun call(): StateAndRef<AssetState> {

        val linearStateQueryCriteria =
                QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))

        val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)

        val chainStateAndRefList = serviceHub.vaultService.
                queryBy(criteria = criteria.and(linearStateQueryCriteria),
                        contractStateType = AssetState::class.java)
                .states

        val inputStateAndRef = chainStateAndRefList.single()
        assertOnChainLength (inputStateAndRef)

        val parties = inputStateAndRef.state.data.parties - ourIdentity
        val outputState = inputStateAndRef.state.data.copy (status = SnippingStatus.REISSUE)

        val consumeTx= subFlow(ConsumeTxFlow (inputStateAndRef = inputStateAndRef, parties = parties))
        val reIssueTx = subFlow(ReIssueTxFlow (outputState = outputState, parties = parties))

        return reIssueTx.coreTransaction.outRefsOfType(AssetState::class.java).single()

    }

    private fun assertOnChainLength (stateAndRef: StateAndRef<AssetState>) {

        var lastTxHash = stateAndRef.ref.txhash
        var i = 1
        while (lastTxHash != null) {
            val tx = serviceHub.validatedTransactions.getTransaction(lastTxHash)
                    ?: throw FlowException("Tx with hash $lastTxHash")

            if (tx.inputs.isEmpty()) { // reached the beginning
                break
            }
            else {
                lastTxHash = tx.inputs.single().txhash
                i++
            }
        }

        if (i > 2)
            throw FlowException ("The unexpected happened: Tx Chain length is $i ")
    }
}


@InitiatedBy(ChainSnippingFlow::class)
class ChainSnippingResponder(
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




