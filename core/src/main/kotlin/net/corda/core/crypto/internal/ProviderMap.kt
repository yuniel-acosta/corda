package net.corda.core.crypto.internal

import net.corda.core.DeleteForDJVM
import net.corda.core.crypto.CordaSecurityProvider
import net.corda.core.crypto.Crypto.EDDSA_ED25519_SHA512
import net.corda.core.crypto.Crypto.decodePrivateKey
import net.corda.core.crypto.Crypto.decodePublicKey
import net.corda.core.internal.X509EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSASecurityProvider
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jcajce.provider.asymmetric.ec.AlgorithmParametersSpi
import org.bouncycastle.jcajce.provider.util.AsymmetricKeyInfoConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import java.security.SecureRandom
import java.security.Security


@DeleteForDJVM
fun platformSecureRandomFactory(): SecureRandom = platformSecureRandom() // To minimise diff of CryptoUtils against open-source.
