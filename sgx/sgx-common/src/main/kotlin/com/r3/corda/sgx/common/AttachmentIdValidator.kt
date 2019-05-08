package com.r3.corda.sgx.common

import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.AttachmentId
import java.io.InputStream

sealed class AttachmentIdValidator {

    abstract fun isTrusted(id: AttachmentId): Boolean

    class AttachmentIdInTrustedSet(
        private val trustedSet: HashSet<SecureHash.SHA256>)
    : AttachmentIdValidator() {

        override fun isTrusted(id: AttachmentId): Boolean {
            return id in trustedSet
        }

        companion object {
            fun readFromStream(stream: InputStream): AttachmentIdInTrustedSet {
                val trustedSet = stream.bufferedReader()
                        .lineSequence()
                        .map { SecureHash.parse(it) }
                        .toHashSet()

                return AttachmentIdInTrustedSet(trustedSet)
            }
        }
    }
}

