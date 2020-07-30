package net.corda.bn.demo.workflows

import co.paralleluniverse.fibers.Suspendable
import com.prowidesoftware.swift.model.BIC
import net.corda.bn.demo.contracts.BankIdentity
import net.corda.bn.flows.DatabaseService
import net.corda.bn.flows.ModifyBusinessIdentityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

@StartableByRPC
class AssignBICFlow(private val networkId: String, private val bic: String, private val notary: Party? = null) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val bnService = serviceHub.cordaService(DatabaseService::class.java)
        val ourMembership = bnService.getMembership(networkId, ourIdentity)?.state?.data
                ?: throw FlowException("$ourIdentity is not member of Business Network with $networkId ID")

        return subFlow(ModifyBusinessIdentityFlow(ourMembership.linearId, BankIdentity(BIC(bic)), notary))
    }
}