package net.corda.core.crypto


import com.r3.sgx.core.common.SgxMeasurement
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.OpaqueBytes
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer
import java.security.*
import java.security.spec.InvalidKeySpecException
import java.security.spec.KeySpec
import java.security.spec.X509EncodedKeySpec


/**
 * A representation of an enclave measurement as a [PublicKey]
 */

class EnclaveIdentity(val measurement: OpaqueBytes, val mode: EnclaveMode = EnclaveMode.RELEASE): PublicKey {

    companion object {
        val KEY_ALGORITHM = "ENCLAVE_REMOTE_ATTESTATION"

        private val ENCODED_SIZE = 4 + SgxMeasurement.size

        fun getInstance(encoded: ByteArray): EnclaveIdentity {
            if (encoded.size != ENCODED_SIZE) {
                throw IllegalArgumentException("Invalid encoded key size")
            }
            val buffer = ByteBuffer.wrap(encoded)
            val id = buffer.getInt()
            val enclaveMode = EnclaveMode.values().find { id == it.id } ?:
                    throw IllegalArgumentException("Unknown enclave mode")
            val measurement = ByteArray(SgxMeasurement.size)
            buffer.get(measurement)
            return EnclaveIdentity(OpaqueBytes(measurement), enclaveMode)
        }
    }

    enum class EnclaveMode(val id: Int) {
        RELEASE(0),
        DEBUG(1),
        SIMULATION(2)
    }

    // Used by corda provider
    class FactorySpi: KeyFactorySpi() {

        @Throws(InvalidKeySpecException::class)
        override fun engineGeneratePrivate(keySpec: KeySpec): PrivateKey {
            // Private composite key not supported.
            throw InvalidKeySpecException("key spec not recognised: " + keySpec.javaClass)
        }

        @Throws(InvalidKeySpecException::class)
        override fun engineGeneratePublic(keySpec: KeySpec): PublicKey? {
            return when (keySpec) {
                is X509EncodedKeySpec -> EnclaveIdentity.getInstance(keySpec.encoded)
                else -> throw InvalidKeySpecException("key spec not recognised: " + keySpec.javaClass)
            }
        }

        @Throws(InvalidKeySpecException::class)
        override fun <T : KeySpec> engineGetKeySpec(key: Key, keySpec: Class<T>): T {
            // Only support X509EncodedKeySpec.
            throw InvalidKeySpecException("Not implemented yet $key $keySpec")
        }

        @Throws(InvalidKeyException::class)
        override fun engineTranslateKey(key: Key): Key {
            throw InvalidKeyException("No other composite key providers known")
        }
    }

    init {
        if (measurement.bytes.size != SgxMeasurement.size) {
            throw IllegalArgumentException("Invalid measument size in input")
        }
    }

    override fun getAlgorithm(): String {
        return KEY_ALGORITHM
    }

    override fun getEncoded(): ByteArray {
        val encoded = ByteArray(ENCODED_SIZE)
        ByteBuffer.wrap(encoded).apply {
            putInt(mode.id)
            put(measurement.bytes)
        }
        return SubjectPublicKeyInfo(
                AlgorithmIdentifier(CordaObjectIdentifier.ENCLAVE_ATTESTATION),
                encoded).encoded
    }

    override fun getFormat() = ASN1Encoding.DER
}