package net.corda.core.identity

import net.corda.core.KeepForDJVM
import net.corda.core.crypto.toStringShort
import java.security.PublicKey

/**
 * The [AnonymousParty] class contains enough information to uniquely identify a [Party] while excluding private
 * information such as name. It is intended to represent a party on the distributed ledger.
 */
@KeepForDJVM
class AnonymousParty(owningKey: PublicKey) : AbstractParty(owningKey) {
    override fun nameOrNull(): CordaX500Name? = null
    override fun toString() = "Anonymous(${owningKey.toStringShort()})"
}