package com.r3.corda.sgx.enclave.transactions

import com.r3.corda.sgx.enclave.internal.CachedNodeServicesImpl
import com.r3.corda.sgx.enclave.internal.TrustedNodeServices
import net.corda.core.contracts.ComponentGroupEnum
import net.corda.core.transactions.ComponentGroup
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction

/**
 * A proper implementation should use the original SignedTransaction logic,
 * supplying an artificial ServiceHub
 */

fun SignedTransaction.toLedgerTransactionSgx(txResolutionData: TransactionResolutionData) =
            (this.coreTransaction as WireTransaction)
                    .toLedgerTransactionSgx(CachedNodeServicesImpl(txResolutionData))

private fun WireTransaction.toLedgerTransactionSgx(txResolutionServices: TrustedNodeServices): LedgerTransaction {
        return this.toLedgerTransactionInternal(
                txResolutionServices.resolveIdentity,
                txResolutionServices.resolveAttachment,
                txResolutionServices.resolveStateRef,
                { it?.let { txResolutionServices.resolveParameters(it) } },
                // TODO: validate attachment against internal trust model
                { true })
}


// non ledger tx are more complicated
fun WireTransaction.outputGroup(): ComponentGroup {
        return componentGroups.single {
                it.groupIndex == ComponentGroupEnum.OUTPUTS_GROUP.ordinal
        }
}