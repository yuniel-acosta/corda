package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.contracts.MembershipContract
import net.corda.bn.contracts.RelationshipContract
import net.corda.bn.states.Group
import net.corda.bn.states.RelationshipState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

@InitiatingFlow
class AmendRelationshipFlow(private val membershipId: UniqueIdentifier, private val groups: Map<String, Group>) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val membershipParty = databaseService.getMembership(membershipId)?.state?.data?.identity
                ?: throw FlowException("Membership with $membershipId ID doesn't exist")

        val inputRelationship = databaseService.getRelationship(membershipId)
        val outputRelationship = inputRelationship?.state?.data?.copy(groups = groups, modified = serviceHub.clock.instant())
                ?: RelationshipState(membershipId = membershipId, groups = groups, participants = listOf(membershipParty))
        val command = if (inputRelationship != null) RelationshipContract.Commands.Amend() else RelationshipContract.Commands.Issue()

        val builder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addOutputState(outputRelationship)
                .addCommand(command, ourIdentity.owningKey)
                .apply { if (inputRelationship != null) addInputState(inputRelationship) }
        builder.verify(serviceHub)

        val signers = listOf(ourIdentity)
        val observers = signers - ourIdentity + membershipParty
        val observerSessions = observers.map { initiateFlow(it) }
        observerSessions.forEach { it.counterparty in signers }

        val selfSignedTransaction = serviceHub.signInitialTransaction(builder)
        val signerSessions = (signers - ourIdentity).map { initiateFlow(it) }
        val allSignedTransaction = subFlow(CollectSignaturesFlow(selfSignedTransaction, signerSessions))

        return subFlow(FinalityFlow(allSignedTransaction, signerSessions))
    }
}

@InitiatedBy(AmendRelationshipFlow::class)
class AmendRelationshipFlowResponder(private val session: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val isSigner = session.receive<Boolean>().unwrap { it }

        val stx = if (isSigner) {
            val signResponder = object : SignTransactionFlow(session) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val command = stx.tx.commands.single()
                    if (command.value !is MembershipContract.Commands.Activate) {
                        throw FlowException("Only Activate command is allowed")
                    }

                    stx.toLedgerTransaction(serviceHub, false).verify()
                }
            }
            subFlow(signResponder)
        } else null

        subFlow(ReceiveFinalityFlow(session, stx?.id))
    }
}