package net.corda.bn.custom.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.contracts.MembershipContract
import net.corda.bn.custom.contracts.CustomMembershipContract
import net.corda.bn.flows.DatabaseService
import net.corda.bn.flows.MembershipRequest
import net.corda.bn.flows.RequestMembershipFlow
import net.corda.bn.flows.RequestMembershipFlowResponder
import net.corda.bn.flows.RequestMembershipObserverFlow
import net.corda.bn.states.BNIdentity
import net.corda.bn.states.MembershipIdentity
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

@StartableByRPC
class CustomRequestMembershipFlow(
        authorisedParty: Party,
        networkId: String,
        businessIdentity: BNIdentity? = null,
        notary: Party? = null
) : RequestMembershipFlow(authorisedParty, networkId, businessIdentity, notary) {

    @Suspendable
    override fun call(): SignedTransaction {
        // check whether the initiator is already member of given Business Network
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        if (databaseService.getMembership(networkId, ourIdentity) != null) {
            throw FlowException("Initiator is already a member of Business Network with $networkId ID")
        }

        // send request to authorised member
        val authorisedPartySession = initiateFlow(authorisedParty)
        authorisedPartySession.send(MembershipRequest(networkId, businessIdentity, notary))

        // sign transaction
        val signResponder = object : SignTransactionFlow(authorisedPartySession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val command = stx.tx.commands.single()
                if (command.value !is MembershipContract.Commands.Request) {
                    throw FlowException("Only Request command is allowed")
                }

                val membershipState = stx.tx.outputs.single().data as MembershipState
                if (ourIdentity != membershipState.identity.cordaIdentity) {
                    throw IllegalArgumentException("Membership identity does not match the one of the initiator")
                }

                stx.toLedgerTransaction(serviceHub, false).verify()
            }
        }
        val stx = subFlow(signResponder)

        // receive finality flow
        return subFlow(ReceiveFinalityFlow(authorisedPartySession, stx.id))
    }
}

@InitiatedBy(RequestMembershipFlow::class)
class CustomRequestMembershipFlowResponder(session: FlowSession) : RequestMembershipFlowResponder(session) {

    @Suspendable
    override fun call() {
        // receive network ID
        val (networkId, businessIdentity, notary) = session.receive<MembershipRequest>().unwrap { it }

        // check whether party is authorised to activate membership
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        authorise(networkId, databaseService) { it.canActivateMembership() }

        // fetch observers
        val authorisedMemberships = databaseService.getMembersAuthorisedToModifyMembership(networkId)
        val observers = (authorisedMemberships.map { it.state.data.identity.cordaIdentity } - ourIdentity).toSet()

        // build transaction
        val counterparty = session.counterparty
        val membershipState = MembershipState(
                identity = MembershipIdentity(counterparty, businessIdentity),
                networkId = networkId,
                status = MembershipStatus.PENDING,
                participants = (observers + ourIdentity + counterparty).toList()
        )
        val requiredSigners = listOf(ourIdentity.owningKey, counterparty.owningKey)
        val builder = TransactionBuilder(notary ?: serviceHub.networkMapCache.notaryIdentities.first())
                .addOutputState(membershipState, CustomMembershipContract.CONTRACT_NAME)
                .addCommand(MembershipContract.Commands.Request(requiredSigners), requiredSigners)
        builder.verify(serviceHub)

        // sign transaction
        val selfSignedTransaction = serviceHub.signInitialTransaction(builder)
        val allSignedTransaction = subFlow(CollectSignaturesFlow(selfSignedTransaction, listOf(session)))

        // finalise transaction
        val observerSessions = observers.map { initiateFlow(it) }.toSet()
        subFlow(FinalityFlow(allSignedTransaction, observerSessions + session))
    }
}

@InitiatedBy(RequestMembershipFlowResponder::class)
class CustomRequestMembershipObserverFlow(session: FlowSession) : RequestMembershipObserverFlow(session)