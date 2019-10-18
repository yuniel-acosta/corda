package com.r3.corda.sgx.common

import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.sgx.core.common.Cursor
import com.r3.sgx.core.common.SgxQuote
import com.r3.sgx.core.common.attestation.*
import com.r3.sgx.core.common.crypto.SignatureSchemeId
import com.r3.sgx.enclavelethost.client.EpidAttestationVerificationBuilder
import com.r3.sgx.enclavelethost.grpc.EpidAttestation
import net.corda.core.node.NetworkParameters
import net.corda.core.node.services.SgxServiceType
import net.corda.core.serialization.CordaSerializable
import com.r3.sgx.enclavelethost.ias.schemas.ReportResponse
import net.corda.core.serialization.serialize
import java.nio.ByteBuffer
import java.security.PublicKey
import java.security.cert.TrustAnchor

@CordaSerializable
class EnclaveAttestedIdentity(
        val attestationReportEncoded: ByteArray, //< protobuf serialization of attestation report
        val enclavePayload: EnclaveOutput.SignedInitResponse
) {
    private val epidAttestation: EpidAttestation
    private val uncheckedQuoteReader: SgxQuoteReader

    init {
        epidAttestation = EpidAttestation.parseFrom(attestationReportEncoded)!!
        val objectMapper = ObjectMapper()
        val response = objectMapper.readValue<ReportResponse>(epidAttestation.iasResponse.toByteArray(), ReportResponse::class.java)
        val quoteCursor = Cursor(SgxQuote, ByteBuffer.wrap(response.isvEnclaveQuoteBody))
        uncheckedQuoteReader = SgxQuoteReader(quoteCursor)
    }

    // Hard-coded, it is the only type of service
    val enclaveService = SgxServiceType.TRANSACTION_VALIDITY_ORACLE

    val mrenclave get() = Measurement(uncheckedQuoteReader.measurement.array())

    val keys: List<Pair<SignatureType, PublicKey>> = enclavePayload.signedContent.publicKeys

    fun verify(rootKey: TrustAnchor, netparams: NetworkParameters) {
        check(rootKey.equals(enclavePayload.signedContent.trustRoot))

        // TODO: extract trust anchor from network parameters
        val epidVerifier = EpidAttestationVerificationBuilder()
                .withAcceptConfigurationNeeded(false)
                .withAcceptDebug(false)
                .build()

        val attestedQuote = epidVerifier.verify(epidVerifier.loadIntelPkix(), epidAttestation)
        verifyEnclaveInitResponse(enclavePayload, attestedQuote)
    }

    companion object {

        fun verifyEnclaveInitResponse(
                enclavePayload: EnclaveOutput.SignedInitResponse,
                attestedQuote: AttestedOutput<Cursor<ByteBuffer, SgxQuote>>
        ): EnclaveInitResponse {
            val keyAuthenticator = PublicKeyAttester(attestedQuote)
            val enclaveSignatureVerifier = AttestedSignatureVerifier(
                    SignatureSchemeId.EDDSA_ED25519_SHA512,
                    keyAuthenticator)
            enclaveSignatureVerifier.verify(
                    enclaveSignatureVerifier.decodeAttestedKey(enclavePayload.encodedKey.bytes),
                    enclavePayload.signature.bytes,
                    enclavePayload.signedContent.serialize().bytes
            )
            return enclavePayload.signedContent
        }

    }
}
