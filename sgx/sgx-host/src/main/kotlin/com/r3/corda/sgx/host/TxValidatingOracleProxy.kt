package com.r3.corda.sgx.host

import com.r3.corda.sgx.common.EnclaveInput
import com.r3.corda.sgx.common.EnclaveOutput
import com.r3.corda.sgx.enclave.*
import com.r3.corda.sgx.host.connector.GrpcEnclaveHandle
import com.r3.sgx.core.common.*
import com.r3.sgx.testing.MockEcallSender
import com.r3.sgx.core.enclave.Enclave
import com.r3.sgx.core.host.*
import com.r3.sgx.enclavelethost.grpc.EpidAttestation
import com.r3.sgx.testing.BlockingBytesRecordingHandler
import io.grpc.ManagedChannelBuilder
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.serialize
import net.corda.core.utilities.sequence
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer

enum class EnclaveServiceMode {
    MOCK,
    LOCAL,
    REMOTE
}

sealed class TxValidatingOracleProxy private constructor(
        val services: ServiceHub,
        val serviceMode: EnclaveServiceMode
) {

    protected val attestation: EpidAttestation? get() = null
    abstract fun connect(): TxValidatingOracleProxy.Connection

    class Mock(services: ServiceHub): TxValidatingOracleProxy(services, EnclaveServiceMode.MOCK) {

        val handler = EnclaveletHostHandler(attestationConfiguration)
        val enclaveHandle = createMockEnclaveWithHandler(
                handler,
                TransactionVerifyingEnclavelet::class.java)

        override fun connect(): Connection {
            val enclaveOutput = BlockingBytesRecordingHandler()
            val channel = enclaveHandle.connection.channels.addDownstream(enclaveOutput).get()!!
            return Connection(HandlerConnected(enclaveOutput, channel.connection), enclaveHandle)
        }
    }

    class Simulated(services: ServiceHub, enclaveFile: File): TxValidatingOracleProxy(services, EnclaveServiceMode.LOCAL) {

        val handler = EnclaveletHostHandler(attestationConfiguration)
        val enclaveHandle = createLocalEnclaveWithHandler(handler, enclaveFile)

        override fun connect(): Connection {
            val enclaveOutput = BlockingBytesRecordingHandler()
            val connection = enclaveHandle.connection.channels.addDownstream(enclaveOutput).get()!!
            return Connection(HandlerConnected(enclaveOutput, connection.connection), enclaveHandle)
        }
    }

    class Remote(services: ServiceHub, target: String): TxValidatingOracleProxy(services, EnclaveServiceMode.REMOTE) {

        val channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build()

        override fun connect(): Connection {
            val enclaveOutput = BlockingBytesRecordingHandler()
            val enclaveHandle = GrpcEnclaveHandle(channel, enclaveOutput)
            return Connection(HandlerConnected(enclaveOutput, enclaveHandle.connection), enclaveHandle)
        }
    }


    companion object {

        fun <CONNECTION> createMockEnclaveWithHandler(
                handler: Handler<CONNECTION>,
                enclaveClass: Class<out Enclave>
        ): EnclaveHandle<CONNECTION> {
            val enclave = enclaveClass.newInstance()
            return object: EnclaveHandle<CONNECTION> by MockEcallSender(handler, enclave) {
                override fun destroy() {
                    // NOOP
                }
            }
        }

        fun <CONNECTION> createLocalEnclaveWithHandler(
                handler: Handler<CONNECTION>,
                enclaveFile: File
        ): EnclaveHandle<CONNECTION> {
            val hostApi = NativeHostApi(EnclaveLoadMode.SIMULATION)
            return object: EnclaveHandle<CONNECTION> by hostApi.createEnclave(handler, enclaveFile)  {
                override fun destroy() {
                    // NOOP
                }
            }
        }

        private val attestationConfiguration = EpidAttestationHostConfiguration(
                quoteType = SgxQuoteType32.LINKABLE,
                spid = Cursor.allocate(SgxSpid)
        )

    }

    class Connection(
            val handlerConnected: HandlerConnected<BytesHandler.Connection>,
            val enclaveHandle: EnclaveHandle<*>
    ): Closeable {

        @Synchronized
        fun invoke(input: EnclaveInput): EnclaveOutput {
            val serialized = input.serialize()
            handlerConnected.connection.send(ByteBuffer.wrap(serialized.bytes))
            val response = (handlerConnected.handler as BlockingBytesRecordingHandler)
                    .received
                    .take()
            return uncheckedCast(SerializationFactory.defaultFactory.deserialize(
                    response.sequence(),
                    EnclaveOutput::class.java,
                    SerializationFactory.defaultFactory.defaultContext))
        }

        override fun close() {
            enclaveHandle.destroy()
        }
    }
}
