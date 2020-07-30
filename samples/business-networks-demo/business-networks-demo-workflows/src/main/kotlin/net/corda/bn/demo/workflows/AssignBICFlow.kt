package net.corda.bn.demo.workflows

import co.paralleluniverse.fibers.Suspendable
import com.prowidesoftware.swift.model.BIC
import net.corda.bn.demo.contracts.BankIdentity
import net.corda.bn.flows.DatabaseService
import net.corda.bn.flows.IllegalFlowArgumentException
import net.corda.bn.flows.MembershipNotFoundException
import net.corda.bn.flows.ModifyBusinessIdentityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

/**
 * This flow assigns [BankIdentity] to initiator's membership's business identity. It is meant to be conveniently used from node shell
 * instead of [ModifyBusinessIdentityFlow].
 *
 * @property networkId ID of the Business Network where initiator belongs to.
 * @property bic Business Identifier Code to be assigned to initiator.
 * @property notary Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used.
 */
@StartableByRPC
class AssignBICFlow(private val networkId: String, private val bic: String, private val notary: Party? = null) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val bnService = serviceHub.cordaService(DatabaseService::class.java)
        val ourMembership = bnService.getMembership(networkId, ourIdentity)?.state?.data
                ?: throw MembershipNotFoundException("$ourIdentity is not member of Business Network with $networkId ID")

        val bicObj = BIC(bic).apply {
            if (!isValid) {
                throw IllegalFlowArgumentException("$bic in not a valid BIC")
            }
        }
        return subFlow(ModifyBusinessIdentityFlow(ourMembership.linearId, BankIdentity(bicObj), notary))
    }
}