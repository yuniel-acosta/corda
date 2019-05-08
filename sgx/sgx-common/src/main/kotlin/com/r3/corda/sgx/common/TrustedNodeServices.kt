package com.r3.corda.sgx.common

import com.r3.sgx.core.common.MuxId
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import java.security.PublicKey

// Expose subset of node services to enclave
@CordaSerializable
interface TrustedNodeServices {
    val resolveIdentity: (PublicKey) -> Party
    val resolveAttachment: (SecureHash) -> Attachment
    val resolveStateRef: (StateRef) -> SerializedBytes<TransactionState<ContractState>>
    val resolveParameters: (SecureHash) -> NetworkParameters
    val resolveContractAttachment: (StateRef) -> Attachment
}

enum class ServiceOpcode(val id: MuxId) {
    RESOLVE_IDENTITY (1),
    RESOLVE_ATTACHMENT( 2),
    RESOLVE_STATE_REF( 3),
    RESOLVE_PARAMETERS(4),
    RESOLVE_CONTRACT_ATTACHMENT( 5)
}
