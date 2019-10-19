package com.r3.corda.sgx.common

import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.OpaqueBytes
import java.security.PublicKey
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

@CordaSerializable
data class EnclaveInitResponse(
        val trustRoot: ByteArray,
        val publicKeys: List<Pair<SignatureType, PublicKey>>,
        val sealingPayload: OpaqueBytes? = null
)