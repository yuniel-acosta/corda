package net.corda.nodeapi.internal.cryptoservice.external

import com.google.protobuf.ByteString
import io.grpc.stub.StreamObserver
import net.corda.core.crypto.Crypto
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.proto.ExternalCryptoServiceGrpc
import net.corda.nodeapi.internal.cryptoservice.proto.KeyRequest
import net.corda.nodeapi.internal.cryptoservice.proto.KeyResponse
import net.corda.nodeapi.internal.cryptoservice.proto.SignRequest
import net.corda.nodeapi.internal.cryptoservice.proto.SignResponse
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

class ExternalOverGprcCryptoServiceServer {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val port = 5001
        }
    }
}

class ExternalOverGrpcCryptoService : ExternalCryptoServiceGrpc.ExternalCryptoServiceImplBase() {

    companion object {
        val PLACEHOLDER_UUID = UUID.fromString("oooooooh, this is totally a placeholder")
        val lock = ReentrantReadWriteLock()
    }

    private val uuidToCryptoServiceMap: MutableMap<UUID, CryptoService> = HashMap()
    private val aliasToCryptoServiceMap: MutableMap<String, CryptoService> = HashMap()

    override fun generateKeyPair(request: KeyRequest, responseObserver: StreamObserver<KeyResponse>) {
        val uuidForRequest = request.uuid?.let { UUID.fromString(it) } ?: PLACEHOLDER_UUID
        val scheme = request.scheme?.fromProto() ?: Crypto.DEFAULT_SIGNATURE_SCHEME
        val cryptoServiceForUUID = getCryptoServiceForUUID(uuidForRequest, request.name)
        val publicKey = cryptoServiceForUUID.generateKeyPair(request.name, scheme)
        responseObserver.onNext(KeyResponse.newBuilder().setKey(ByteString.copyFrom(publicKey.encoded)).setAlgorithm(scheme.algorithmName).build())
        responseObserver.onCompleted()
    }

    private fun getCryptoServiceForUUID(uuidForRequest: UUID, alias: String): CryptoService {
        return lock.write {
            val inMemoryCryptoService = InMemoryCryptoService(emptyMap())
            var populatedUUIDMap = false
            uuidToCryptoServiceMap.computeIfAbsent(uuidForRequest) { _ ->
                populatedUUIDMap = true
                inMemoryCryptoService
            }

            if (populatedUUIDMap && aliasToCryptoServiceMap.containsKey(alias)) {
                throw IllegalStateException()
            }

            aliasToCryptoServiceMap.computeIfAbsent(alias) { _ ->
                inMemoryCryptoService
            }
        }
    }

    override fun sign(request: SignRequest?, responseObserver: StreamObserver<SignResponse>?) {
        super.sign(request, responseObserver)
    }

    override fun getKey(request: KeyRequest?, responseObserver: StreamObserver<KeyResponse>?) {
        super.getKey(request, responseObserver)
    }
}