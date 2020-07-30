package net.corda.bn.demo.contracts

import com.prowidesoftware.swift.model.BIC
import net.corda.bn.states.BNIdentity
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.SerializationWhitelist

/**
 * Business identity specific for banks. Uses Swift Business Identifier Code (BIC).
 *
 * @property bic Business Identifier Code of the bank.
 */
@CordaSerializable
data class BankIdentity(val bic: BIC) : BNIdentity

/**
 * [BIC] cannot be whitelisted with [CordaSerializable] annotation since it is external dependency so we need to create custom class
 * implementing [SerializationWhitelist].
 */
class WhitelistBIC : SerializationWhitelist {
    override val whitelist = listOf(BIC::class.java)
}

/**
 * Custom serializer used for serialization/deserialization of [BIC].
 */
class BICSerializer : SerializationCustomSerializer<BIC, BICSerializer.Proxy> {
    data class Proxy(val bic: String)

    override fun toProxy(obj: BIC) = Proxy(obj.bic)
    override fun fromProxy(proxy: Proxy) = BIC(proxy.bic)
}