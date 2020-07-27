package net.corda.bn.demo.workflows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.demo.contracts.LoanContract
import net.corda.bn.demo.contracts.LoanState
import net.corda.bn.flows.DatabaseService
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByRPC
class SettleLoanFlow(private val networkId: String, private val linearId: UniqueIdentifier, private val amountToSettle: Int) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId)))
        val inputState = serviceHub.vaultService.queryBy(LoanState::class.java, criteria).states.single()

        if (ourIdentity != inputState.state.data.borrower) {
            throw FlowException("Only borrower can settle loan")
        }
        if (amountToSettle <= 0) {
            throw FlowException("Settlement can only be done with positive amount")
        }
        if (inputState.state.data.amount - amountToSettle < 0) {
            throw FlowException("Amount to settle is bigger than actual loan amount")
        }

        val bnService = serviceHub.cordaService(DatabaseService::class.java)
        val lenderMembership = bnService.getMembership(networkId, inputState.state.data.lender)
                ?: throw FlowException("Lender is not longer part of Business Network with $networkId ID")
        val borrowerMembership = bnService.getMembership(networkId, inputState.state.data.borrower)
                ?: throw FlowException("Borrower is not longer part of Business Network with $networkId ID")

        val isFullySettled = inputState.state.data.amount - amountToSettle == 0
        val command = if (isFullySettled) LoanContract.Commands.Exit() else LoanContract.Commands.Settle()
        val builder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(inputState)
                .addCommand(command, inputState.state.data.participants.map { it.owningKey })
                .addReferenceState(ReferencedStateAndRef(lenderMembership))
                .addReferenceState(ReferencedStateAndRef(borrowerMembership))
        if (!isFullySettled) {
            builder.addOutputState(inputState.state.data.settle(amountToSettle))
        }
        builder.verify(serviceHub)

        val selfSignedTransaction = serviceHub.signInitialTransaction(builder)
        val sessions = listOf(initiateFlow(inputState.state.data.lender))
        val fullSignedTransaction = subFlow(CollectSignaturesFlow(selfSignedTransaction, sessions))
        return subFlow(FinalityFlow(fullSignedTransaction, sessions))
    }
}

@InitiatedBy(SettleLoanFlow::class)
class SettleLoanResponderFlow(private val session: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}