package net.corda.crypto.internal

import net.corda.core.crypto.CompositeSignaturesWithKeys

interface SignatureBytesDeserializer {
    companion object : SignatureBytesDeserializer {

        private lateinit var instance: SignatureBytesDeserializer

        fun initialize(instance: SignatureBytesDeserializer) {
            this.instance = instance
        }

        override fun deserialize(sigBytes: ByteArray): CompositeSignaturesWithKeys = instance.deserialize(sigBytes)
    }

    fun deserialize(sigBytes: ByteArray): CompositeSignaturesWithKeys
}