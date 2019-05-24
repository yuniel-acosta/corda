package net.corda.crypto

import net.corda.core.crypto.*
import net.corda.crypto.internal.*
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DLSequence
import org.bouncycastle.asn1.bc.BCObjectIdentifiers
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.sec.SECObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PublicKey
import org.bouncycastle.pqc.jcajce.spec.SPHINCS256KeyGenParameterSpec
import java.security.*
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

object BasicCrypto {
    object algorithms {
        const val CompositeSignatureAlgorithm = "COMPOSITESIG"
        const val CompositeKeyAlgorithm = "COMPOSITE"
    }
    @JvmStatic
    fun decodePublicKey(encodedKey: ByteArray): PublicKey {
        val subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(encodedKey)
        val signatureScheme = findSignatureScheme(subjectPublicKeyInfo.algorithm)
        val keyFactory = keyFactory(signatureScheme)
        return keyFactory.generatePublic(X509EncodedKeySpec(encodedKey))
    }

    /**
     * Decode a PKCS8 encoded key to its [PrivateKey] object.
     * Use this method if the key type is a-priori unknown.
     * @param encodedKey a PKCS8 encoded private key.
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for this key factory to produce a private key.
     */
    @JvmStatic
    fun decodePrivateKey(encodedKey: ByteArray): PrivateKey {
        val keyInfo = PrivateKeyInfo.getInstance(encodedKey)
        val signatureScheme = findSignatureScheme(keyInfo.privateKeyAlgorithm)
        val keyFactory = keyFactory(signatureScheme)
        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(encodedKey))
    }

    /**
     * Decode an X509 encoded key to its [PrivateKey] object based on the input scheme code name.
     * This should be used when the type key is known, e.g. during deserialisation or with key caches or key managers.
     * @param signatureScheme a signature scheme (e.g. ECDSA_SECP256K1_SHA256).
     * @param encodedKey an X509 encoded public key.
     * @throws IllegalArgumentException if the requested scheme is not supported.
     * @throws InvalidKeySpecException if the given key specification
     * is inappropriate for this key factory to produce a public key.
     */
    @JvmStatic
    @Throws(InvalidKeySpecException::class)
    fun decodePublicKey(signatureScheme: SignatureScheme, encodedKey: ByteArray): PublicKey {
        require(isSupportedSignatureScheme(signatureScheme)) {
            "Unsupported key/algorithm for schemeCodeName: ${signatureScheme.schemeCodeName}"
        }
        try {
            val keyFactory = keyFactory(signatureScheme)
            return keyFactory.generatePublic(X509EncodedKeySpec(encodedKey))
        } catch (ikse: InvalidKeySpecException) {
            throw throw InvalidKeySpecException("This public key cannot be decoded, please ensure it is X509 encoded and " +
                    "that it corresponds to the input scheme's code name.", ikse)
        }
    }

    /**
     * Decode a PKCS8 encoded key to its [PrivateKey] object based on the input scheme code name.
     * This should be used when the type key is known, e.g. during deserialisation or with key caches or key managers.
     * @param signatureScheme a signature scheme (e.g. ECDSA_SECP256K1_SHA256).
     * @param encodedKey a PKCS8 encoded private key.
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for this key factory to produce a private key.
     */
    @JvmStatic
    @Throws(InvalidKeySpecException::class)
    fun decodePrivateKey(signatureScheme: SignatureScheme, encodedKey: ByteArray): PrivateKey {
        require(isSupportedSignatureScheme(signatureScheme)) {
            "Unsupported key/algorithm for schemeCodeName: ${signatureScheme.schemeCodeName}"
        }
        try {
            val keyFactory = keyFactory(signatureScheme)
            return keyFactory.generatePrivate(PKCS8EncodedKeySpec(encodedKey))
        } catch (ikse: InvalidKeySpecException) {
            throw InvalidKeySpecException("This private key cannot be decoded, please ensure it is PKCS8 encoded and that " +
                    "it corresponds to the input scheme's code name.", ikse)
        }
    }

    /** Check if the requested [SignatureScheme] is supported by the system. */
    @JvmStatic
    fun isSupportedSignatureScheme(signatureScheme: SignatureScheme): Boolean {
        return signatureScheme.schemeCodeName in signatureSchemeMap
    }

    /**
     * Normalise an algorithm identifier by converting [DERNull] parameters into a Kotlin null value.
     */
    private fun normaliseAlgorithmIdentifier(id: AlgorithmIdentifier): AlgorithmIdentifier {
        return if (id.parameters is DERNull) {
            AlgorithmIdentifier(id.algorithm, null)
        } else {
            id
        }
    }

    /**
     * Factory pattern to retrieve the corresponding [SignatureScheme] based on [SignatureScheme.schemeCodeName].
     * This function is usually called by key generators and verify signature functions.
     * In case the input is not a key in the supportedSignatureSchemes map, null will be returned.
     * @param schemeCodeName a [String] that should match a supported signature scheme code name (e.g. ECDSA_SECP256K1_SHA256), see [Crypto].
     * @return a currently supported SignatureScheme.
     * @throws IllegalArgumentException if the requested signature scheme is not supported.
     */
    @JvmStatic
    fun findSignatureScheme(schemeCodeName: String): SignatureScheme {
        return signatureSchemeMap[schemeCodeName]
                ?: throw IllegalArgumentException("Unsupported key/algorithm for schemeCodeName: $schemeCodeName")
    }

    /**
     * Retrieve the corresponding [SignatureScheme] based on the type of the input [Key].
     * This function is usually called when requiring to verify signatures and the signing schemes must be defined.
     * For the supported signature schemes see [Crypto].
     * @param key either private or public.
     * @return a currently supported SignatureScheme.
     * @throws IllegalArgumentException if the requested key type is not supported.
     */
    @JvmStatic
    fun findSignatureScheme(key: PublicKey): SignatureScheme {
        val keyInfo = SubjectPublicKeyInfo.getInstance(key.encoded)
        return findSignatureScheme(keyInfo.algorithm)
    }

    @JvmStatic
    fun findSignatureScheme(algorithm: AlgorithmIdentifier): SignatureScheme {
        return algorithmMap[normaliseAlgorithmIdentifier(algorithm)]
                ?: throw IllegalArgumentException("Unrecognised algorithm: ${algorithm.algorithm.id}")
    }

    private fun keyFactory(signatureScheme: SignatureScheme) = signatureScheme.getKeyFactory {
        KeyFactory.getInstance(signatureScheme.algorithmName, providerMap[signatureScheme.providerName])
    }

    /**
     * RSA PKCS#1 signature scheme using SHA256 for message hashing.
     * The actual algorithm id is 1.2.840.113549.1.1.1
     * Note: Recommended key size >= 3072 bits.
     */
    @JvmField
    val RSA_SHA256 = SignatureScheme(
            1,
            "RSA_SHA256",
            AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption, null),
            listOf(AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, null)),
            cordaBouncyCastleProvider.name,
            "RSA",
            "SHA256WITHRSA",
            null,
            3072,
            "RSA_SHA256 signature scheme using SHA256 as hash algorithm."
    )

    /** ECDSA signature scheme using the secp256k1 Koblitz curve and SHA256 for message hashing. */
    @JvmField
    val ECDSA_SECP256K1_SHA256 = SignatureScheme(
            2,
            "ECDSA_SECP256K1_SHA256",
            AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA256, SECObjectIdentifiers.secp256k1),
            listOf(AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256k1)),
            cordaBouncyCastleProvider.name,
            "ECDSA",
            "SHA256withECDSA",
            ECNamedCurveTable.getParameterSpec("secp256k1"),
            256,
            "ECDSA signature scheme using the secp256k1 Koblitz curve."
    )

    /** ECDSA signature scheme using the secp256r1 (NIST P-256) curve and SHA256 for message hashing. */
    @JvmField
    val ECDSA_SECP256R1_SHA256 = SignatureScheme(
            3,
            "ECDSA_SECP256R1_SHA256",
            AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA256, SECObjectIdentifiers.secp256r1),
            listOf(AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256r1)),
            cordaBouncyCastleProvider.name,
            "ECDSA",
            "SHA256withECDSA",
            ECNamedCurveTable.getParameterSpec("secp256r1"),
            256,
            "ECDSA signature scheme using the secp256r1 (NIST P-256) curve."
    )

    /**
     * EdDSA signature scheme using the ed25519 twisted Edwards curve and SHA512 for message hashing.
     * The actual algorithm is PureEdDSA Ed25519 as defined in https://tools.ietf.org/html/rfc8032
     * Not to be confused with the EdDSA variants, Ed25519ctx and Ed25519ph.
     */
    @JvmField
    val EDDSA_ED25519_SHA512: SignatureScheme = SignatureScheme(
            4,
            "EDDSA_ED25519_SHA512",
            AlgorithmIdentifier(`id-Curve25519ph`, null),
            emptyList(), // Both keys and the signature scheme use the same OID in i2p library.
            // We added EdDSA to bouncy castle for certificate signing.
            cordaBouncyCastleProvider.name,
            "1.3.101.112",
            EdDSAEngine.SIGNATURE_ALGORITHM,
            EdDSANamedCurveTable.getByName("ED25519"),
            256,
            "EdDSA signature scheme using the ed25519 twisted Edwards curve."
    )

    /** DLSequence (ASN1Sequence) for SHA512 truncated to 256 bits, used in SPHINCS-256 signature scheme. */
    @JvmField
    val SHA512_256 = DLSequence(arrayOf(NISTObjectIdentifiers.id_sha512_256))

    /**
     * SPHINCS-256 hash-based signature scheme using SHA512 for message hashing. It provides 128bit security against
     * post-quantum attackers at the cost of larger key nd signature sizes and loss of compatibility.
     */
    // TODO: change val name to SPHINCS256_SHA512. This will break backwards compatibility.
    @JvmField
    val SPHINCS256_SHA256 = SignatureScheme(
            5,
            "SPHINCS-256_SHA512",
            AlgorithmIdentifier(BCObjectIdentifiers.sphincs256_with_SHA512, DLSequence(arrayOf(ASN1Integer(0), SHA512_256))),
            listOf(AlgorithmIdentifier(BCObjectIdentifiers.sphincs256, DLSequence(arrayOf(ASN1Integer(0), SHA512_256)))),
            bouncyCastlePQCProvider.name,
            "SPHINCS256",
            "SHA512WITHSPHINCS256",
            SPHINCS256KeyGenParameterSpec(SPHINCS256KeyGenParameterSpec.SHA512_256),
            256,
            "SPHINCS-256 hash-based signature scheme. It provides 128bit security against post-quantum attackers " +
                    "at the cost of larger key sizes and loss of compatibility."
    )

    /** Corda [CompositeKey] signature type. */
    // TODO: change the val name to a more descriptive one as it's now confusing and looks like a Key type.
    @JvmField
    val COMPOSITE_KEY = SignatureScheme(
            6,
            "COMPOSITE",
            AlgorithmIdentifier(CordaObjectIdentifier.COMPOSITE_KEY),
            emptyList(),
            cordaSecurityProvider.name,
            algorithms.CompositeKeyAlgorithm,
            algorithms.CompositeSignatureAlgorithm,
            null,
            null,
            "Composite keys composed from individual public keys"
    )

    /** Our default signature scheme if no algorithm is specified (e.g. for key generation). */
    @JvmField
    val DEFAULT_SIGNATURE_SCHEME = EDDSA_ED25519_SHA512

    /**
     * Supported digital signature schemes.
     * Note: Only the schemes added in this map will be supported (see [Crypto]).
     */
    private val signatureSchemeMap: Map<String, SignatureScheme> = listOf(
            RSA_SHA256,
            ECDSA_SECP256K1_SHA256,
            ECDSA_SECP256R1_SHA256,
            EDDSA_ED25519_SHA512,
            SPHINCS256_SHA256,
            COMPOSITE_KEY
    ).associateBy { it.schemeCodeName }

    /**
     * Map of X.509 algorithm identifiers to signature schemes Corda recognises. See RFC 2459 for the format of
     * algorithm identifiers.
     */
    private val algorithmMap: Map<AlgorithmIdentifier, SignatureScheme> = (signatureSchemeMap.values.flatMap { scheme -> scheme.alternativeOIDs.map { Pair(it, scheme) } }
            + signatureSchemeMap.values.map { Pair(it.signatureOID, it) })
            .toMap()

    /**
     * Utility to simplify the act of verifying a digital signature.
     * It returns true if it succeeds, but it always throws an exception if verification fails.
     * @param schemeCodeName a signature scheme's code name (e.g. ECDSA_SECP256K1_SHA256).
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     * @param clearData the clear data/message that was signed (usually the Merkle root).
     * @return true if verification passes or throws an exception if verification fails.
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData scheme is unable to process the input data provided, if the verification is not possible.
     * @throws IllegalArgumentException if the signature scheme is not supported or if any of the clear or signature data is empty.
     */
    @JvmStatic
    @Throws(InvalidKeyException::class, SignatureException::class)
    fun doVerify(schemeCodeName: String, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean {
        return doVerify(findSignatureScheme(schemeCodeName), publicKey, signatureData, clearData)
    }

    /**
     * Utility to simplify the act of verifying a digital signature by identifying the signature scheme used from the input public key's type.
     * It returns true if it succeeds, but it always throws an exception if verification fails.
     * Strategy on identifying the actual signing scheme is based on the [PublicKey] type, but if the schemeCodeName is known,
     * then better use doVerify(schemeCodeName: String, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray).
     *
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     * @param clearData the clear data/message that was signed (usually the Merkle root).
     * @return true if verification passes or throws an exception if verification fails.
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData scheme is unable to process the input data provided, if the verification is not possible.
     * @throws IllegalArgumentException if the signature scheme is not supported or if any of the clear or signature data is empty.
     */
    @JvmStatic
    @Throws(InvalidKeyException::class, SignatureException::class)
    fun doVerify(publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean {
        return doVerify(findSignatureScheme(publicKey), publicKey, signatureData, clearData)
    }

    /**
     * Method to verify a digital signature.
     * It returns true if it succeeds, but it always throws an exception if verification fails.
     * @param signatureScheme a [SignatureScheme] object, retrieved from supported signature schemes, see [Crypto].
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     * @param clearData the clear data/message that was signed (usually the Merkle root).
     * @return true if verification passes or throws an exception if verification fails.
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData scheme is unable to process the input data provided, if the verification is not possible.
     * @throws IllegalArgumentException if the signature scheme is not supported or if any of the clear or signature data is empty.
     */
    @JvmStatic
    @Throws(InvalidKeyException::class, SignatureException::class)
    fun doVerify(signatureScheme: SignatureScheme, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean {
        require(isSupportedSignatureScheme(signatureScheme)) {
            "Unsupported key/algorithm for schemeCodeName: ${signatureScheme.schemeCodeName}"
        }
        if (signatureData.isEmpty()) throw IllegalArgumentException("Signature data is empty!")
        if (clearData.isEmpty()) throw IllegalArgumentException("Clear data is empty, nothing to verify!")
        val verificationResult = isValid(signatureScheme, publicKey, signatureData, clearData)
        if (verificationResult) {
            return true
        } else {
            throw SignatureException("Signature Verification failed!")
        }
    }

    /**
     * Method to verify a digital signature. In comparison to [doVerify] if the key and signature
     * do not match it returns false rather than throwing an exception.
     * Use this method if the signature scheme type is a-priori unknown.
     * @param signatureScheme a [SignatureScheme] object, retrieved from supported signature schemes, see [Crypto].
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     * @param clearData the clear data/message that was signed (usually the Merkle root).
     * @return true if verification passes or false if verification fails.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData scheme is unable to process the input data provided, if the verification is not possible.
     * @throws IllegalArgumentException if the requested signature scheme is not supported.
     */
    @JvmStatic
    @Throws(SignatureException::class)
    fun isValid(signatureScheme: SignatureScheme, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean {
        require(isSupportedSignatureScheme(signatureScheme)) {
            "Unsupported key/algorithm for schemeCodeName: ${signatureScheme.schemeCodeName}"
        }
        val signature = Instances.getSignatureInstance(signatureScheme.signatureName, providerMap[signatureScheme.providerName])
        signature.initVerify(publicKey)
        signature.update(clearData)
        return signature.verify(signatureData)
    }

    /**
     * Utility to simplify the act of verifying a digital signature by identifying the signature scheme used from the
     * input public key's type.
     * It returns true if it succeeds and false if not. In comparison to [doVerify] if the key and signature
     * do not match it returns false rather than throwing an exception. Normally you should use the function which throws,
     * as it avoids the risk of failing to test the result.
     * @param txId transaction's id.
     * @param transactionSignature the signature on the transaction.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData scheme is unable to process the input data provided, if the verification is not possible.
     */
    @JvmStatic
    @Throws(SignatureException::class)
    fun isValid(txId: SecureHash, transactionSignature: TransactionSignature): Boolean {
        val signableData = SignableData(originalSignedHash(txId, transactionSignature.partialMerkleTree), transactionSignature.signatureMetadata)
        return isValid(
                findSignatureScheme(transactionSignature.by),
                transactionSignature.by,
                transactionSignature.bytes,
                SignableDataSerializer.serialize(signableData).bytes)
    }

    /**
     * Utility to simplify the act of verifying a digital signature by identifying the signature scheme used from the
     * input public key's type.
     * It returns true if it succeeds and false if not. In comparison to [doVerify] if the key and signature
     * do not match it returns false rather than throwing an exception. Normally you should use the function which throws,
     * as it avoids the risk of failing to test the result.
     * Use this method if the signature scheme is not a-priori known.
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     * @param clearData the clear data/message that was signed (usually the Merkle root).
     * @return true if verification passes or false if verification fails.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData scheme is unable to process the input data provided, if the verification is not possible.
     */
    @JvmStatic
    @Throws(SignatureException::class)
    fun isValid(publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean {
        return isValid(findSignatureScheme(publicKey), publicKey, signatureData, clearData)
    }

    /**
     * Utility to simplify the act of verifying a [TransactionSignature].
     * It returns true if it succeeds, but it always throws an exception if verification fails.
     * @param txId transaction's id.
     * @param transactionSignature the signature on the transaction.
     * @return true if verification passes or throw exception if verification fails.
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException if this signatureData object is not initialized properly,
     * the passed-in signatureData is improperly encoded or of the wrong type,
     * if this signatureData scheme is unable to process the input data provided, if the verification is not possible.
     * @throws IllegalArgumentException if the signature scheme is not supported or if any of the clear or signature data is empty.
     */
    @JvmStatic
    @Throws(InvalidKeyException::class, SignatureException::class)
    fun doVerify(txId: SecureHash, transactionSignature: TransactionSignature): Boolean {
        val signableData = SignableData(originalSignedHash(txId, transactionSignature.partialMerkleTree), transactionSignature.signatureMetadata)
        return BasicCrypto.doVerify(transactionSignature.by, transactionSignature.bytes, SignableDataSerializer.serialize(signableData).bytes)
    }

    /**
     *  Get the hash value that is actually signed.
     *  The txId is returned when [partialMerkleTree] is null,
     *  else the root of the tree is computed and returned.
     *  Note that the hash of the txId should be a leaf in the tree, not the txId itself.
     */
    private fun originalSignedHash(txId: SecureHash, partialMerkleTree: PartialMerkleTree?): SecureHash {
        return if (partialMerkleTree != null) {
            val usedHashes = mutableListOf<SecureHash>()
            val root = PartialMerkleTree.rootAndUsedHashes(partialMerkleTree.root, usedHashes)
            require(txId.sha256() in usedHashes) { "Transaction with id:$txId is not a leaf in the provided partial Merkle tree" }
            root
        } else {
            txId
        }
    }

    /**
     * Convert a public key to a supported implementation.
     * @param key a public key.
     * @return a supported implementation of the input public key.
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for a supported key factory to produce a private key.
     */
    @JvmStatic
    fun toSupportedPublicKey(key: SubjectPublicKeyInfo): PublicKey = decodePublicKey(key.encoded)

    /**
     * Convert a public key to a supported implementation. This can be used to convert a SUN's EC key to an BC key.
     * This method is usually required to retrieve a key (via its corresponding cert) from JKS keystores that by default
     * return SUN implementations.
     * @param key a public key.
     * @return a supported implementation of the input public key.
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for a supported key factory to produce a private key.
     */
    @JvmStatic
    fun toSupportedPublicKey(key: PublicKey): PublicKey {
        return when (key) {
            is BCECPublicKey -> key
            is BCRSAPublicKey -> key
            is BCSphincs256PublicKey -> key
            is EdDSAPublicKey -> key
            is CompositeKey -> key
            else -> decodePublicKey(key.encoded)
        }
    }



}