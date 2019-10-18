package com.r3.corda.sgx.host

import com.r3.corda.sgx.common.EnclaveInitResponse
import com.r3.corda.sgx.common.EnclaveInput
import com.r3.corda.sgx.common.EnclaveOutput
import com.r3.corda.sgx.enclave.*
import com.r3.sgx.core.common.*
import com.r3.sgx.testing.MockEcallSender
import com.r3.sgx.core.enclave.Enclave
import com.r3.sgx.core.host.EnclaveHandle
import com.r3.sgx.core.host.EnclaveletHostHandler
import com.r3.sgx.core.host.EpidAttestationHostConfiguration
import com.r3.sgx.testing.BytesRecordingHandler
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.serialize
import net.corda.core.utilities.sequence
import java.nio.ByteBuffer

class TxValidatingOracleProxy(val services: ServiceHub) {

    private val enclaveHandle: EnclaveHandle<*>
    val channel: Channel<BytesHandler.Connection>
    val enclaveOutput: BytesRecordingHandler
    val enclavePublicId: EnclaveInitResponse

    init {
        val attestationConfiguration = EpidAttestationHostConfiguration(
                quoteType = SgxQuoteType32.LINKABLE,
                spid = Cursor.allocate(SgxSpid)
        )
        val handler = EnclaveletHostHandler(attestationConfiguration)
        enclaveHandle = createMockEnclaveWithHandler(
                handler,
                TransactionVerifyingEnclavelet::class.java)
        val connection = enclaveHandle.connection
        enclaveOutput = BytesRecordingHandler()
        channel = connection.channels.addDownstream(enclaveOutput).get()
        enclavePublicId = getEnclaveId()
    }

    fun <CONNECTION> createMockEnclaveWithHandler(
            handler: Handler<CONNECTION>,
            enclaveClass: Class<out Enclave>
    ): EnclaveHandle<CONNECTION> {
        val enclave = enclaveClass.newInstance()
        return MockEcallSender(handler, enclave)
    }

    @Synchronized
    fun getEnclaveId(): EnclaveInitResponse {
        val output = invoke(EnclaveInput.Init(
                ledgerRootIdentity = services.identityService.trustRoot
        )) as EnclaveOutput.SignedInitResponse

        // Cannot validate it without remote attestation
        return output.signedContent
    }

    @Synchronized
    fun invoke(input: EnclaveInput): EnclaveOutput {
        val serialized = input.serialize()
        channel.connection.send(ByteBuffer.wrap(serialized.bytes))
        val response = enclaveOutput.nextCall
        val inputBytes = ByteArray(response.remaining()).also {
            response.get(it)
        }
        return uncheckedCast(SerializationFactory.defaultFactory.deserialize(
                inputBytes.sequence(),
                EnclaveOutput::class.java,
                SerializationFactory.defaultFactory.defaultContext))
    }
}