package net.corda.notarychange.demo.flows

import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import net.corda.core.contracts.Command
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.CollectSignatureFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.NotaryChangeFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.notarychange.demo.contracts.SwapNotaryCommand
import net.corda.notarychange.demo.nodeLegalIdentity

class SwapNotaryForNonFungibleToken(val originalTxId: String, val newNotary: Party) : FlowLogic<SignedTransaction>() {

    override fun call(): SignedTransaction {
        val originalState = SecureHash.parse(originalTxId)

        val tx = serviceHub.validatedTransactions.getTransaction(originalState)?.toLedgerTransaction(serviceHub)
                ?: throw FlowException("Could not get transaction.")

        val oldState = tx.outRef<NonFungibleToken>(0)
        val newNotaryState = subFlow(NotaryChangeFlow(oldState, newNotary))
        val nodeIdentity = serviceHub.nodeLegalIdentity()

        val utx = TransactionBuilder(newNotary)
                .addInputState(newNotaryState)
                .addCommand(Command(SwapNotaryCommand(oldState.state.data.issuedTokenType), nodeIdentity.owningKey))

        val holder = serviceHub.identityService.wellKnownPartyFromAnonymous(newNotaryState.state.data.holder)
        require(holder != null) {
            "T"
        }

        val anonHolder = newNotaryState.state.data.holder
//        initiateFlow(AnonymousParty(newNotaryState.state.data.holder))

        val stx = serviceHub.signInitialTransaction(utx)
        val signedTx = subFlow(CollectSignatureFlow(stx, holder))
        val finalTx = subFlow(FinalityFlow(signedTx, holder))
        finalTx.verify(serviceHub)
        return finalTx
    }
}