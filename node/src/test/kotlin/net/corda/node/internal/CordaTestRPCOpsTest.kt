package net.corda.node.internal

//import net.corda.testing.internal.IntegrationTest
//import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.client.rpc.internal.RPCClient
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.junit.Test
import kotlin.test.assertEquals

class CordaTestRPCOpsTest /* : IntegrationTest() */ {
    companion object {
        //@ClassRule
        //@JvmField
        //val databaseSchemas = IntegrationTestSchemas(ALICE_NAME)
    }

    @Test(timeout = 300_000)
    fun checkCanCallTestRPC() {
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptyList(), notarySpecs = emptyList())) {
            val nodeHandle = startNode(providedName = ALICE_NAME).getOrThrow()

            val rpcUser = nodeHandle.rpcUsers.first()
            RPCClient<CordaTestRPCOps>(nodeHandle.rpcAddress)
                    .start(CordaTestRPCOps::class.java, rpcUser.username, rpcUser.password).use {
                        assertEquals(PLATFORM_VERSION, it.proxy.protocolVersion)
                    }
        }
    }
}