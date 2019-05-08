package com.r3.corda.sgx.host.connector

import com.google.protobuf.ByteString
import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.LeafSender
import com.r3.sgx.enclavelethost.grpc.ClientMessage
import com.r3.sgx.enclavelethost.grpc.EnclaveletHostGrpc
import com.r3.sgx.enclavelethost.grpc.ServerMessage
import io.grpc.stub.StreamObserver
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

class GrpcHandlerConnector(private val rpcProxy: EnclaveletHostGrpc.EnclaveletHostStub): HandlerConnector {

    private var stream: StreamObserver<ClientMessage>? = null

    @Synchronized
    override fun <CONNECTION> setDownstream(downstream: Handler<CONNECTION>): CONNECTION {
        require(stream == null) { "Handler already connected" }
        val connection = CompletableFuture<CONNECTION>()
        val localStream = rpcProxy.openSession(
                object : StreamObserver<ServerMessage> {
                    override fun onNext(value: ServerMessage) {
                        downstream.onReceive(connection.get(), value.blob.asReadOnlyByteBuffer())
                    }

                    override fun onCompleted() {
                    }

                    override fun onError(t: Throwable) {
                        throw t
                    }
                })
        connection.complete(downstream.connect(Sender(localStream)))
        stream = localStream
        return connection.get()
    }

    override fun close() {
        stream?.onCompleted() ?: throw IllegalStateException("Non connected handler")
        stream = null
    }

    class Sender(val stream: StreamObserver<ClientMessage>): LeafSender()  {
        override fun sendSerialized(serializedBuffer: ByteBuffer) {
            stream.onNext(ClientMessage.newBuilder()
                    .setBlob(ByteString.copyFrom(serializedBuffer))
                    .build())
        }
    }
}