package com.r3.corda.sgx.common

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.transactions.ComponentGroup
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import java.security.PublicKey
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

@CordaSerializable
data class TransactionSigningRequest(
        val txId: SecureHash,
        val signatureType: SignatureType
)

@CordaSerializable
data class TransactionResolutionPayload(

        val tx: SignedTransaction,

        val attachments: List<Attachment>,

        val inputStates: List<SignedData<Pair<SecureHash, ComponentGroup>>>,

        val netparam: SignedDataWithCert<NetworkParameters>,

        val attestedEnclaveIds: List<EnclaveAttestedIdentity>
)

sealed class EnclaveInput {

    @CordaSerializable
    data class Init(
            val ledgerRootIdentity: X509Certificate
    ): EnclaveInput()

    @CordaSerializable
    data class InputMessage(
        val request: TransactionSigningRequest,
        val payload: TransactionResolutionPayload
    ): EnclaveInput()

}

sealed class EnclaveOutput {

    // Return a signature in both cases
    @CordaSerializable
    data class TransactionVerified (
            val txSignature: OpaqueBytes
    ): EnclaveOutput()

    @CordaSerializable
    data class TransactionNotarized (
            val outputGroupSig: OpaqueBytes
    ): EnclaveOutput()

    @CordaSerializable
    data class SignedInitResponse(
            val signedContent: EnclaveInitResponse,
            val signature: OpaqueBytes,
            val encodedKey: OpaqueBytes
    ): EnclaveOutput()
}