package com.r3.corda.sgx.enclave

import com.r3.corda.sgx.common.AttachmentIdValidator
import com.r3.corda.sgx.common.TrustedNodeServices
import com.r3.corda.sgx.common.transactions.ResolvableWireTransaction
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.Verifier

class TransactionVerifyingEnclavelet : CordaEnclavelet<ResolvableWireTransaction, SecureHash>() {

    // TODO: inject trusted measurement from classpath
    override val attachmentIdValidators: List<AttachmentIdValidator> get() = emptyList()

    internal class ConnectionImpl(val services: TrustedNodeServices): CordaEnclavelet.Connection<ResolvableWireTransaction, SecureHash> {
        override fun process(input: ResolvableWireTransaction): SecureHash {
            val ltx = input.toLedgerTransactionSgx(services)
            ltx.verify()
            return ltx.id
        }
    }

    override fun connect(services: TrustedNodeServices): CordaEnclavelet.Connection<ResolvableWireTransaction, SecureHash> {
        return ConnectionImpl(services)
    }
}