package net.corda.coretests.transactions

import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.declaredField
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.OpaqueBytes
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.internal.fakeAttachment
import org.apache.commons.io.IOUtils
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.NotSerializableException
import java.net.URL
import kotlin.test.assertFailsWith

class AttachmentsClassLoaderSerializationTests {

    companion object {
        val ISOLATED_CONTRACTS_JAR_PATH: URL = AttachmentsClassLoaderSerializationTests::class.java.getResource("/isolated.jar")
        private const val ISOLATED_CONTRACT_CLASS_NAME = "net.corda.isolated.contracts.AnotherDummyContract"
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    // These tests are not Attachment specific. Should they be removed?
    @Test(timeout=300_000)
	fun `test serialization of SecureHash`() {
        val secureHash = SecureHash.randomSHA256()
        val bytes = secureHash.serialize()
        val copiedSecuredHash = bytes.deserialize()

        assertEquals(secureHash, copiedSecuredHash)
    }

    @Test(timeout=300_000)
	fun `test serialization of OpaqueBytes`() {
        val opaqueBytes = OpaqueBytes("0123456789".toByteArray())
        val bytes = opaqueBytes.serialize()
        val copiedOpaqueBytes = bytes.deserialize()

        assertEquals(opaqueBytes, copiedOpaqueBytes)
    }

    @Test(timeout=300_000)
	fun `test serialization of sub-sequence OpaqueBytes`() {
        val bytesSequence = ByteSequence.of("0123456789".toByteArray(), 3, 2)
        val bytes = bytesSequence.serialize()
        val copiedBytesSequence = bytes.deserialize()

        assertEquals(bytesSequence, copiedBytesSequence)
    }
}

