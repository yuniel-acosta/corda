package net.corda.bank

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.CordaRPCConnection
import net.corda.client.rpc.GracefulReconnect
import net.corda.core.contracts.ContractState
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import java.time.Duration

object SimpleRPCClient {

    private const val BOC_RPC_USER = "bankUser"
    private const val BOC_RPC_PWD = "test"
    private const val BOC_RPC_PORT = 10006

    @JvmStatic
    fun main(args: Array<String>) {
        val hostAndPort = NetworkHostAndPort("localhost", BOC_RPC_PORT)
        val sleep: Long = 2000
        //val maxAttempts: Int = 10

        val rpcConfig: CordaRPCClientConfiguration = CordaRPCClientConfiguration.DEFAULT.copy(
                connectionRetryIntervalMultiplier = 2.0,
                connectionMaxRetryInterval = Duration.ofSeconds(10),
                maxReconnectAttempts = 5
        )
        val client = CordaRPCClient(listOf(hostAndPort), configuration = rpcConfig)
        val gracefulReconnect = GracefulReconnect()
//        val notary = Party(CordaX500Name("Notary Service", "GB", "GB"),)

        client.start(BOC_RPC_USER, BOC_RPC_PWD, gracefulReconnect).use { rpc->
            rpc.proxy.waitUntilNetworkReady().getOrThrow()

            while (true) {
//                rpc.proxy.startFlowDynamic(CashIssueFlow::class.java, 10.DOLLARS, 1, notary)
//                runVaultQuery(rpc)
                println("Sleeping for ${Duration.ofMillis(sleep)}")
                Thread.sleep(sleep)
            }
        }
    }

    fun runVaultQuery(rpc: CordaRPCConnection) {
        val resultStates = rpc.proxy.vaultQueryBy<ContractState>().states
        resultStates.forEach {
            println("-----------------------------")
            println(it)
            println(it.state.data)
        }
    }
}
