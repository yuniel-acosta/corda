package com.r3.corda.sgx.common

import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.AbstractAttachment

class UploadableAttachment(dataLoader: () -> ByteArray): AbstractAttachment(dataLoader, null) {
    override val id: SecureHash by lazy { attachmentData.sha256() }
}