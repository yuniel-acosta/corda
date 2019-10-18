package com.r3.corda.sgx.host

import com.r3.corda.sgx.common.*
import com.r3.corda.sgx.enclave.transactions.outputGroup
import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.node.NetworkParameters
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.serialize
import net.corda.core.transactions.ComponentGroup
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction

//@CordaService
class TxVerifyingOracleClient(val services: ServiceHub): SingletonSerializeAsToken() {

    val enclaveId: EnclaveInitResponse
    val proxy: TxValidatingOracleProxy

    val notarizedTxKey get() =
        enclaveId.publicKeys.single { it.first == SignatureType.TRANSACTION_NOTARIZED }.second

    val verifiedTxKey get() =
        enclaveId.publicKeys.single { it.first == SignatureType.TRANSACTION_VERIFIED }.second

    init {
        proxy = TxValidatingOracleProxy(services)
        val initToken = EnclaveInput.Init(ledgerRootIdentity = services.identityService.trustRoot)
        val output = proxy.invoke(initToken) as EnclaveOutput.SignedInitResponse

        // Cannot validate it without remote attestation
        enclaveId = output.signedContent
    }

    fun getEnclaveSignature(tx: WireTransaction): DigitalSignature.WithKey {

        val outputMsg = getSignatureOverChain(
                SignedTransaction(tx, emptyList()),
                SignatureType.TRANSACTION_VERIFIED) as EnclaveOutput.TransactionVerified

        return DigitalSignature.WithKey(
                by = verifiedTxKey,
                bytes = outputMsg.txSignature.bytes
        )

    }

    fun getSignatureOverChain(txId: SecureHash, sigType: SignatureType): EnclaveOutput {
        val tx = services.validatedTransactions.getTransaction(txId)
                ?: throw TransactionResolutionException(txId)
        return getSignatureOverChain(tx, sigType)
    }

    // Limited to chains consisting exclusively of WireTransaction (very inefficient, no caching)
    fun getSignatureOverChain(tx: SignedTransaction, sigType: SignatureType): EnclaveOutput {
        val txId = tx.id
        val wireTx = tx.tx

        // Build transaction resolution payload
        val attachmentsData = wireTx.attachments.map {
            services.attachments.openAttachment(it)
                    ?: throw TransactionResolutionException(txId)
        }

        // output states of input transactions
        val inputStates = ArrayList<SignedData<Pair<SecureHash, ComponentGroup>>>()
        for (inputTxId in (tx.inputs + tx.references).map {it.txhash}.toSet()) {
            val inputTx = services.validatedTransactions.getTransaction(inputTxId)
                    ?: throw TransactionResolutionException(inputTxId)

            val outputStatesSig = (getSignatureOverChain(
                    inputTxId,
                    SignatureType.TRANSACTION_NOTARIZED) as EnclaveOutput.TransactionNotarized).outputGroupSig

            val signedOutputStates = SignedData(
                    raw = Pair(inputTxId, inputTx.tx.outputGroup()).serialize(),
                    sig = DigitalSignature.WithKey(by = notarizedTxKey, bytes = outputStatesSig.bytes)
            )

            inputStates.add(signedOutputStates)
        }

        val netparam = services.networkParametersService.lookup(wireTx.networkParametersHash!!)
                ?: throw TransactionResolutionException(txId)

        // Fake signature
        val signedNetParam = SignedDataWithCert<NetworkParameters>(
                raw = netparam.serialize(),
                sig = DigitalSignatureWithCert(by = services.identityService.trustRoot, bytes = ByteArray(0))
        )

        val txResolutionPayload = TransactionResolutionPayload(
                tx = tx,
                inputStates = inputStates,
                attachments = attachmentsData,
                netparam = signedNetParam,
                attestedEnclaveIds = emptyList() //< No other oracles for now
        )

        return proxy.invoke(EnclaveInput.InputMessage(
                request = TransactionSigningRequest(txId = txId, signatureType = sigType),
                payload = txResolutionPayload
        ))
    }
}