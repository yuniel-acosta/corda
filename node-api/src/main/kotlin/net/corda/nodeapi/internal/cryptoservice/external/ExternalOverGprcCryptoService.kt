package net.corda.nodeapi.internal.cryptoservice.external

import net.corda.core.crypto.SignatureScheme
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import net.corda.nodeapi.internal.cryptoservice.proto.ExternalCryptoServiceGrpc
import net.corda.nodeapi.internal.cryptoservice.proto.KeyRequest
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import java.io.OutputStream
import java.security.PublicKey
import org.bouncycastle.operator.ContentSigner as ContentSigner1

class ExternalOverGprcCryptoService : CryptoService {

    override fun containsKey(alias: String): Boolean {
        return false
    }

    override fun getPublicKey(alias: String): PublicKey? {
        return null
    }

    override fun sign(alias: String, data: ByteArray, signAlgorithm: String?): ByteArray {
        return ByteArray(0)
    }

    override fun getSigner(alias: String): ContentSigner1 {
        return object : ContentSigner1 {
            override fun getAlgorithmIdentifier(): AlgorithmIdentifier {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun getOutputStream(): OutputStream {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun getSignature(): ByteArray {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }
        }
    }

    override fun generateKeyPair(alias: String, scheme: SignatureScheme): PublicKey {

        val externalCryptoStub = ExternalCryptoServiceGrpc.newBlockingStub(null)

        val request = KeyRequest.newBuilder().setName("testKey").build()
        val response = externalCryptoStub.generateKeyPair(request)
        return object : PublicKey {
            override fun getAlgorithm(): String {
                TODO("not implemented")
            }

            override fun getEncoded(): ByteArray {
                TODO("not implemented")
            }

            override fun getFormat(): String {
                TODO("not implemented")
            }
        }
    }

    override fun getType(): SupportedCryptoServices {
        return SupportedCryptoServices.EXTERNAL
    }
}