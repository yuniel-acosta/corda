package net.corda.experimental.issuerwhitelist.contract

import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey

/**
 * States implementing this interface are governed by a notary whitelist defined by the [issuerKey].
 */
interface RestrictedNotaryState {
    val issuerKey: PublicKey
}

@CordaSerializable
data class NotaryWhitelist(val stateType: Class<*>, val notaries: List<Party>)