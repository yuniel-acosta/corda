package com.r3.corda.sgx.host.connector

import com.google.protobuf.ByteString
import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.LeafSender
import com.r3.sgx.core.host.EnclaveHandle
import com.r3.sgx.enclavelethost.grpc.ClientMessage
import com.r3.sgx.enclavelethost.grpc.EnclaveletHostGrpc
import com.r3.sgx.enclavelethost.grpc.ServerMessage
import io.grpc.ManagedChannel
import io.grpc.stub.StreamObserver
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

class GrpcEnclaveHandle<CONNECTION>(
        channel: ManagedChannel,
        private val handler: Handler<CONNECTION>
): EnclaveHandle<CONNECTION> {

    val rpcProxy:  EnclaveletHostGrpc.EnclaveletHostStub
    private lateinit var stream: StreamObserver<ClientMessage>

    init {
        rpcProxy = createRpcProxy(channel)
    }

    companion object {

        private fun createRpcProxy(channel: ManagedChannel): EnclaveletHostGrpc.EnclaveletHostStub {
            return EnclaveletHostGrpc.newStub(channel)
                    .withCompression("gzip")
                    .withWaitForReady()
        }

    }

    override val connection: CONNECTION by lazy {
        val connection = CompletableFuture<CONNECTION>()
        val localStream = rpcProxy.openSession(
                object : StreamObserver<ServerMessage> {
                    override fun onNext(value: ServerMessage) {
                        handler.onReceive(connection.get(), value.blob.asReadOnlyByteBuffer())
                    }

                    override fun onCompleted() {
                    }

                    override fun onError(t: Throwable) {
                        throw t
                    }
                })
        connection.complete(handler.connect(Sender(localStream)))
        stream = localStream
        connection.get()
    }

    override fun destroy() {
        stream.onCompleted()
    }

    class Sender(val stream: StreamObserver<ClientMessage>): LeafSender()  {
        override fun sendSerialized(serializedBuffer: ByteBuffer) {
            stream.onNext(ClientMessage.newBuilder()
                    .setBlob(ByteString.copyFrom(serializedBuffer))
                    .build())
        }
    }
}