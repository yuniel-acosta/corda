package com.r3.corda.sgx.common.transactions


import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.node.NetworkParameters
import net.corda.core.node.ServicesForResolution
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.transactions.WireTransaction
import java.security.PublicKey

@CordaSerializable
data class TransactionResolutionCache(
        val partyByPublicKey: Map<PublicKey, Party>,
        val attachmentsByHash: Map<SecureHash, Attachment>,
        val stateRefToState: Map<StateRef, SerializedBytes<TransactionState<ContractState>>>,
        val networkParametersByHash: Map<SecureHash, NetworkParameters>,
        val stateRefAttachments: Map<StateRef, Attachment>) {

    companion object {
        fun fromWireTx(tx: WireTransaction, services: ServicesForResolution): TransactionResolutionCache {
            val partyByPublicKey = tx.commands.flatMap { it.signers }.map {
                it to services.identityService.partyFromKey(it)!!
            }.toMap()
            val attachmentsByHash = tx.attachments.map { it to services.attachments.openAttachment(it)!! }.toMap()
            val stateRefToState = (tx.inputs + tx.references).map { it to WireTransaction.resolveStateRefBinaryComponent(it, services)!! }.toMap()
            val netParamHash = tx.networkParametersHash!!
            val networkParametersByHash = mapOf(
                    netParamHash to services.networkParametersService.lookup(netParamHash)!!)
            return TransactionResolutionCache(
                    partyByPublicKey = partyByPublicKey,
                    attachmentsByHash = attachmentsByHash,
                    stateRefToState = stateRefToState,
                    networkParametersByHash = networkParametersByHash,
                    stateRefAttachments = emptyMap()
            )
        }
    }
}