package net.corda.nodeapi.internal.cryptoservice.external

import com.google.protobuf.ByteString
import io.grpc.ManagedChannelBuilder
import net.corda.core.crypto.SignatureScheme
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import net.corda.nodeapi.internal.cryptoservice.proto.ExternalCryptoServiceGrpc
import net.corda.nodeapi.internal.cryptoservice.proto.KeyRequest
import net.corda.nodeapi.internal.cryptoservice.proto.SignRequest
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import java.io.OutputStream
import java.security.PublicKey
import org.bouncycastle.operator.ContentSigner as ContentSigner1

class ExternalOverGprcCryptoServiceClient(val host: String, val port: Int) : CryptoService {

    override fun containsKey(alias: String): Boolean {
        return getPublicKey(alias) != null
    }

    override fun getPublicKey(alias: String): PublicKey? {
        val externalCryptoStub = ExternalCryptoServiceGrpc.newBlockingStub(ManagedChannelBuilder.forAddress(host, port).build())
        val request = KeyRequest.newBuilder().setName(alias).build()
        val response = externalCryptoStub.getKey(request)

        if (response.key != null) {
            return object : PublicKey {
                override fun getAlgorithm(): String {
                    return response.algorithm
                }

                override fun getEncoded(): ByteArray {
                    return response.key.toByteArray();
                }

                override fun getFormat(): String {
                    return response.format;
                }
            }
        } else {
            return null;
        }
    }

    override fun sign(alias: String, data: ByteArray, signAlgorithm: String?): ByteArray {
        val externalCryptoStub = ExternalCryptoServiceGrpc
                .newBlockingStub(ManagedChannelBuilder.forAddress(host, port).build())

        val signResponse = externalCryptoStub.sign(
                SignRequest.newBuilder()
                        .setAlias(alias)
                        .setData(ByteString.copyFrom(data))
                        .setSignAlgorithm(signAlgorithm).build()
        )
        return signResponse.data.toByteArray()
    }

    override fun getSigner(alias: String): ContentSigner1 {
        return object : ContentSigner1 {
            val backingOutput = ByteString.newOutput()
            override fun getAlgorithmIdentifier(): AlgorithmIdentifier {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun getOutputStream(): OutputStream {
                return backingOutput
            }

            override fun getSignature(): ByteArray {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }
        }
    }

    override fun generateKeyPair(alias: String, scheme: SignatureScheme): PublicKey {

        val externalCryptoStub = ExternalCryptoServiceGrpc
                .newBlockingStub(ManagedChannelBuilder.forAddress(host, port).build())

        val request = KeyRequest.newBuilder().setName(alias).build()
        val response = externalCryptoStub.generateKeyPair(request)
        return object : PublicKey {
            override fun getAlgorithm(): String {
                return response.algorithm;
            }

            override fun getEncoded(): ByteArray {
                return response.key.toByteArray()
            }

            override fun getFormat(): String {
                return response.format;
            }
        }
    }

    override fun getType(): SupportedCryptoServices {
        return SupportedCryptoServices.EXTERNAL
    }
}