@file:JvmName("Structures")
package net.corda.core.contracts



import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.secureRandomBytes
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.OpaqueBytes

// DOCSTART 1
/** Implemented by anything that can be named by a secure hash value (e.g. transactions, attachments). */
interface NamedByHash {
    val id: SecureHash
}
// DOCEND 1

/**
 * The [Issued] data class holds the details of an on ledger digital asset.
 * In particular it gives the public credentials of the entity that created these digital tokens
 * and the particular product represented.
 *
 * @param P the class type of product underlying the definition, for example [java.util.Currency].
 * @property issuer The [AbstractParty] details of the entity which issued the asset
 * and a reference blob, which can contain other details related to the token creation e.g. serial number,
 * warehouse location, etc.
 * The issuer is the gatekeeper for creating, or destroying the tokens on the digital ledger and
 * only their [PrivateKey] signature can authorise transactions that do not conserve the total number
 * of tokens on the ledger.
 * Other identities may own the tokens, but they can only create transactions that conserve the total token count.
 * Typically the issuer is also a well know organisation that can convert digital tokens to external assets
 * and thus underwrites the digital tokens.
 * Different issuer values may coexist for a particular product, but these cannot be merged.
 * @property product The details of the specific product represented by these digital tokens. The value
 * of product may differentiate different kinds of asset within the same logical class e.g the currency, or
 * it may just be a type marker for a single custom asset.
 */
@CordaSerializable
data class Issued<out P : Any>(val issuer: PartyAndReference, val product: P) {
    init {
        require(issuer.reference.size <= MAX_ISSUER_REF_SIZE) { "Maximum issuer reference size is $MAX_ISSUER_REF_SIZE." }
    }

    override fun toString() = "$product issued by $issuer"
}

/**
 * The maximum permissible size of an issuer reference.
 */
const val MAX_ISSUER_REF_SIZE = 512

/**
 * Strips the issuer and returns an [Amount] of the raw token directly. This is useful when you are mixing code that
 * cares about specific issuers with code that will accept any, or which is imposing issuer constraints via some
 * other mechanism and the additional type safety is not wanted.
 */
fun <T : Any> Amount<Issued<T>>.withoutIssuer(): Amount<T> = Amount(quantity, displayTokenSize, token.product)

/**
 * Reference to something being stored or issued by a party e.g. in a vault or (more likely) on their normal
 * ledger. The reference is intended to be encrypted so it's meaningless to anyone other than the party.
 */
@CordaSerializable
data class PartyAndReference(val party: AbstractParty, val reference: OpaqueBytes) {
    override fun toString() = "$party$reference"
}

/**
 * A privacy salt is required to compute nonces per transaction component in order to ensure that an adversary cannot
 * use brute force techniques and reveal the content of a Merkle-leaf hashed value.
 * Because this salt serves the role of the seed to compute nonces, its size and entropy should be equal to the
 * underlying hash function used for Merkle tree generation, currently [SecureHash.SHA256], which has an output of 32 bytes.
 * There are two constructors, one that generates a new 32-bytes random salt, and another that takes a [ByteArray] input.
 * The latter is required in cases where the salt value needs to be pre-generated (agreed between transacting parties),
 * but it is highlighted that one should always ensure it has sufficient entropy.
 */
@CordaSerializable
class PrivacySalt(bytes: ByteArray) : OpaqueBytes(bytes) {
    /** Constructs a salt with a randomly-generated 32 byte value. */
    
    constructor() : this(secureRandomBytes(32))

    init {
        require(bytes.size == 32) { "Privacy salt should be 32 bytes." }
        require(bytes.any { it != 0.toByte() }) { "Privacy salt should not be all zeros." }
    }
}
