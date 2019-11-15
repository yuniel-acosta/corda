package net.corda.node

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.utilities.Try
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.services.statemachine.ReceiveTimeoutException
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.enclosedCordapp
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Duration

class AsyncMessagingTest {

    @Test
    fun `receive returns a failure result when it times out`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val (nodeA, nodeB) = listOf(
                    startNode(),
                    startNode()
            ).map { it.getOrThrow() }

            val result = nodeA.rpc.startFlowDynamic(InitFlow::class.java, nodeB.nodeInfo.legalIdentities.first()).returnValue.get()
            assertThat(result.isFailure).isTrue()
            assertThat((result as Try.Failure).exception).isInstanceOf(ReceiveTimeoutException::class.java)
        }
    }

    @Test
    fun `late messages are not properly discarded`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList(), cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val (nodeA, nodeB) = listOf(
                    startNode(),
                    startNode()
            ).map { it.getOrThrow() }

            val result = nodeA.rpc.startFlowDynamic(InitFlowForDiscard::class.java, nodeB.nodeInfo.legalIdentities.first()).returnValue.get()
            assertThat(result.isSuccess).isTrue()
            assertThat((result as Try.Success).value).isEqualTo("first message")
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class InitFlow(private val party: Party) : FlowLogic<Try<*>>() {
        @Suspendable
        override fun call(): Try<*> {
            val session = initiateFlow(party)
            session.send("hey")

            return session.receive(String::class.java, false, Duration.ofSeconds(1)).unwrap { it }
        }
    }

    @InitiatedBy(InitFlow::class)
    class InitiatedFlow(private val initiatingSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            initiatingSession.receive<String>().unwrap { it }
            // will never return, simply blocking here to trigger the timeout on the other side.
            initiatingSession.receive<String>()
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class InitFlowForDiscard(private val party: Party) : FlowLogic<Try<*>>() {
        @Suspendable
        override fun call(): Try<*> {
            val session = initiateFlow(party)
            session.send("hey")

            session.receive(String::class.java, false, Duration.ofSeconds(1)).unwrap { it }

            return session.receive(String::class.java, false).unwrap{ Try.Success(it) }
        }
    }

    @InitiatedBy(InitFlowForDiscard::class)
    class InitiatedFlowForDiscard(private val initiatingSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            initiatingSession.receive<String>().unwrap { it }

            sleep(Duration.ofSeconds(4))

            // late message that won't be discarded at the moment, while it should be.
            initiatingSession.send("first message")
            initiatingSession.send("second message")

        }
    }

}