package net.corda.core.serialization.internal

import net.corda.core.crypto.SignableData
import net.corda.core.serialization.serialize
import net.corda.core.utilities.OpaqueBytes
import net.corda.crypto.internal.SignableDataSerializer

class SignableDataSerializerImpl : SignableDataSerializer {
    companion object {
        fun registerInstance() {
            SignableDataSerializer.initialize(SignableDataSerializerImpl())
        }
    }

    override fun serialize(signableData: SignableData): OpaqueBytes {
        return signableData.serialize()
    }
}