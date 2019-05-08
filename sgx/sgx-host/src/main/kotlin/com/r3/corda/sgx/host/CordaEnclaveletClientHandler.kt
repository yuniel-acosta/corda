package com.r3.corda.sgx.host

import com.r3.corda.sgx.common.OcallRecordingHandler
import com.r3.corda.sgx.common.TrustedNodeServices
import com.r3.corda.sgx.host.internal.TrustedNodeServicesHandler
import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.MuxingHandler
import com.r3.sgx.core.common.Sender
import java.nio.ByteBuffer

class CordaEnclaveletClientHandler(
        private val resolutionServices: TrustedNodeServices
): Handler<CordaEnclaveletClientHandler.Connection> {

    private val muxingHandler = MuxingHandler()

    override fun connect(upstream: Sender): Connection {
        val muxHandlerConnection = muxingHandler.connect(upstream)
        muxHandlerConnection.addDownstream(0, TrustedNodeServicesHandler (resolutionServices))
        val upstream = muxHandlerConnection.addDownstream(1, OcallRecordingHandler())
        return Connection(muxHandlerConnection, upstream)
    }

    override fun onReceive(connection: Connection, input: ByteBuffer) {
        muxingHandler.onReceive(connection.maxHandlerConnection, input)
    }

    data class Connection(
            val maxHandlerConnection: MuxingHandler.Connection,
            private val upstream: OcallRecordingHandler.Connection) {

        fun sendAndReceive(msg: ByteBuffer): ByteBuffer {
            upstream.send(msg)
            return upstream.get()
        }
    }

}