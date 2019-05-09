package net.corda.core.serialization.internal

import net.corda.core.crypto.CompositeSignaturesWithKeys
import net.corda.core.serialization.deserialize
import net.corda.crypto.internal.SignatureBytesDeserializer

class SignatureBytesDeserializerImpl : SignatureBytesDeserializer {
    companion object {
        private val instance = createDeserializerInstance()

        private fun createDeserializerInstance(): SignatureBytesDeserializer {
            val thisInstance = SignatureBytesDeserializerImpl()
            SignatureBytesDeserializer.initialize(thisInstance)
            return thisInstance
        }
    }

    override fun deserialize(sigBytes: ByteArray): CompositeSignaturesWithKeys {
        return sigBytes.deserialize()
    }
}