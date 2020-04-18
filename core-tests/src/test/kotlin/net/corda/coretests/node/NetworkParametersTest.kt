package net.corda.coretests.node

import net.corda.core.node.NetworkParameters
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.*
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.MOCK_VERSION_INFO
import org.assertj.core.api.Assertions.*
import org.junit.After
import org.junit.Test
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

class NetworkParametersTest {
    private val mockNet = InternalMockNetwork(
            defaultParameters = MockNetworkParameters(networkSendManuallyPumped = true),
            notarySpecs = listOf(MockNetworkNotarySpec(DUMMY_NOTARY_NAME))
    )

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    // Minimum Platform Version tests
    @Test(timeout=300_000)
	fun `node shutdowns when on lower platform version than network`() {
        val alice = mockNet.createUnstartedNode(InternalMockNodeParameters(legalName = ALICE_NAME, forcedID = 100, version = MOCK_VERSION_INFO.copy(platformVersion = 1)))
        val aliceDirectory = mockNet.baseDirectory(100)
        val netParams = testNetworkParameters(minimumPlatformVersion = 2)
        dropParametersToDir(aliceDirectory, netParams)
        assertThatThrownBy { alice.start() }.hasMessageContaining("platform version")
    }

    @Test(timeout=300_000)
	fun `node works fine when on higher platform version`() {
        val alice = mockNet.createUnstartedNode(InternalMockNodeParameters(legalName = ALICE_NAME, forcedID = 100, version = MOCK_VERSION_INFO.copy(platformVersion = 2)))
        val aliceDirectory = mockNet.baseDirectory(100)
        val netParams = testNetworkParameters(minimumPlatformVersion = 1)
        dropParametersToDir(aliceDirectory, netParams)
        alice.start()
    }

    @Test(timeout=300_000)
	fun `that we can copy while preserving the event horizon`() {
        val nm1 = NetworkParameters(
                minimumPlatformVersion = 1,
                maxMessageSize = Int.MAX_VALUE,
                maxTransactionSize = Int.MAX_VALUE,
                modifiedTime = Instant.now(),
                epoch = 1,
                eventHorizon = Duration.ofDays(1)
        )
        val twoDays = Duration.ofDays(2)
        val nm2 = nm1.copy(minimumPlatformVersion = 2, eventHorizon = twoDays)

        assertEquals(2, nm2.minimumPlatformVersion)
        assertEquals(nm1.maxMessageSize, nm2.maxMessageSize)
        assertEquals(nm1.maxTransactionSize, nm2.maxTransactionSize)
        assertEquals(nm1.modifiedTime, nm2.modifiedTime)
        assertEquals(nm1.epoch, nm2.epoch)
        assertEquals(twoDays, nm2.eventHorizon)
    }

    // Helpers
    private fun dropParametersToDir(dir: Path, params: NetworkParameters) {
        NetworkParametersCopier(params).install(dir)
    }
}
