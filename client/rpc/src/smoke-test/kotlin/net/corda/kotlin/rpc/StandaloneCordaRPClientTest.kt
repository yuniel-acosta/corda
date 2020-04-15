package net.corda.kotlin.rpc

import com.google.common.hash.Hashing
import com.google.common.hash.HashingInputStream
import net.corda.client.rpc.CordaRPCConnection
import net.corda.client.rpc.PermissionException
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.InputStreamAndHash
import net.corda.core.messaging.*
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.vault.*
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.finance.SWISS_FRANCS
import net.corda.finance.USD
import net.corda.nodeapi.internal.config.User
import net.corda.smoketesting.NodeConfig
import net.corda.smoketesting.NodeProcess
import org.apache.commons.io.output.NullOutputStream
import org.junit.*
import org.junit.rules.ExpectedException
import java.io.FilterInputStream
import java.io.InputStream
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StandaloneCordaRPClientTest {
    private companion object {
        private val log = contextLogger()
        val superUser = User("superUser", "test", permissions = setOf("ALL"))
        val nonUser = User("nonUser", "test", permissions = emptySet())
        val rpcUser = User("rpcUser", "test", permissions = setOf("InvokeRpc.startFlow", "InvokeRpc.killFlow"))
        val port = AtomicInteger(15200)
        const val attachmentSize = 2116
        val timeout = 60.seconds
    }

    private lateinit var factory: NodeProcess.Factory
    private lateinit var notary: NodeProcess
    private lateinit var rpcProxy: CordaRPCOps
    private lateinit var connection: CordaRPCConnection
    private lateinit var notaryNode: NodeInfo
    private lateinit var notaryNodeIdentity: Party

    private val notaryConfig = NodeConfig(
            legalName = CordaX500Name(organisation = "Notary Service", locality = "Zurich", country = "CH"),
            p2pPort = port.andIncrement,
            rpcPort = port.andIncrement,
            rpcAdminPort = port.andIncrement,
            isNotary = true,
            users = listOf(superUser, nonUser, rpcUser)
    )

    @get:Rule
    val exception: ExpectedException = ExpectedException.none()

    @Before
    fun setUp() {
        factory = NodeProcess.Factory()
        notary = factory.create(notaryConfig)
        connection = notary.connect(superUser)
        rpcProxy = connection.proxy
        notaryNode = fetchNotaryIdentity()
        notaryNodeIdentity = rpcProxy.nodeInfo().legalIdentitiesAndCerts.first().party
    }

    @After
    fun done() {
        connection.use {
            notary.close()
        }
    }


    @Test(timeout=300_000)
	fun `test attachments`() {
        val attachment = InputStreamAndHash.createInMemoryTestZip(attachmentSize, 1)
        assertFalse(rpcProxy.attachmentExists(attachment.sha256))
        val id = attachment.inputStream.use { rpcProxy.uploadAttachment(it) }
        assertEquals(attachment.sha256, id, "Attachment has incorrect SHA256 hash")

        val hash = HashingInputStream(Hashing.sha256(), rpcProxy.openAttachment(id)).use { it ->
            it.copyTo(NullOutputStream())
            SecureHash.SHA256(it.hash().asBytes())
        }
        assertEquals(attachment.sha256, hash)
    }

    @Ignore("CORDA-1520 - After switching from Kryo to AMQP this test won't work")
    @Test(timeout=300_000)
	fun `test wrapped attachments`() {
        val attachment = InputStreamAndHash.createInMemoryTestZip(attachmentSize, 1)
        assertFalse(rpcProxy.attachmentExists(attachment.sha256))
        val id = WrapperStream(attachment.inputStream).use { rpcProxy.uploadAttachment(it) }
        assertEquals(attachment.sha256, id, "Attachment has incorrect SHA256 hash")

        val hash = HashingInputStream(Hashing.sha256(), rpcProxy.openAttachment(id)).use { it ->
            it.copyTo(NullOutputStream())
            SecureHash.SHA256(it.hash().asBytes())
        }
        assertEquals(attachment.sha256, hash)
    }

    @Test(timeout=300_000)
	fun `test network map`() {
        assertEquals(notaryConfig.legalName, notaryNodeIdentity.name)
    }

    private fun fetchNotaryIdentity(): NodeInfo {
        val nodeInfo = rpcProxy.networkMapSnapshot()
        assertEquals(1, nodeInfo.size)
        return nodeInfo[0]
    }

    // This InputStream cannot have been whitelisted.
    private class WrapperStream(input: InputStream) : FilterInputStream(input)
}
