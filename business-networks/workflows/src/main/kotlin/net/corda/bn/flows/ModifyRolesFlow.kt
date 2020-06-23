package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.contracts.MembershipContract
import net.corda.bn.states.BNORole
import net.corda.bn.states.BNRole
import net.corda.bn.states.MemberRole
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
class ModifyRolesFlow(private val membershipId: UniqueIdentifier, private val roles: Set<BNRole>) : MembershipManagementFlow<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val membership = databaseService.getMembership(membershipId)
                ?: throw FlowException("Membership state with $membershipId linear ID doesn't exist")

        // check whether party is authorised to initiate flow
        val networkId = membership.state.data.networkId
        authorise(networkId, databaseService) { it.canModifyPermissions() }

        // fetch observers and signers
        val authorisedMemberships = databaseService.getMembersAuthorisedToModifyMembership(networkId).toSet()
        val observers = authorisedMemberships.map { it.state.data.identity }.toSet() + membership.state.data.identity - ourIdentity
        val signers = authorisedMemberships.filter { it.state.data.isActive() }.map { it.state.data.identity }

        // building transaction
        val outputMembership = membership.state.data.copy(roles = roles, modified = serviceHub.clock.instant())
        val builder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(membership)
                .addOutputState(outputMembership)
                .addCommand(MembershipContract.Commands.ModifyPermissions(), signers.map { it.owningKey })
        builder.verify(serviceHub)

        // send info to observers whether they need to sign the transaction
        val observerSessions = observers.map { initiateFlow(it) }
        val finalisedTransaction = collectSignaturesAndFinaliseTransaction(builder, observerSessions, signers)

        // send all non revoked memberships (ones that can be modified) if modified member becomes authorised to modify them
        if (!membership.state.data.canModifyMembership()) {
            onboardMembershipSync(networkId, outputMembership, emptySet(), observerSessions, databaseService)
        }

        return finalisedTransaction
    }
}

@StartableByRPC
class AssignBNORoleFlow(private val membershipId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        return subFlow(ModifyRolesFlow(membershipId, setOf(BNORole())))
    }
}

@StartableByRPC
class AssignMemberRoleFlow(private val membershipId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        return subFlow(ModifyRolesFlow(membershipId, setOf(MemberRole())))
    }
}

@InitiatedBy(ModifyRolesFlow::class)
class ModifyRolesResponderFlow(private val session: FlowSession) : MembershipManagementFlow<Unit>() {

    @Suspendable
    override fun call() {
        signAndReceiveFinalisedTransaction(session) {
            if (it.value !is MembershipContract.Commands.ModifyPermissions) {
                throw FlowException("Only ModifyPermissions command is allowed")
            }
        }
        receiveMemberships(session)
    }
}