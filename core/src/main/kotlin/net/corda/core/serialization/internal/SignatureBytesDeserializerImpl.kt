package net.corda.core.serialization.internal

import net.corda.core.crypto.CompositeSignaturesWithKeys
import net.corda.core.serialization.deserialize
import net.corda.crypto.internal.SignatureBytesDeserializer

class SignatureBytesDeserializerImpl : SignatureBytesDeserializer {
    companion object {
        fun registerInstance() {
            SignatureBytesDeserializer.initialize(SignatureBytesDeserializerImpl())
        }
    }

    override fun deserialize(sigBytes: ByteArray): CompositeSignaturesWithKeys {
        return sigBytes.deserialize()
    }
}