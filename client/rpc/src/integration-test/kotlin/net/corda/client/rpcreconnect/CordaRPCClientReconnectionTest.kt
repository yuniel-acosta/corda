package net.corda.client.rpcreconnect

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.CordaRPCClientTest
import net.corda.client.rpc.GracefulReconnect
import net.corda.client.rpc.MaxRpcRetryException
import net.corda.client.rpc.RPCException
import net.corda.client.rpc.internal.ReconnectingCordaRPCOps
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.User
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.rpcDriver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.lang.Thread.sleep
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CordaRPCClientReconnectionTest {

    private val portAllocator = incrementalPortAllocation()

    private val gracefulReconnect = GracefulReconnect()
    private val config = CordaRPCClientConfiguration.DEFAULT.copy(
            connectionRetryInterval = Duration.ofSeconds(1),
            connectionRetryIntervalMultiplier = 1.0
    )

    companion object {
        val rpcUser = User("user1", "test", permissions = setOf(Permissions.all()))
    }

    @Test(timeout=300_000)
    fun `an RPC call fails, when the maximum number of attempts is exceeded`() {
        driver(DriverParameters(cordappsForAllNodes = emptyList())) {
            val address = NetworkHostAndPort("localhost", portAllocator.nextPort())

            fun startNode(): NodeHandle {
                return startNode(
                        providedName = CHARLIE_NAME,
                        rpcUsers = listOf(CordaRPCClientTest.rpcUser),
                        customOverrides = mapOf("rpcSettings.address" to address.toString())
                ).getOrThrow()
            }

            val node = startNode()
            val client = CordaRPCClient(node.rpcAddress, config)

            (client.start(rpcUser.username, rpcUser.password, gracefulReconnect = GracefulReconnect(maxAttempts = 1))).use {
                val rpcOps = it.proxy as ReconnectingCordaRPCOps

                node.stop()
                thread { startNode() }
                assertThatThrownBy { rpcOps.networkParameters }
                        .isInstanceOf(MaxRpcRetryException::class.java)
            }

        }
    }

    @Test(timeout=300_000)
    fun `establishing an RPC connection fails if there is no node listening to the specified address`() {
        rpcDriver {
            assertThatThrownBy {
                CordaRPCClient(NetworkHostAndPort("localhost", portAllocator.nextPort()), config)
                        .start(rpcUser.username, rpcUser.password, GracefulReconnect())
            }.isInstanceOf(RPCException::class.java)
                    .hasMessage("Cannot connect to server(s). Tried with all available servers.")
        }
    }

    @Test(timeout=300_000)
    fun `RPC connection stops reconnecting after config number of retries`() {
        driver(DriverParameters(cordappsForAllNodes = emptyList())) {
            val address = NetworkHostAndPort("localhost", portAllocator.nextPort())
            val conf = config.copy(maxReconnectAttempts = 2)
            fun startNode(): NodeHandle = startNode(
                    providedName = CHARLIE_NAME,
                    rpcUsers = listOf(CordaRPCClientTest.rpcUser),
                    customOverrides = mapOf("rpcSettings.address" to address.toString())
            ).getOrThrow()

            val node = startNode()
            val connection = CordaRPCClient(node.rpcAddress, conf).start(rpcUser.username, rpcUser.password, gracefulReconnect)
            node.stop()
            // After two tries we throw RPCException
            assertThatThrownBy { connection.proxy.isWaitingForShutdown() }
                    .isInstanceOf(RPCException::class.java)
        }
    }
}
