package com.r3.corda.sgx.enclave.internal

import com.r3.corda.sgx.common.*
import com.r3.corda.sgx.enclave.transactions.UploadableAttachment
import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.MuxId
import com.r3.sgx.core.common.MuxingHandler
import com.r3.sgx.core.common.Sender
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.serialize
import java.nio.ByteBuffer
import java.security.PublicKey
import java.util.HashMap

class TrustedNodeServicesImpl(private val serializationEnvironment: SerializationEnvironment,
                              private val attachmentIdValidator: List<AttachmentIdValidator>)
    : Handler<TrustedNodeServicesImpl.Connection> {

    private val muxing = MuxingHandler()

    override fun connect(upstream: Sender): Connection {
        return Connection(muxing.connect(upstream))
    }

    override fun onReceive(connection: Connection, input: ByteBuffer) {
        muxing.onReceive(connection.upstream, input)
    }

    inner class Connection(val upstream: MuxingHandler.Connection): TrustedNodeServices {
        private val recordingHandlers = HashMap<MuxId, OcallRecordingHandler.Connection>()

        private val attachmentResolutionCheck = { input: SecureHash, resolved: UploadableAttachment ->
            attachmentIdValidator.all { it.isTrusted(resolved.id) } && resolved.id == input
        }

        private val checkNetParamResolution = { input: SecureHash, resolved: NetworkParameters ->
            serializationEnvironment.asContextEnv { input.serialize() }.hash == input
        }

        init {
            ServiceOpcode.values().forEach { opcode ->
                val handlerForId = upstream.addDownstream(opcode.id, OcallRecordingHandler())
                recordingHandlers.put(opcode.id, handlerForId)
            }
        }

        override val resolveAttachment = buildProxy(
                id = ServiceOpcode.RESOLVE_ATTACHMENT,
                resolutionCheck = attachmentResolutionCheck)

        override val resolveIdentity = buildProxy<PublicKey, Party>(ServiceOpcode.RESOLVE_IDENTITY) { id, out -> true }

        // TODO: Add resolution trust check
        override val resolveStateRef = buildProxy<StateRef, SerializedBytes<TransactionState<ContractState>>>(ServiceOpcode.RESOLVE_STATE_REF) { id, out -> true }

        // This does not seem to be used in tx resolution !!
        override val resolveContractAttachment = buildProxy<StateRef, UploadableAttachment>(
                id = ServiceOpcode.RESOLVE_CONTRACT_ATTACHMENT,
                resolutionCheck = { input, output -> true})

        override val resolveParameters = buildProxy(
                id = ServiceOpcode.RESOLVE_PARAMETERS,
                resolutionCheck = checkNetParamResolution)

        private inline fun <reified U : Any, reified V : Any> buildProxy(
                id: ServiceOpcode,
                crossinline resolutionCheck: (U, V) -> Boolean): (U) -> V {
            return { input: U ->
                val serialized = serializationEnvironment.asContextEnv { input.serialize().bytes }
                val outputHandler = recordingHandlers[id.id]!!
                outputHandler.send(ByteBuffer.wrap(serialized))
                val result = outputHandler.get().deserialize(serializationEnvironment, V::class.java)
                if (!resolutionCheck(input, result)) {
                    throw RuntimeException("Resolution output not trusted")
                }
                result
            }
        }
    }
}