package net.corda.core.crypto

import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.MovedPackage
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.OpaqueBytes

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
@KeepForDJVM
@MovedPackage("net.corda.core.contracts", "net.corda.core.crypto")
class PrivacySalt(bytes: ByteArray) : OpaqueBytes(bytes) {
    /** Constructs a salt with a randomly-generated 32 byte value. */
    @DeleteForDJVM
    constructor() : this(secureRandomBytes(32))

    init {
        require(bytes.size == 32) { "Privacy salt should be 32 bytes." }
        require(bytes.any { it != 0.toByte() }) { "Privacy salt should not be all zeros." }
    }
}

