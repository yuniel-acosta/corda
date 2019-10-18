package com.r3.corda.sgx.enclave

import com.r3.corda.sgx.enclave.internal.MyAMQPSerializationScheme
import com.r3.sgx.core.common.*
import com.r3.sgx.core.common.crypto.SignatureScheme
import com.r3.sgx.core.common.crypto.SignatureSchemeId
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.Enclavelet
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.MessageDigest

abstract class AMQPEnclavelet<U: Any, V: Any>: Enclavelet() {
    lateinit var signatureScheme: SignatureScheme
    lateinit var attestedKeyPair: KeyPair
    protected val amqpSerializationEnv = MyAMQPSerializationScheme.createSerializationEnv()

    companion object {
        fun getSignatureScheme(api: EnclaveApi): SignatureScheme {
           return api.getSignatureScheme(SignatureSchemeId.EDDSA_ED25519_SHA512)
        }
    }

    interface Connection<U: Any, V: Any> {
        fun process(input: U): V
    }

    abstract fun connect(): Connection<U, V>

    override fun createReportData(api: EnclaveApi): Cursor<ByteBuffer, SgxReportData> {
        signatureScheme = getSignatureScheme(api)
        attestedKeyPair = signatureScheme.generateKeyPair()
        val reportData = Cursor.allocate(SgxReportData)
        val buffer = reportData.getBuffer()
        val keyDigest = MessageDigest.getInstance("SHA-512").digest(attestedKeyPair.public.encoded)
        require(keyDigest.size == SgxReportData.size) {
            "Key Digest of ${keyDigest.size} bytes instead of ${SgxReportData.size}"
        }
        buffer.put(keyDigest, 0, SgxReportData.size)
        return reportData
    }

    override fun createHandler(api: EnclaveApi): Handler<*> {
        val logic = connect()
        return AMQPHandler(amqpSerializationEnv) {
            input: U -> logic.process(input)
        }
    }
}