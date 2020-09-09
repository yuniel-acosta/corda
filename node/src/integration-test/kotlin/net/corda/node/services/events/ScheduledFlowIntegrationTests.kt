package net.corda.node.services.events

import co.paralleluniverse.fibers.Suspendable
import com.google.common.collect.ImmutableList
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.concurrent.CordaFuture
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.testMessage.ScheduledState
import net.corda.testMessage.SpentState
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.dummyCommand
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.DUMMY_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.cordappWithPackages
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.Test
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals

class ScheduledFlowIntegrationTests {
    @StartableByRPC
    @InitiatingFlow
    class InsertInitialStateFlow(private val destination: Party,
                                 private val notary: Party,
                                 private val identity: Int = 1,
                                 private val scheduledFor: Instant? = null) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val creationTime = scheduledFor ?: serviceHub.clock.instant()
            val scheduledState = ScheduledState(creationTime, ourIdentity, destination, identity.toString())
            val builder = TransactionBuilder(notary)
                    .addOutputState(scheduledState, DummyContract.PROGRAM_ID)
                    .addCommand(dummyCommand(ourIdentity.owningKey))
            val tx = serviceHub.signInitialTransaction(builder)
            subFlow(FinalityFlow(tx, initiateFlow(destination)))
        }
    }

    @InitiatedBy(InsertInitialStateFlow::class)
    class InsertInitialStateResponderFlow(private val otherSide: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(ReceiveFinalityFlow(otherSide))
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class AnotherFlow(private val identity: String) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val results = serviceHub.vaultService.queryBy<ScheduledState>(QueryCriteria.LinearStateQueryCriteria(externalId = ImmutableList.of(identity)))
            val state = results.states.firstOrNull() ?: return
            require(!state.state.data.processed) { "Cannot spend an already processed state" }
            val lock = UUID.randomUUID()
            serviceHub.vaultService.softLockReserve(lock, NonEmptySet.of(state.ref))
            val notary = state.state.notary
            val outputState = SpentState(identity, ourIdentity, state.state.data.destination)
            val builder = TransactionBuilder(notary)
                    .addInputState(state)
                    .addOutputState(outputState, DummyContract.PROGRAM_ID)
                    .addCommand(dummyCommand(ourIdentity.owningKey))
            val tx = serviceHub.signInitialTransaction(builder)
            subFlow(FinalityFlow(tx, initiateFlow(state.state.data.destination)))
        }
    }

    @InitiatedBy(AnotherFlow::class)
    class AnotherResponderFlow(private val otherSide: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(ReceiveFinalityFlow(otherSide))
        }
    }

    private fun MutableList<CordaFuture<*>>.getOrThrowAll() {
        forEach {
            try {
                it.getOrThrow()
            } catch (ex: Exception) {
            }
        }
    }

    @Test(timeout=300_000)
	fun `test that when states are being spent at the same time that schedules trigger everything is processed`() {
        driver(DriverParameters(
                startNodesInProcess = false,
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP, cordappWithPackages("net.corda.testMessage"), enclosedCordapp())
        )) {
            val N = 23
            val rpcUser = User("admin", "admin", setOf("ALL"))
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME).map { startNode(providedName = it, rpcUsers = listOf(rpcUser)) }.transpose().getOrThrow()

            val aliceClient = CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password)
            val bobClient = CordaRPCClient(bob.rpcAddress).start(rpcUser.username, rpcUser.password)

            val scheduledFor = Instant.now().plusSeconds(10)
            val initialiseFutures = mutableListOf<CordaFuture<*>>()
            for (i in 0 until N) {
                initialiseFutures.add(aliceClient.proxy.startFlow(
                        ::InsertInitialStateFlow,
                        bob.nodeInfo.legalIdentities.first(),
                        defaultNotaryIdentity,
                        i,
                        scheduledFor
                ).returnValue)
                initialiseFutures.add(bobClient.proxy.startFlow(
                        ::InsertInitialStateFlow,
                        alice.nodeInfo.legalIdentities.first(),
                        defaultNotaryIdentity,
                        i + 100,
                        scheduledFor
                ).returnValue)
            }
            initialiseFutures.getOrThrowAll()

            val spendAttemptFutures = mutableListOf<CordaFuture<*>>()
            for (i in (0 until N).reversed()) {
                spendAttemptFutures.add(aliceClient.proxy.startFlow(::AnotherFlow, (i).toString()).returnValue)
                spendAttemptFutures.add(bobClient.proxy.startFlow(::AnotherFlow, (i + 100).toString()).returnValue)
            }
            spendAttemptFutures.getOrThrowAll()

            // TODO: the queries below are not atomic so we need to allow enough time for the scheduler to finish.  Would be better to query scheduler.
            Thread.sleep(20.seconds.toMillis())

            val aliceStates = aliceClient.proxy.vaultQuery(ScheduledState::class.java).states.filter { it.state.data.processed }
            val aliceSpentStates = aliceClient.proxy.vaultQuery(SpentState::class.java).states

            val bobStates = bobClient.proxy.vaultQuery(ScheduledState::class.java).states.filter { it.state.data.processed }
            val bobSpentStates = bobClient.proxy.vaultQuery(SpentState::class.java).states

            assertEquals(aliceStates.count() + aliceSpentStates.count(), N * 2)
            assertEquals(bobStates.count() + bobSpentStates.count(), N * 2)
            assertEquals(aliceSpentStates.count(), bobSpentStates.count())
        }
    }
}
