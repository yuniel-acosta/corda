package net.corda.bn.demo.contracts

import com.prowidesoftware.swift.model.BIC
import net.corda.bn.states.BNIdentity
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationCustomSerializer

/**
 * Business identity specific for banks. Uses Swift Business Identifier Code (BIC).
 *
 * @property bic Business Identifier Code of the bank.
 */
@CordaSerializable
data class BankIdentity(val bic: BIC) : BNIdentity

/**
 * Custom serializer used for serialization/deserialization of [BIC].
 */
class BICSerializer : SerializationCustomSerializer<BIC, BICSerializer.Proxy> {
    data class Proxy(val bic: String)

    override fun toProxy(obj: BIC) = Proxy(obj.bic)
    override fun fromProxy(proxy: Proxy) = BIC(proxy.bic)
}