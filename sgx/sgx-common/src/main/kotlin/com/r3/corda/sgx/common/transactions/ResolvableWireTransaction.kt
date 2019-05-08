package com.r3.corda.sgx.common.transactions

import com.r3.corda.sgx.common.TrustedNodeServices
import com.r3.corda.sgx.common.internal.CachedNodeServicesImpl
import net.corda.core.node.ServicesForResolution
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.WireTransaction

@CordaSerializable
open class ResolvableWireTransaction(
        val tx: WireTransaction,
        val txResolutionCache: TransactionResolutionCache) {

    constructor(tx: WireTransaction, resolutionServices: ServicesForResolution)
        : this(tx, TransactionResolutionCache.fromWireTx(tx, resolutionServices))

    fun toLedgerTransactionSgx(txResolutionServices: TrustedNodeServices) =
            tx.toLedgerTransactionSgx(CachedNodeServicesImpl(txResolutionCache, txResolutionServices))
}

fun WireTransaction.toLedgerTransactionSgx(txResolutionServices: TrustedNodeServices): LedgerTransaction {
    return this.toLedgerTransactionInternal(
            txResolutionServices.resolveIdentity,
            txResolutionServices.resolveAttachment,
            txResolutionServices.resolveStateRef,
            { it?.let { txResolutionServices.resolveParameters(it) } },
            txResolutionServices.resolveContractAttachment,
            { true })
}


