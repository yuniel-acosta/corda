package com.r3.corda.sgx.enclave

import com.r3.corda.sgx.enclave.internal.asContextEnv
import com.r3.corda.sgx.enclave.internal.deserialize
import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.Sender
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.serialize
import java.nio.ByteBuffer
import java.util.function.Consumer

class AMQPHandler<U: Any, T: Any>(
        val serializationEnvironment: SerializationEnvironment,
        val downstream: (U) -> T) : Handler<AMQPHandler<U, T>.AMQPSender> {

    override fun connect(upstream: Sender): AMQPSender {
        return AMQPSender(upstream)
    }

    override fun onReceive(connection: AMQPSender, input: ByteBuffer) {
        val input: U = uncheckedCast(input.deserialize<Any>(serializationEnvironment))
        val output = serializationEnvironment.asContextEnv { downstream(input) }
        connection.send(output)
    }

    inner class AMQPSender(val upstream: Sender) {
        fun send(value: T) {
            val serialized = serializationEnvironment.asContextEnv {
                value.serialize()
            }.bytes
            upstream.send(serialized.size, Consumer { buffer -> buffer.put(serialized) })
        }
    }
}