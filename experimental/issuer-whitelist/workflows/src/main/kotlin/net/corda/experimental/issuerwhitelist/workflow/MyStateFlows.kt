package net.corda.experimental.issuerwhitelist.workflow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.NotaryChangeFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.AttachmentId
import net.corda.core.transactions.TransactionBuilder
import net.corda.experimental.issuerwhitelist.contract.MyContract
import net.corda.experimental.issuerwhitelist.contract.MyState

@StartableByRPC
class IssueMyState(private val whitelistAttachmentId: AttachmentId) : FlowLogic<StateAndRef<MyState>>() {
    @Suspendable
    override fun call(): StateAndRef<MyState> {
        val me = serviceHub.myInfo.legalIdentities.first()
        val notary = serviceHub.networkParameters.notaries.first().identity
        val tx = TransactionBuilder(notary)
                .addOutputState(
                        TransactionState(MyState(me.owningKey, me, listOf(me)), notary = notary)
                )
                .addCommand(
                        MyContract.Commands.Issue(), me.owningKey
                )
                .addAttachment(whitelistAttachmentId)
        val stx = serviceHub.signInitialTransaction(tx)
        serviceHub.recordTransactions(stx)
        return stx.tx.outRef<MyState>(0)
    }
}

@StartableByRPC
@InitiatingFlow
class MoveMyState(
        private val state: StateAndRef<MyState>,
        private val recipient: Party,
        private val whitelistAttachmentId: AttachmentId
) : FlowLogic<StateAndRef<MyState>>() {
    @Suspendable
    override fun call(): StateAndRef<MyState> {
        val me = serviceHub.myInfo.legalIdentities.first()
        val notary = state.state.notary

        val outputState = state.state.data.withNewOwner(recipient).ownableState
        val output = state.state.copy(data = outputState as MyState)

        val tx = TransactionBuilder(notary)
                .addInputState(state)
                .addOutputState(output)
                .addCommand(MyContract.Commands.Move(), me.owningKey)
                .addAttachment(whitelistAttachmentId)

        val stx = serviceHub.signInitialTransaction(tx)
        val session = initiateFlow(recipient)
        subFlow(FinalityFlow(stx, session))
        return stx.tx.outRef<MyState>(0)
    }
}

@InitiatedBy(MoveMyState::class)
class ReceiveMyState(private val counterParty: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveFinalityFlow(counterParty))
    }
}

@StartableByRPC
class ChangeNotaries(
        private val state: StateAndRef<MyState>,
        private val newNotary: Party
) : FlowLogic<StateAndRef<MyState>>() {
    @Suspendable
    override fun call(): StateAndRef<MyState> {
        return subFlow(NotaryChangeFlow(state, newNotary))
    }
}
