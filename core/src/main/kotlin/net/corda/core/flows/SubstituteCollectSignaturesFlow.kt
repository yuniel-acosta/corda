package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.transactions.SignedTransaction
import java.lang.UnsupportedOperationException

class SubstituteCollectSignaturesFlow(private val originalFlow: CollectSignaturesFlow): FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        throw UnsupportedOperationException("not implemented yet!")
    }

}