package net.corda.node.services.cryptoservice
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceException
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.operator.ContentSigner
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Path
import java.security.PublicKey
import com.google.firebase.FirebaseApp
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import java.io.FileInputStream
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.stub.StreamObserver
import net.corda.core.internal.concurrent.OpenFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.node.services.cryptoservice.android.*
import net.corda.nodeapi.internal.config.UnknownConfigurationKeysException
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import org.iq80.snappy.Snappy
import java.lang.Exception
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of the Crypto service for signing on Android devices.
 *
 * Using FirebaseCloudMessaging to communicate with the device.
 */
class AndroidCryptoService(val config: AndroidCryptoServiceConfig) : CryptoService {
    init {
        // TODO: config and start method
        val serviceAccount = FileInputStream(config.serviceAccountPath)
        val options = FirebaseOptions.Builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .setDatabaseUrl(config.databaseUrl)
                .build()
        FirebaseApp.initializeApp(options)
    }

    val server = AndroidSigningServer(config.port)

    fun start() {
        server.start()
    }

    val keys = mutableMapOf<String, PublicKey>()
    val clientToken = config.clientToken
    val respondTo = config.host + ":" + config.port

    override fun getType(): SupportedCryptoServices = SupportedCryptoServices.ANDROID_FCM

    override fun generateKeyPair(alias: String, scheme: SignatureScheme): PublicKey {
        log.info("alias: $alias, scheme: $scheme")
        val requestId = UUID.randomUUID().toString()
        val message = Message.builder()
                .putData("generateKeyPair", alias)
                .putData("requestId", requestId)
                .putData("respondTo", respondTo)
                .setToken(clientToken)
                .build()
        log.info("sending message")

        val publicKeyFut = server.registerRequest(requestId)

        val response = FirebaseMessaging.getInstance().send(message)
        log.info("sent message, response: $response")

        val pk = publicKeyFut.get()
        log.info("got pk: $requestId")

        keys[alias] = pk
        return pk
    }

    override fun sign(alias: String, data: ByteArray, signAlgorithm: String?): ByteArray {
        log.info("sign: $alias")
        val requestId = UUID.randomUUID().toString()
        val compressed = Snappy.compress(data)
        val dataBase64 = Base64.getEncoder().encodeToString(compressed)
        log.info("data size: ${dataBase64.length}")

        if (dataBase64.length > 4096) {
            throw Exception("payload too big")
        }
        val message = Message.builder()
                .putData("sign", dataBase64)
                .putData("alias", alias)
                .putData("requestId", requestId)
                .putData("respondTo", respondTo)
                .setToken(clientToken)
                .build()
        log.info("sending message")
        val sigFut = server.registerSigRequest(requestId)
        val response = FirebaseMessaging.getInstance().send(message)
        log.info("sent message, response: $response")

        val sig = sigFut.get()
        log.info("got signature: $requestId")
        return sig
    }

    override fun containsKey(alias: String): Boolean {
        log.info("contains key: $alias")
        val requestId = UUID.randomUUID().toString()
        val message = Message.builder()
                .putData("containsKey", alias)
                .putData("requestId", requestId)
                .putData("respondTo", respondTo)
                .setToken(clientToken)
                .build()
        log.info("sending message")
        val fut = server.registerContainsKeyRequest(requestId)
        val response = FirebaseMessaging.getInstance().send(message)
        log.info("sent message, response: $response")
        return fut.get()
    }

    override fun getPublicKey(alias: String): PublicKey? {
        log.info("get public key: $alias")
        val requestId = UUID.randomUUID().toString()
        val message = Message.builder()
                .putData("getPublicKey", alias)
                .putData("requestId", requestId)
                .putData("respondTo", respondTo)
                .setToken(clientToken)
                .build()
        log.info("sending message")
        val fut = server.registerGetPublicKeyRequest(requestId)
        val response = FirebaseMessaging.getInstance().send(message)
        log.info("sent message, response: $response")
        return fut.get()
    }

    override fun getSigner(alias: String): ContentSigner {
        log.info("get signer: $alias")

        return object : ContentSigner {
            private val publicKey: PublicKey = getPublicKey(alias) ?: throw CryptoServiceException("No key found for alias $alias")
            private val sigAlgID: AlgorithmIdentifier = Crypto.findSignatureScheme(publicKey).signatureOID

            private val baos = ByteArrayOutputStream()
            override fun getAlgorithmIdentifier(): AlgorithmIdentifier = sigAlgID
            override fun getOutputStream(): OutputStream = baos
            override fun getSignature(): ByteArray = sign(alias, baos.toByteArray())
        }
    }


    override fun defaultIdentitySignatureScheme(): SignatureScheme {
        return DEFAULT_IDENTITY_SIGNATURE_SCHEME
    }

    companion object {
        val DEFAULT_IDENTITY_SIGNATURE_SCHEME = Crypto.ECDSA_SECP256R1_SHA256
        val DEFAULT_TLS_SIGNATURE_SCHEME = Crypto.ECDSA_SECP256R1_SHA256

        fun fromConfigurationFile(configFile: Path): AndroidCryptoService {
            return AndroidCryptoService(parseConfig(configFile))
        }
        val log = contextLogger()

        fun parseConfig(path: Path): AndroidCryptoServiceConfig {
            try {
                val config = ConfigFactory.parseFile(path.toFile()).resolve()
                return config.parseAs(AndroidCryptoServiceConfig::class)
            } catch (e: Exception) {
                when(e) {
                    is ConfigException, is UnknownConfigurationKeysException -> throw Exception("Error in ${path.toFile().absolutePath} : ${e.message}")
                    else -> throw e
                }
            }
        }
    }

    data class AndroidCryptoServiceConfig(
            val host: String,
            val port: Int,
            val clientToken: String,
            val serviceAccountPath: String,
            val databaseUrl: String
    )
}

class AndroidSigningServer(val port: Int) {
    var server: Server? = null

    val futures = ConcurrentHashMap<String, OpenFuture<PublicKey>>()
    val sigFutures = ConcurrentHashMap<String, OpenFuture<ByteArray>>()
    val containsKeyFutures = ConcurrentHashMap<String, OpenFuture<Boolean>>()
    val publicKeyFutures = ConcurrentHashMap<String, OpenFuture<PublicKey>>()

    fun start() {
        server = ServerBuilder.forPort(port)
                .addService(SigningServerImpl(futures, sigFutures, containsKeyFutures, publicKeyFutures))
                .build()
                .start()
    }

    fun stop() {
        server?.shutdown()
    }

    fun blockUntilShutdown() {
        server?.awaitTermination()
    }

    fun registerRequest(id: String): OpenFuture<PublicKey> {
        val fut = openFuture<PublicKey>()
        futures[id] = fut
        return fut
    }

    fun registerSigRequest(id: String): OpenFuture<ByteArray> {
        val fut = openFuture<ByteArray>()
        sigFutures[id] = fut
        return fut
    }

    fun registerContainsKeyRequest(id: String): OpenFuture<Boolean> {
        val fut = openFuture<Boolean>()
        containsKeyFutures[id] = fut
        return fut
    }

    fun registerGetPublicKeyRequest(id: String): OpenFuture<PublicKey> {
        val fut = openFuture<PublicKey>()
        publicKeyFutures[id] = fut
        return fut
    }

    class SigningServerImpl(
            val futures: MutableMap<String, OpenFuture<PublicKey>>,
            val sigFutures: MutableMap<String, OpenFuture<ByteArray>>,
            val containsKeyFutures: MutableMap<String, OpenFuture<Boolean>>,
            val publicKeyFutures: MutableMap<String, OpenFuture<PublicKey>>) : AndroidSignerGrpc.AndroidSignerImplBase() {

        val log = contextLogger()

        override fun createdKeyPair(request: CreateKeyPairResponse, responseObserver: StreamObserver<Ack>) {
            val requestId = request.id
            val fut = futures[requestId]
            if (fut != null) {
                futures.remove(requestId)

                println("request.publicKey: ${request.publicKey}")

                // Decode the pub key.
                val factory = KeyFactory.getInstance("EC")
                val publicKey = factory.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(request.publicKey)))
                fut.set(publicKey)
            }
            responseObserver.onNext(Ack.getDefaultInstance())
            responseObserver.onCompleted()
        }

        override fun signed(request: SigningResponse, responseObserver: StreamObserver<Ack>) {
            val requestId = request.id
            val fut = sigFutures[requestId]
            if (fut != null) {
                sigFutures.remove(requestId)
                fut.set(request.signature.toByteArray())
            }
            responseObserver.onNext(Ack.getDefaultInstance())
            responseObserver.onCompleted()
        }

        override fun containsKey(request: ContainsKeyResponse, responseObserver: StreamObserver<Ack>) {
            val requestId = request.sessionId
            val fut = containsKeyFutures[requestId]
            if (fut != null) {
                containsKeyFutures.remove(requestId)
                fut.set(request.containsKey)
            }
            responseObserver.onNext(Ack.getDefaultInstance())
            responseObserver.onCompleted()
        }

        override fun publicKey(request: GetPublicKeyResponse, responseObserver: StreamObserver<Ack>) {
            val requestId = request.sessionId
            log.info("requestId: $requestId")
            val fut = publicKeyFutures[requestId]
            log.info("fut: $fut")
            if (fut != null) {
                publicKeyFutures.remove(requestId)
                log.info("removed")
                // TODO: handle empty byte string, when pub key is not found.
                val factory = KeyFactory.getInstance("EC")
                log.info("got instance")
                log.info("got data ${request.publicKey.toByteArray().size}")
                val keySpec =  X509EncodedKeySpec(request.publicKey.toByteArray())
                log.info("got spec $keySpec")
                val publicKey = factory.generatePublic(keySpec)
                log.info("got pubkey")
                fut.set(publicKey)
            }
            responseObserver.onNext(Ack.getDefaultInstance())
            responseObserver.onCompleted()
        }
    }
}