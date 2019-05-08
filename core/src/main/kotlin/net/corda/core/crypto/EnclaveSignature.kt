package net.corda.core.crypto

import com.r3.sgx.core.common.*
import com.r3.sgx.enclavelethost.client.EpidAttestationVerificationBuilder
import com.r3.sgx.enclavelethost.client.Measurement
import com.r3.sgx.enclavelethost.client.QuoteConstraint
import com.r3.sgx.enclavelethost.grpc.EpidAttestation
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.utilities.OpaqueBytes
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SignatureException

@CordaSerializable
data class EnclaveSignature(val signature: DigitalSignature.WithKey,
                            val attestationReport: AttestationReport) {
    companion object {
        fun decode(encoded: ByteArray): EnclaveSignature {
            return SerializedBytes<EnclaveSignature>(encoded).deserialize()
        }
    }

    fun isValid(by: EnclaveIdentity, clearData: ByteArray): Boolean {

        val quote = attestationReport.verify(by)

        // Verify hash of public key match report data
        val hashedResponseKey = MessageDigest
                .getInstance("SHA-512")
                .digest(signature.by.encoded)

        if (quote.getReportData() != OpaqueBytes(hashedResponseKey)) {
            throw IllegalArgumentException("Malformed quote")
        }

        // Verify attested signature
        return signature.isValid(clearData)
    }

    private fun Cursor<ByteBuffer, SgxQuote>.getReportData(): OpaqueBytes {
        val data = ByteArray(SgxReportData.size) { 0 }
        this[SgxQuote.reportBody][SgxReportBody.reportData].getBuffer().get(data)
        return OpaqueBytes(data)
    }

    @CordaSerializable
    sealed class AttestationReport {

        abstract fun verify(id: EnclaveIdentity): Cursor<ByteBuffer, SgxQuote>

        @CordaSerializable
        data class EpidAttestationReport(private val epidAttestation: Any /* EpidAttestation */)
            : AttestationReport() {

            override fun verify(id: EnclaveIdentity): Cursor<ByteBuffer, SgxQuote> {
                val acceptDebug = when (id.mode) {
                    EnclaveIdentity.EnclaveMode.SIMULATION -> throw SignatureException("Mismatching enclave mode")
                    EnclaveIdentity.EnclaveMode.DEBUG -> true
                    EnclaveIdentity.EnclaveMode.RELEASE -> false
                }
                val expectedMeasurement = Measurement(id.measurement.bytes)
                val quoteConstraints = listOf(QuoteConstraint.ValidMeasurements(expectedMeasurement))
                val attestationValidator = EpidAttestationVerificationBuilder(quoteConstraints)
                        .withAcceptDebug(acceptDebug)
                        .withAcceptConfigurationNeeded(true) //< for testing
                        .withAcceptGroupOutOfDate(true) //< for testing
                        .build()
                return attestationValidator.verify(
                        attestationValidator.loadIntelPkix(),
                        epidAttestation as EpidAttestation)
            }
        }

        @CordaSerializable
        class UnattestedSignedQuote(private val data_: Any /* Cursor<ByteBuffer, SgxQuote> */)
            : AttestationReport() {

            override fun verify(id: EnclaveIdentity): Cursor<ByteBuffer, SgxQuote> {
                require(id.mode == EnclaveIdentity.EnclaveMode.SIMULATION)
                val data = data_ as Cursor<ByteBuffer, SgxQuote>
                if (data.getMeasurement() != id.measurement) {
                    throw IllegalArgumentException("Mismatching measurement")
                }
                return data
            }
        }

        protected fun Cursor<ByteBuffer, SgxQuote>.getMeasurement(): OpaqueBytes {
            val data = ByteArray(SgxMeasurement.size) { 0 }
            this[SgxQuote.reportBody][SgxReportBody.measurement].getBuffer().get(data)
            return OpaqueBytes(data)
        }
    }

}