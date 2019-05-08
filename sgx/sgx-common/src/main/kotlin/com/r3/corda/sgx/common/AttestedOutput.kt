package com.r3.corda.sgx.common

import net.corda.core.crypto.EnclaveIdentity
import net.corda.core.crypto.EnclaveSignature
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import java.security.SignatureException

@CordaSerializable
class AttestedOutput<T: Any>(val raw: SerializedBytes<T>, val sig: EnclaveSignature) {
    fun validate(id: EnclaveIdentity): T {
        if (!sig.isValid(id, raw.bytes)) {
            throw SignatureException("Invalid enclave signature")
        }
        return uncheckedCast(raw.deserialize<Any>())
    }
}