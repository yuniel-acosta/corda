package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.contracts.MembershipContract
import net.corda.bn.states.AdminPermission
import net.corda.bn.states.BNORole
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.util.*

/**
 * Self issues [MembershipState] for the flow initiator creating new Business Network as consequence. Every node in Compatibility Zone can
 * initiate this flow.
 */
@InitiatingFlow
@StartableByRPC
class CreateBusinessNetworkFlow : FlowLogic<SignedTransaction>() {

    @Suspendable
    private fun createMembershipRequest(): SignedTransaction {
        val membership = MembershipState(
                identity = ourIdentity,
                networkId = UUID.randomUUID().toString(),
                status = MembershipStatus.PENDING,
                participants = listOf(ourIdentity)
        )

        val builder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addOutputState(membership)
                .addCommand(MembershipContract.Commands.Request(), ourIdentity.owningKey)
        builder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(builder)
        return subFlow(FinalityFlow(stx, emptyList()))
    }

    @Suspendable
    private fun activateMembership(membership: StateAndRef<MembershipState>): SignedTransaction {
        val builder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(membership)
                .addOutputState(membership.state.data.copy(status = MembershipStatus.ACTIVE, modified = serviceHub.clock.instant()))
                .addCommand(MembershipContract.Commands.Activate(), ourIdentity.owningKey)
        builder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(builder)
        return subFlow(FinalityFlow(stx, emptyList()))
    }

    @Suspendable
    private fun authoriseMembership(membership: StateAndRef<MembershipState>): SignedTransaction {
        val builder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(membership)
                .addOutputState(membership.state.data.copy(roles = setOf(BNORole()), modified = serviceHub.clock.instant()))
                .addCommand(MembershipContract.Commands.ModifyPermissions(), ourIdentity.owningKey)
        builder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(builder)
        return subFlow(FinalityFlow(stx, emptyList()))
    }

    @Suspendable
    override fun call(): SignedTransaction {
        // first issue membership with PENDING status
        val pendingMembership = createMembershipRequest().tx.outRefsOfType(MembershipState::class.java).single()
        // after that activate the membership
        val activeMembership = activateMembership(pendingMembership).tx.outRefsOfType(MembershipState::class.java).single()
        // in the end give all permissions to the membership
        return authoriseMembership(activeMembership)
    }
}