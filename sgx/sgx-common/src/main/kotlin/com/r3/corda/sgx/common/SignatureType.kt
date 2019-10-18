package com.r3.corda.sgx.common

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class SignatureType {
    TRANSACTION_VERIFIED,
    TRANSACTION_NOTARIZED
}
