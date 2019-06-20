package net.corda.core.identity

import net.corda.core.DoNotImplement
import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey

/**
 * An [AbstractParty] contains the common elements of [Party] and [AnonymousParty], specifically the owning key of
 * the party. In most cases [Party] or [AnonymousParty] should be used, depending on use-case.
 */
@CordaSerializable
@DoNotImplement
abstract class AbstractParty(val owningKey: PublicKey) {
    /** Anonymised parties do not include any detail apart from owning key, so equality is dependent solely on the key */
    override fun equals(other: Any?): Boolean = other === this || other is AbstractParty && other.owningKey == owningKey

    override fun hashCode(): Int = owningKey.hashCode()
    abstract fun nameOrNull(): CordaX500Name?
}