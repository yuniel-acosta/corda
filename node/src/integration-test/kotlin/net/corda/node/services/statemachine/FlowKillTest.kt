package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.concurrent.Semaphore
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.services.Permissions
import net.corda.node.services.statemachine.Checkpoint.FlowStatus
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import net.corda.testing.node.internal.waitForShutdown
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class FlowKillTest {

    private val rpcUser = User("user1", "test", permissions = setOf(Permissions.all()))

    internal fun startDriver(notarySpec: NotarySpec = NotarySpec(DUMMY_NOTARY_NAME), dsl: DriverDSL.() -> Unit) {
        driver(
                DriverParameters(
                        notarySpecs = listOf(notarySpec),
                        startNodesInProcess = false,
                        inMemoryDB = false,
                        systemProperties = mapOf("co.paralleluniverse.fibers.verifyInstrumentation" to "true")
                )
        ) {
            dsl()
        }
    }

    @Test(timeout=300_000)
    fun `Flow status is set to killed in database when the sleeping flow is killed`() {
        startDriver {
            val aliceNode = startNode(NodeParameters(providedName = ALICE_NAME, rpcUsers = listOf(rpcUser))).getOrThrow()
            val aliceClient = CordaRPCClient(aliceNode.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            val flow = aliceClient.startFlow(FlowKillTest::SleepingFlow)
            aliceClient.killFlow(flow.id)
            val flow1 = aliceClient.startFlow(FlowKillTest::GetCheckpointStatusFlow, flow.id.uuid)
            assertEquals(FlowStatus.KILLED, flow1.returnValue.getOrThrow())
            aliceClient.waitForShutdown()
        }
    }

    @StartableByRPC
    internal class SleepingFlow : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            sleep(30.seconds)
        }
    }

    @StartableByRPC
    class GetCheckpointStatusFlow(var uuid: UUID) : FlowLogic<FlowStatus?>() {
        @Suspendable
        override fun call(): FlowStatus? {
            var count = 0
            var ordinal : Int? = null
            serviceHub.jdbcSession().prepareStatement("select status from node_checkpoints where flow_id='$uuid'").use { ps ->
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        ordinal = rs.getInt(1)
                        count++
                    }
                }
            }
            if (count == 0) {
                return null
            }
            return FlowStatus.values()[ordinal!!]
        }
    }
}