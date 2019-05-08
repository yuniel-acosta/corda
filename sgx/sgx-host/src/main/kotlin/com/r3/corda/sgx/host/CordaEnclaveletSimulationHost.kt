package com.r3.corda.sgx.host

import com.r3.corda.sgx.common.AttestedOutput
import com.r3.corda.sgx.common.OcallRecordingHandler
import com.r3.corda.sgx.host.internal.TrustedNodeServicesHostHandler
import com.r3.sgx.core.common.*
import com.r3.sgx.core.host.*
import net.corda.core.crypto.EnclaveIdentity
import net.corda.core.crypto.EnclaveSignature
import net.corda.core.crypto.SignedData
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.serialize
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.sequence
import java.io.File
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.util.function.Function

class CordaEnclaveletSimulationHost<U : Any, V : Any>(
        serviceHub: ServiceHub,
        enclaveFile: File,
        epidConfiguration: EpidAttestationHostConfiguration,
        mode: EnclaveLoadMode)
    : Function<U, SignedData<V>> {

    val enclaveIdentity: EnclaveIdentity
        get() = EnclaveIdentity(measurement, EnclaveIdentity.EnclaveMode.SIMULATION)

    private val enclavelthostHandler = EnclaveletHostHandler(epidConfiguration, true)
    private val handle = NativeHostApi(mode).createEnclave(enclavelthostHandler, enclaveFile)
    private val connection = handle.connection

    private val mux: MuxingHandler.Connection
    private val outputSink: OcallRecordingHandler.Connection
    private val quote: Cursor<ByteBuffer, SgxSignedQuote>
    private val measurement: OpaqueBytes
    private val reportData: OpaqueBytes

    init {
        mux = connection.channels.addDownstream(MuxingHandler()).get().connection
        mux.addDownstream(0, TrustedNodeServicesHostHandler(serviceHub))
        outputSink = mux.addDownstream(1, OcallRecordingHandler())
        quote = connection.attestation.getQuote()
        val quoteSize = quote.getBuffer().remaining()
        val signedQuote = SgxSignedQuote(quoteSize)
        val reportBody = quote[signedQuote.quote][SgxQuote.reportBody]
        measurement = readBytes(reportBody[SgxReportBody.measurement])
        if (measurement.bytes.size != SgxMeasurement.size()) {
            throw IllegalStateException("Invalid measurement size")
        }
        reportData = readBytes(reportBody[SgxReportBody.reportData])
    }

    override fun apply(input: U): SignedData<V> {
        val serialized = input.serialize()
        outputSink.send(ByteBuffer.wrap(serialized.bytes))
        val response = outputSink.get()
        val inputBytes = ByteArray(response.remaining()).also {
            response.get(it)
        }
        return uncheckedCast(SerializationFactory.defaultFactory.deserialize(
                    inputBytes.sequence(),
                    SignedData::class.java,
                    SerializationFactory.defaultFactory.defaultContext))
    }

    private inline fun <reified T: Encoder<*>> readBytes(cursor: Cursor<*, T>): OpaqueBytes {
        val data = ByteArray(cursor.encoder.size()) { 0 }
        cursor.getBuffer().get(data)
        return OpaqueBytes(data)
    }
}