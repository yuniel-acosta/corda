/*
package com.r3.corda.sgx.host.internal

import com.r3.corda.sgx.common.ServiceOpcode
import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.MuxingHandler
import com.r3.sgx.core.common.Sender
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.WireTransaction
import java.nio.ByteBuffer
import java.security.PublicKey
import java.util.function.Consumer

class TrustedNodeServicesHostHandler(private val services: ServiceHub): Handler<MuxingHandler.Connection> {
    private val muxing = MuxingHandler()

    override fun connect(upstream: Sender): MuxingHandler.Connection {
        val connection = muxing.connect(upstream).apply{
            addDownstream(ServiceOpcode.RESOLVE_IDENTITY.id, makeServiceHostHandler { pk: PublicKey ->
                services.identityService.partyFromKey(pk)!!
            })
            addDownstream(ServiceOpcode.RESOLVE_ATTACHMENT.id, makeServiceHostHandler { id: AttachmentId ->
                services.attachments.openAttachment(id)!!
            })
            addDownstream(ServiceOpcode.RESOLVE_STATE_REF.id, makeServiceHostHandler { ref: StateRef ->
                WireTransaction.resolveStateRefBinaryComponent(ref, services)!!
            })
            addDownstream(ServiceOpcode.RESOLVE_PARAMETERS.id, makeServiceHostHandler { hash: SecureHash ->
                services.networkParametersService.lookup(hash)!!
            })
            addDownstream(ServiceOpcode.RESOLVE_CONTRACT_ATTACHMENT.id, makeServiceHostHandler { ref: StateRef ->
                services.loadContractAttachment(ref)
            })
        }
        return connection
    }

    override fun onReceive(connection: MuxingHandler.Connection, input: ByteBuffer) {
        muxing.onReceive(connection, input)
    }

    private inner class ServiceHostHandler<U : Any, V : Any>(
            private val impl: (U) -> V,
            private val inputClazz: Class<U>): Handler<ServiceHostHandler<*, *>> {

        private lateinit var upstream: Sender

        override fun connect(upstream_: Sender): ServiceHostHandler<U, V> {
            upstream = upstream_
            return this
        }

        override fun onReceive(connection: ServiceHostHandler<*, *>, input: ByteBuffer) {
            require(connection == this) { "Internal error" }
            val deserialized = ByteArray(input.remaining()).also { input.get(it) }.deserialize<Any>()
            val response = impl(uncheckedCast(deserialized)).serialize().bytes
            upstream.send(response.size, Consumer { it.put(response) })
        }
    }

    private inline fun <reified U: Any, V: Any> makeServiceHostHandler(noinline f: (U) -> V) = ServiceHostHandler(f, U::class.java)
}
*/
