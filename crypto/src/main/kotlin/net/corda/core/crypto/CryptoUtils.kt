package net.corda.core.crypto

import net.corda.core.DeleteForDJVM
import net.corda.core.utilities.toSHA256Bytes
import net.corda.crypto.BasicCrypto
import net.corda.crypto.internal.platformSecureRandomFactory
import java.security.*

/** Render a public key to its hash (in Base58) of its serialised form using the DL prefix. */
fun PublicKey.toStringShort(): String = "DL" + this.toSHA256Bytes().toBase58()

/**
 * Generate a securely random [ByteArray] of requested number of bytes. Usually used for seeds, nonces and keys.
 * @param numOfBytes how many random bytes to output.
 * @return a random [ByteArray].
 * @throws NoSuchAlgorithmException thrown if "NativePRNGNonBlocking" is not supported on the JVM
 * or if no strong [SecureRandom] implementations are available or if Security.getProperty("securerandom.strongAlgorithms") is null or empty,
 * which should never happen and suggests an unusual JVM or non-standard Java library.
 */
@DeleteForDJVM
@Throws(NoSuchAlgorithmException::class)
fun secureRandomBytes(numOfBytes: Int): ByteArray = ByteArray(numOfBytes).apply { newSecureRandom().nextBytes(this) }

/**
 * Get an instance of [SecureRandom] to avoid blocking, due to waiting for additional entropy, when possible.
 * In this version, the NativePRNGNonBlocking is exclusively used on Linux OS to utilize dev/urandom because in high traffic
 * /dev/random may wait for a certain amount of "noise" to be generated on the host machine before returning a result.
 *
 * On Solaris, Linux, and OS X, if the entropy gathering device in java.security is set to file:/dev/urandom
 * or file:/dev/random, then NativePRNG is preferred to SHA1PRNG. Otherwise, SHA1PRNG is preferred.
 * @see <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/security/SunProviders.html#SecureRandomImp">SecureRandom Implementation</a>.
 *
 * If both dev/random and dev/urandom are available, then dev/random is only preferred over dev/urandom during VM boot
 * where it may be possible that OS didn't yet collect enough entropy to fill the randomness pool for the 1st time.
 * @see <a href="http://www.2uo.de/myths-about-urandom/">Myths about urandom</a> for a more descriptive explanation on /dev/random Vs /dev/urandom.
 * TODO: check default settings per OS and random/urandom availability.
 * @return a [SecureRandom] object.
 * @throws NoSuchAlgorithmException thrown if "NativePRNGNonBlocking" is not supported on the JVM
 * or if no strong SecureRandom implementations are available or if Security.getProperty("securerandom.strongAlgorithms") is null or empty,
 * which should never happen and suggests an unusual JVM or non-standard Java library.
 */
@DeleteForDJVM
@Throws(NoSuchAlgorithmException::class)
fun newSecureRandom(): SecureRandom = platformSecureRandomFactory()

/**
 * Return a [Set] of the contained leaf keys if this is a [CompositeKey].
 * Otherwise, return a [Set] with a single element (this [PublicKey]).
 * <i>Note that leaf keys cannot be of type [CompositeKey].</i>
 */
val PublicKey.keys: Set<PublicKey> get() = (this as? CompositeKey)?.leafKeys ?: setOf(this)

/** Convert a byte array to a Base58 encoded [String]. */
fun ByteArray.toBase58(): String = Base58.encode(this)

/**
 * Utility to simplify the act of verifying a signature.
 *
 * @throws InvalidKeyException if the key to verify the signature with is not valid (i.e. wrong key type for the
 * signature).
 * @throws SignatureException if the signature is invalid (i.e. damaged), or does not match the key (incorrect).
 * @throws IllegalArgumentException if the signature scheme is not supported or if any of the clear or signature data is empty.
 */
// TODO: SignatureException should be used only for a damaged signature, as per `java.security.Signature.verify()`.
@Throws(SignatureException::class, InvalidKeyException::class)
fun PublicKey.verify(content: ByteArray, signature: DigitalSignature) = BasicCrypto.doVerify(this, signature.bytes, content)

/**
 * Utility to simplify the act of verifying a signature. In comparison to [verify] if the key and signature
 * do not match it returns false rather than throwing an exception. Normally you should use the function which throws,
 * as it avoids the risk of failing to test the result, but this is for uses such as [java.security.Signature.verify]
 * implementations.
 *
 * @throws InvalidKeyException if the key to verify the signature with is not valid (i.e. wrong key type for the
 * signature).
 * @throws SignatureException if the signature is invalid (i.e. damaged).
 * @throws IllegalArgumentException if the signature scheme is not supported or if any of the clear or signature data is empty.
 * @throws IllegalStateException if this is a [CompositeKey], because verification of composite key signatures is not supported.
 * @return whether the signature is correct for this key.
 */
@Throws(SignatureException::class, InvalidKeyException::class)
fun PublicKey.isValid(content: ByteArray, signature: DigitalSignature): Boolean {
    if (this is CompositeKey)
        throw IllegalStateException("Verification of CompositeKey signatures currently not supported.") // TODO CompositeSignature verification.
    return BasicCrypto.isValid(this, signature.bytes, content)
}
