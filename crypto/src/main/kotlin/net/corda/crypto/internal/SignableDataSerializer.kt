package net.corda.crypto.internal

import net.corda.core.crypto.SignableData
import net.corda.core.utilities.OpaqueBytes

interface SignableDataSerializer {
    companion object : SignableDataSerializer {

        private lateinit var instance: SignableDataSerializer

        fun initialize(instance: SignableDataSerializer) {
            this.instance = instance
        }

        override fun serialize(signableData: SignableData): OpaqueBytes = instance.serialize(signableData)
    }

    fun serialize(signableData: SignableData): OpaqueBytes
}