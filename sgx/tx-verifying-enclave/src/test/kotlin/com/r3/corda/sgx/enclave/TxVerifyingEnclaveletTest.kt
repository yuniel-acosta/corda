/*
package com.r3.corda.sgx.enclave

import com.r3.corda.sgx.common.OcallRecordingHandler
import com.r3.corda.sgx.common.transactions.ResolvableWireTransaction
import com.r3.corda.sgx.enclave.internal.asContextEnv
import com.r3.corda.sgx.host.CordaEnclaveletClient
import com.r3.corda.sgx.host.internal.TrustedNodeServicesHandler
import com.r3.sgx.core.common.*
import com.r3.sgx.core.host.EnclaveLoadMode
import com.r3.sgx.core.host.EnclaveletHostHandler
import com.r3.sgx.core.host.EpidAttestationHostConfiguration
import junit.framework.Assert.assertEquals
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.sequence
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

class CordaServiceEnclaveletTest {

    companion object {
        private val enclavePath = System.getProperty("com.r3.sgx.enclave.path")
                ?: throw AssertionError("System property 'com.r3.sgx.enclave.path' not set")

        private val configuration = EpidAttestationHostConfiguration(
                quoteType = SgxQuoteType32.LINKABLE,
                spid = Cursor.allocate(SgxSpid))
    }


    @Test
    fun testTxProcessingEnclavelet() {
        val host = CordaEnclaveletClient<ResolvableWireTransaction, SecureHash>(
                serviceHub = TestData.MockTxResolutionServices,
                enclaveFile = File(enclavePath),
                epidConfiguration = configuration,
                mode = EnclaveLoadMode.SIMULATION)


        TestData.SERIALIZATION_ENV.asContextEnv {
            val signedOutput = host.invoke(TestData.DUMMY_TOKEN_ISSUE)
            val attestedOutput = host.attestEnclaveSignature(signedOutput)
            val output = attestedOutput.validate(host.enclaveIdentity)
            val expectedHash = TestData.DUMMY_TOKEN_ISSUE.tx.id
            assertEquals(expectedHash, output)
        }
    }

}

*/
