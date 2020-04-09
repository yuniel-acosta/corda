package net.corda.node.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.context.InvocationContext
import net.corda.core.context.InvocationOrigin
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByService
import net.corda.core.node.AppServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.DOLLARS
import net.corda.node.internal.cordapp.DummyRPCFlow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.FINANCE_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class CordaServiceTest {
    private lateinit var mockNet: MockNetwork
    private lateinit var nodeA: StartedMockNode

    @Before
    fun start() {
        mockNet = MockNetwork(MockNetworkParameters(threadPerNode = true, cordappsForAllNodes = listOf(FINANCE_CONTRACTS_CORDAPP, enclosedCordapp())))
        nodeA = mockNet.createNode()
        mockNet.startNodes()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test(timeout=300_000)
	fun `Can't start StartableByRPC flows`() {
        val service = nodeA.services.cordaService(TestCordaService2::class.java)
        assertFailsWith<IllegalArgumentException> { service.startInvalidRPCFlow() }
    }

    @Test(timeout=300_000)
	fun `Corda service can access a non-null thread context classloader`() {
        val service = nodeA.services.cordaService(CordaServiceThatRequiresThreadContextClassLoader::class.java)
        service.thatWeCanAccessClassLoader()
    }

    /**
     * Reproduce CORDA-2296
     * Querying the vault from a services constructor failed because the criteriaBuilder
     * had not been initialized.
     */
    @Test(timeout=300_000)
	fun `Can query vault service in constructor`() {
        nodeA.services.cordaService(VaultQueryService::class.java)
    }

    @Test(timeout=300_000)
	fun `Can query using jdbc session in constructor`() {
        nodeA.services.cordaService(JdbcSessionQueryService::class.java)
    }

    @Test(timeout=300_000)
	fun `Can use entity manager in constructor`() {
        nodeA.services.cordaService(EntityManagerService::class.java)
    }

    @CordaService
    class TestCordaService2(val appServiceHub: AppServiceHub): SingletonSerializeAsToken() {
        fun startInvalidRPCFlow() {
            val handle = appServiceHub.startFlow(DummyRPCFlow())
            handle.returnValue.get()
        }

    }

    @CordaService
    class LegacyCordaService(@Suppress("UNUSED_PARAMETER") simpleServiceHub: ServiceHub) : SingletonSerializeAsToken()

    @CordaService
    class VaultQueryService(val serviceHub: AppServiceHub): SingletonSerializeAsToken() {
        init {
            val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
            serviceHub.vaultService.trackBy(ContractState::class.java, criteria)
        }
    }

    /**
     * See: CORDA-2653
     * This is to check that a corda service is presented with a non-null thread context classloader
     */
    @CordaService
    class CordaServiceThatRequiresThreadContextClassLoader(val serviceHub: AppServiceHub) : SingletonSerializeAsToken() {
        init {
            assertNotNull(Thread.currentThread().contextClassLoader, "thread context classloader should not be null during service initialisation")
        }

        fun thatWeCanAccessClassLoader() {
            assertNotNull(Thread.currentThread().contextClassLoader, "thread context classloader should not be null during service initialisation")
        }
    }

    @CordaService
    class JdbcSessionQueryService(val serviceHub: AppServiceHub): SingletonSerializeAsToken() {
        init {
            serviceHub.jdbcSession().prepareStatement("SELECT * FROM VAULT_STATES").execute()
        }
    }

    @CordaService
    class EntityManagerService(val serviceHub: AppServiceHub): SingletonSerializeAsToken() {
        init {
            serviceHub.withEntityManager {
                createNativeQuery("SELECT * FROM VAULT_STATES").resultList
            }
        }
    }
}