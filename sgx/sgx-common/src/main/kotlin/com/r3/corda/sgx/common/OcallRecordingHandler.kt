package com.r3.corda.sgx.common

import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.Sender
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer


class OcallRecordingHandler : Handler<OcallRecordingHandler.Connection> {
    override fun connect(upstream: Sender): Connection {
        return Connection(upstream)
    }

    override fun onReceive(connection: Connection, input: ByteBuffer) {
        connection.received.add(input)
    }

    class Connection(val upstream: Sender) {
        var received = ArrayBlockingQueue<ByteBuffer>(2)
        fun send(bytes: ByteBuffer) {
            upstream.send(bytes.remaining(), Consumer { buffer ->
                buffer.put(bytes)
            })
        }

        fun get(): ByteBuffer {
            return received.take()
        }
    }
}
