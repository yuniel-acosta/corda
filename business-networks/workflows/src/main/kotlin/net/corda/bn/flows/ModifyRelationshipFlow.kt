package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction

@InitiatingFlow
@StartableByRPC
class ModifyRelationshipFlow : MembershipManagementFlow<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

@InitiatedBy(ModifyRelationshipFlow::class)
class ModifyRelationshipResponderFlow(private val session: FlowSession) : MembershipManagementFlow<Unit>() {

    @Suspendable
    override fun call() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}