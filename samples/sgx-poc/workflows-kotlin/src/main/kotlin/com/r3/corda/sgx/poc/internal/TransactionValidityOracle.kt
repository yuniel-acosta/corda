package com.r3.corda.sgx.poc.internal

import com.r3.corda.sgx.host.TxVerifyingOracleClient
import net.corda.core.crypto.DigitalSignature
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.WireTransaction
import java.util.concurrent.atomic.AtomicBoolean

@CordaService
class TransactionValidityOracle(val services: ServiceHub): SingletonSerializeAsToken() {

    var proxy: TxVerifyingOracleClient? = null


    fun invoke(tx: WireTransaction): DigitalSignature.WithKey {
        val oracle = proxy ?: {
            val next = TxVerifyingOracleClient(services)
            next.start()
            proxy = next
            next
        } ()

        return oracle.getEnclaveSignature(tx)
    }
}