package net.corda.bn.demo.workflows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.demo.contracts.LoanContract
import net.corda.bn.demo.contracts.LoanPermissions
import net.corda.bn.demo.contracts.LoanState
import net.corda.bn.flows.DatabaseService
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.ReceiveTransactionFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByRPC
class IssueLoanFlow(private val networkId: String, private val borrower: Party, private val amount: Int) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        businessNetworkVerification()

        val outputState = LoanState(lender = ourIdentity, borrower = borrower, amount = amount)
        val builder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addOutputState(outputState)
                .addCommand(LoanContract.Commands.Issue(), ourIdentity.owningKey, borrower.owningKey)
        builder.verify(serviceHub)

        val selfSignedTransaction = serviceHub.signInitialTransaction(builder)
        val sessions = listOf(initiateFlow(borrower))
        val fullSignedTransaction = subFlow(CollectSignaturesFlow(selfSignedTransaction, sessions))
        return subFlow(FinalityFlow(fullSignedTransaction, sessions))
    }

    @Suspendable
    private fun businessNetworkVerification() {
        val bnService = serviceHub.cordaService(DatabaseService::class.java)

        bnService.getMembership(networkId, ourIdentity)?.state?.data?.apply {
            if (!isActive()) {
                throw FlowException("$ourIdentity is not active member of Business Network with $networkId ID")
            }
            if (roles.find { LoanPermissions.CAN_ISSUE_LOAN in it.permissions } == null) {
                throw FlowException("$ourIdentity is not authorised to issue loan in Business Network with $networkId ID")
            }
        } ?: throw FlowException("$ourIdentity is not member of Business Network with $networkId ID")

        bnService.getMembership(networkId, borrower)?.state?.data?.apply {
            if (!isActive()) {
                throw FlowException("$borrower is not active member of Business Network with $networkId ID")
            }
        } ?: throw FlowException("$borrower is not member of Business Network with $networkId ID")
    }
}

@InitiatedBy(IssueLoanFlow::class)
class IssueLoanResponderFlow(private val session: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val signResponder = object : SignTransactionFlow(session) {
            override fun checkTransaction(stx: SignedTransaction) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }
        }
        val stx = subFlow(signResponder)

        subFlow(ReceiveFinalityFlow(session, stx.id))
    }
}