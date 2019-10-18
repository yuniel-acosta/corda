package com.r3.corda.sgx.enclave

import com.r3.corda.sgx.common.*
import com.r3.corda.sgx.enclave.internal.asContextEnv
import com.r3.corda.sgx.enclave.transactions.TransactionResolutionData
import com.r3.corda.sgx.enclave.transactions.outputGroup
import com.r3.corda.sgx.enclave.transactions.toLedgerTransactionSgx
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.OpaqueBytes
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

/**
 * Transaction validity oracle:
 *
 * - LedgerTransaction only type of transaction supported
 */
class TransactionVerifyingEnclavelet: AMQPEnclavelet<EnclaveInput, EnclaveOutput>() {

    inner class ConnectionImpl: Connection<EnclaveInput, EnclaveOutput> {
        override fun process(input: EnclaveInput): EnclaveOutput {
            return when(input) {
                is EnclaveInput.Init -> {
                    getOrInitialize(input).publicId
                }
                is EnclaveInput.InputMessage -> {
                    val keys = (getOrInitialize() ?: throw IllegalStateException("Uninitialized enclave"))
                            .keys

                    // Verify transaction resolution payload
                    val checkedResolutionData = verifyTxResolutionPayload(
                            input.request.txId,
                            input.payload,
                            keys.getValue(SignatureType.TRANSACTION_NOTARIZED).public)

                    // Don't verify transaction if already signed by another transaction validity oracle
                    if (!checkSignatureOfValidity(input.payload, keys.values.map {it.public})) {
                        val resolvedTx = input.payload.tx.toLedgerTransactionSgx(checkedResolutionData)
                        resolvedTx.verify()
                    }

                    when (input.request.signatureType) {
                        SignatureType.TRANSACTION_VERIFIED -> EnclaveOutput.TransactionVerified(
                                sign(input.request.txId, keys.getValue(SignatureType.TRANSACTION_VERIFIED).private))

                        SignatureType.TRANSACTION_NOTARIZED -> {
                            // Check all required signatures
                            input.payload.tx.verifyRequiredSignatures()
                            EnclaveOutput.TransactionNotarized(
                                    sign(Pair(input.payload.tx.tx.id, input.payload.tx.tx.outputGroup()),
                                            keys.getValue(SignatureType.TRANSACTION_NOTARIZED).private))
                        }
                    }
                }
            }
        }
    }

    override fun connect(): Connection<EnclaveInput, EnclaveOutput> {
        return ConnectionImpl()
    }

    // Verify input tx resolution payload and extract transaction resolution data
    private fun verifyTxResolutionPayload(
            txId: SecureHash,
            payload: TransactionResolutionPayload,
            txNotarizedKey: PublicKey
    ): TransactionResolutionData {
        if (txId != payload.tx.id) {
            throw TransactionResolutionException(txId, "Payload hash mismatch")
        }

        val stateRefMap = payload.inputStates.flatMap {
            if (it.sig.by != txNotarizedKey) {
                throw TransactionResolutionException(txId, "Payload verification failure")
            }
            val (inputTxId, group) = it.verified()
            if (group.groupIndex != ComponentGroupEnum.OUTPUTS_GROUP.ordinal) {
                throw TransactionResolutionException(txId, "Invalid output group in resolution payload")
            }
            group.components.mapIndexed { id, data ->
                StateRef(inputTxId, id) to (data as SerializedBytes<TransactionState<ContractState>>)
            }
        }.toMap()

        val attachmentMap = payload.attachments.map {
            it.id to it
        }.toMap()

        // TODO: Add signature check!
        val netparam = amqpSerializationEnv.asContextEnv {
            payload.netparam.raw.deserialize()
        }

        val netparamMap = mapOf(payload.netparam.raw.hash to netparam)

        return TransactionResolutionData(
                emptyMap(),
                attachmentMap,
                stateRefMap,
                netparamMap,
                emptyMap()
        )
    }

    private fun checkSignatureOfValidity(
            input: TransactionResolutionPayload,
            ownKeys: Collection<PublicKey>
    ): Boolean {
        val attestedKeys = input.attestedEnclaveIds.flatMap {
            it.keys.map { it.second }
        }
        return input.tx.sigs.any {
            it.by in attestedKeys || it.by in ownKeys
        }
    }

    private var state_: InitState? = null
    private data class InitState(
            val publicId: EnclaveOutput.SignedInitResponse,
            val keys: Map<SignatureType, KeyPair>
    )

    @Synchronized
    private fun getOrInitialize(msg: EnclaveInput.Init? = null): InitState {
        if (state_ != null) {
            return state_!!
        }
        msg ?: throw IllegalArgumentException("Uninitialized enclave")

        val txVerifiedKey = signatureScheme.generateKeyPair()
        val txNotarizedKey = signatureScheme.generateKeyPair()
        val responseContent = EnclaveInitResponse(
                trustRoot = msg.ledgerRootIdentity,
                publicKeys = listOf(
                        Pair(SignatureType.TRANSACTION_VERIFIED, txVerifiedKey.public),
                        Pair(SignatureType.TRANSACTION_NOTARIZED, txNotarizedKey.public))
        )

        val responseContentSignature = signatureScheme.sign(
                attestedKeyPair.private,
                amqpSerializationEnv.asContextEnv {
                    responseContent.serialize()
                }.bytes)

        val response = EnclaveOutput.SignedInitResponse(
                signedContent = responseContent,
                signature = OpaqueBytes(responseContentSignature),
                encodedKey = OpaqueBytes(attestedKeyPair.private.encoded)
        )

        val state = InitState(
                publicId = response,
                keys = mapOf(
                        Pair(SignatureType.TRANSACTION_VERIFIED, txVerifiedKey),
                        Pair(SignatureType.TRANSACTION_NOTARIZED, txNotarizedKey))
        )
        state_ = state
        return state
    }

    private fun <T: Any> sign(value: T, key: PrivateKey): OpaqueBytes {
        val serialized = amqpSerializationEnv.asContextEnv {
            value.serialize()
        }
        return OpaqueBytes(signatureScheme.sign(key, serialized.bytes))
    }
 }