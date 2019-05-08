package com.r3.corda.sgx.enclave

import com.r3.corda.sgx.common.AttachmentIdValidator
import com.r3.corda.sgx.common.TrustedNodeServices
import com.r3.corda.sgx.enclave.internal.MyAMQPSerializationScheme
import com.r3.corda.sgx.enclave.internal.TrustedNodeServicesImpl
import com.r3.sgx.core.common.*
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.Enclavelet
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SignedData
import net.corda.core.serialization.serialize
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.MessageDigest

abstract class CordaEnclavelet<U: Any, V: Any>: Enclavelet() {
    lateinit var signatureScheme: SignatureScheme
    lateinit var txSigningKeyPair: KeyPair
    private val amqpSerializationEnv = MyAMQPSerializationScheme.createSerializationEnv()

    companion object {
        fun getSignatureScheme(api: EnclaveApi): SignatureScheme {
           return api.signatureSchemeFactory.make(SchemesSettings.EDDSA_ED25519_SHA512)
        }
    }

    interface Connection<U: Any, V: Any> {
        fun process(input: U): V
    }

    abstract val attachmentIdValidators: List<AttachmentIdValidator>

    abstract fun connect(services: TrustedNodeServices): Connection<U, V>

    override fun createReportData(api: EnclaveApi): Cursor<ByteBuffer, SgxReportData> {
        signatureScheme = getSignatureScheme(api)
        txSigningKeyPair = signatureScheme.generateKeyPair()
        val reportData = Cursor.allocate(SgxReportData)
        val buffer = reportData.getBuffer()
        val keyDigest = MessageDigest.getInstance("SHA-512").digest(txSigningKeyPair.public.encoded)
        require(keyDigest.size == SgxReportData.size) {
            "Key Digest of ${keyDigest.size} bytes instead of ${SgxReportData.size}"
        }
        buffer.put(keyDigest, 0, SgxReportData.size)
        return reportData
    }

    override fun createHandler(api: EnclaveApi): Handler<*> {
        return CordaEnclaveletHandler()
    }

    internal inner class ConnectionWrapper(val muxingConnection: MuxingHandler.Connection) {
        private val trustedNodeServices: TrustedNodeServices
        init {
            trustedNodeServices = muxingConnection.addDownstream(0, TrustedNodeServicesImpl(amqpSerializationEnv, attachmentIdValidators))
            val responder = connect(trustedNodeServices)
            muxingConnection.addDownstream(1, AMQPHandler(amqpSerializationEnv) {
                input: U -> signOutput(responder.process(input))
            })
        }
    }

    internal inner class CordaEnclaveletHandler: Handler<ConnectionWrapper> {

        private val muxingHandler = MuxingHandler()

        override fun connect(upstream: Sender): ConnectionWrapper {
            val muxingConnection = muxingHandler.connect(upstream)
            return ConnectionWrapper(muxingConnection)
        }

        override fun onReceive(connection: ConnectionWrapper, input: ByteBuffer) {
            muxingHandler.onReceive(connection.muxingConnection, input)
        }
    }

    private fun signOutput(result: V): SignedData<V> {
        val serialized = result.serialize()
        val signature = signatureScheme.sign(txSigningKeyPair.private, serialized.bytes)
        return SignedData(serialized, DigitalSignature.WithKey(txSigningKeyPair.public, signature))
    }
}