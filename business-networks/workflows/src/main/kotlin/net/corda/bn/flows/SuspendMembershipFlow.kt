package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.contracts.MembershipContract
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.ReceiveTransactionFlow
import net.corda.core.flows.SendTransactionFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

@InitiatingFlow
@StartableByRPC
class SuspendMembershipFlow(private val membershipId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val membership = databaseService.getMembership(membershipId)
                ?: throw FlowException("Membership state with $membershipId linear ID doesn't exist")

        // check whether party is authorised to initiate flow
        val networkId = membership.state.data.networkId
        val auth = BNUtils.loadBNMemberAuth()
        val ourMembership = databaseService.getMembership(networkId, ourIdentity)?.state?.data
                ?: throw FlowException("Initiator is not member of a business network")
        if (!ourMembership.isActive()) {
            throw FlowException("Initiator's membership is not active")
        }
        if (!auth.canActivateMembership(ourMembership)) {
            throw FlowException("Initiator is not authorised to run ${javaClass.name} flow")
        }

        // fetch observers and signers
        val authorisedMemberships = databaseService.getMembersAuthorisedToModifyMembership(networkId, auth).toSet()
        val observers = authorisedMemberships.map { it.state.data.identity }.toSet() + membership.state.data.identity - ourIdentity
        val signers = authorisedMemberships.filter { it.state.data.isActive() }.map { it.state.data.identity } - membership.state.data.identity

        // building transaction
        val outputMembership = membership.state.data.copy(status = MembershipStatus.SUSPENDED, modified = serviceHub.clock.instant())
        val builder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(membership)
                .addOutputState(outputMembership)
                .addCommand(MembershipContract.Commands.Suspend(), signers.map { it.owningKey })
        builder.verify(serviceHub)

        // send info to observers whether they need to sign the transaction
        val observerSessions = observers.map { initiateFlow(it) }
        observerSessions.forEach { it.send(it.counterparty in signers) }

        // signing transaction
        val selfSignedTransaction = serviceHub.signInitialTransaction(builder)
        val signerSessions = observerSessions.filter { it.counterparty in signers }
        val allSignedTransaction = subFlow(CollectSignaturesFlow(selfSignedTransaction, signerSessions))

        // finalise transaction
        val finalisedTransaction = subFlow(FinalityFlow(allSignedTransaction, observerSessions, StatesToRecord.ALL_VISIBLE))

        // send authorised memberships to new activated or new suspended member (status moved from PENDING to ACTIVE/SUSPENDED)
        // also send all non revoked memberships (ones that can be modified) if new activated member is authorised to modify them
        if (membership.state.data.isPending()) {
            val activatedMemberSession = observerSessions.single { it.counterparty == membership.state.data.identity }
            val pendingAndSuspendedMemberships =
                    if (auth.run { canActivateMembership(outputMembership) || canSuspendMembership(outputMembership) || canRevokeMembership(outputMembership) }) {
                        databaseService.getAllMembershipsWithStatus(networkId, MembershipStatus.PENDING, MembershipStatus.ACTIVE, MembershipStatus.SUSPENDED)
                    } else emptyList()
            sendMemberships(authorisedMemberships + pendingAndSuspendedMemberships, observerSessions, activatedMemberSession)
        }

        return finalisedTransaction
    }

    @Suspendable
    private fun sendMemberships(
            memberships: Collection<StateAndRef<MembershipState>>,
            observerSessions: List<FlowSession>,
            destinationSession: FlowSession
    ) {
        val membershipsTransactions = memberships.map {
            serviceHub.validatedTransactions.getTransaction(it.ref.txhash)
                    ?: throw FlowException("Transaction for membership with ${it.state.data.linearId} ID doesn't exist")
        }
        observerSessions.forEach { it.send(if (it.counterparty == destinationSession.counterparty) membershipsTransactions.size else 0) }
        membershipsTransactions.forEach { subFlow(SendTransactionFlow(destinationSession, it)) }
    }
}

@InitiatedBy(SuspendMembershipFlow::class)
class SuspendMembershipFlowResponder(val session: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val isSigner = session.receive<Boolean>().unwrap { it }

        val stx = if (isSigner) {
            val signResponder = object : SignTransactionFlow(session) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val command = stx.tx.commands.single()
                    if (command.value !is MembershipContract.Commands.Suspend) {
                        throw FlowException("Only Suspend command is allowed")
                    }

                    stx.toLedgerTransaction(serviceHub, false).verify()
                }
            }
            subFlow(signResponder)
        } else null

        subFlow(ReceiveFinalityFlow(session, stx?.id, StatesToRecord.ALL_VISIBLE))

        val txNumber = session.receive<Int>().unwrap { it }
        repeat(txNumber) {
            subFlow(ReceiveTransactionFlow(session, true, StatesToRecord.ALL_VISIBLE))
        }
    }
}