package com.r3.corda.sgx.common.internal

import com.r3.corda.sgx.common.TrustedNodeServices
import com.r3.corda.sgx.common.transactions.TransactionResolutionCache
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import java.security.PublicKey

class CachedNodeServicesImpl(
        private val cache: TransactionResolutionCache,
        private val services: TrustedNodeServices): TrustedNodeServices {

    override val resolveIdentity = { pk: PublicKey ->
        cache.partyByPublicKey[pk] ?: services.resolveIdentity(pk) }

    override val resolveAttachment = { hash: SecureHash ->
        cache.attachmentsByHash[hash] ?: services.resolveAttachment(hash) }

    override val resolveContractAttachment = { stateRef: StateRef ->
        cache.stateRefAttachments[stateRef] ?: services.resolveContractAttachment(stateRef) }

    override val resolveParameters = { hash: SecureHash ->
        cache.networkParametersByHash[hash] ?: services.resolveParameters(hash) }

    override val resolveStateRef = { stateRef: StateRef ->
        cache.stateRefToState[stateRef] ?: services.resolveStateRef(stateRef) }
}