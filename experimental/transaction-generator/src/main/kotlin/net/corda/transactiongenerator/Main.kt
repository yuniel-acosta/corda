package net.corda.transactiongenerator

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.RPCException
import net.corda.core.internal.mapNotNull
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.POUNDS
import net.corda.finance.flows.CashIssueAndPaymentFlow
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule
import kotlin.streams.toList

class Driver(val parameters: Parameters) {

    private val seedNode = NetworkHostAndPort.parse(parameters.hostPort)

    private val logger = LoggerFactory.getLogger("Driver")

    fun rpcAddresses(): List<NetworkHostAndPort> {
        logger.info("Connecting to seed node $seedNode")
        val client = CordaRPCClient(seedNode).start(parameters.username, parameters.password)
        val notaries = client.proxy.notaryIdentities()
        val networkMap = client.proxy.networkMapSnapshot()
        client.close()

        for (entry in networkMap) {
            logger.info("${entry.legalIdentities}: ${entry.addresses}")
        }

        val networkMapFiltered = networkMap.filter {
            parameters.includeNotaries || !notaries.any { n -> it.legalIdentities.contains(n) }
        }.filter {
            parameters.filterPattern.isEmpty() || it.legalIdentities.map { it.name.toString() }.any { it.contains(parameters.filterPattern) }
        }

        // RPC port is P2P port of the first address plus one by convention.
        val rpcAddresses = networkMapFiltered.map { it.addresses[0] }.map { it.copy(port = it.port + 1) }

        logger.info("Discovered RPC addresses: $rpcAddresses")

        return rpcAddresses
    }

    fun start() {
        logger.info("Starting driver")
        val addresses = rpcAddresses()
        logger.info("Obtained addresses")
        val rpcClients = addresses.parallelStream().mapNotNull {
            logger.info("Attempting to connect to $it")
            try {
                it to CordaRPCClient(it).start(parameters.username, parameters.password)
            } catch (e: RPCException) {
                logger.warn("Caught RPC Exception, ignoring host $it", e)
                null
            }
        }.toList().toMap()

        logger.info("Started ${rpcClients.size} rpc clients, ${addresses.size - rpcClients.size} nodes not available.")

        val rng = Random(parameters.rngSeed)

        while (true) {
            // TODO: use existing clients not addresses
            val clientAddress = addresses.shuffled(rng).first()
            if (rpcClients[clientAddress] == null) {
                continue
            }
            val rpcClient = rpcClients[clientAddress]!!.proxy
            val myIdentity = rpcClient.nodeInfo().legalIdentities.first()
            val notaries = rpcClient.notaryIdentities()

            val peer = rpcClient.networkMapSnapshot().filterNot {
                it.legalIdentities.contains(myIdentity)
            }.filter {
                parameters.includeNotaries || !notaries.any { n -> it.legalIdentities.contains(n) }
            }.filter {
                parameters.filterPattern.isEmpty() || it.legalIdentities.map { it.name.toString() }.any { it.contains(parameters.filterPattern) }
            }.shuffled(rng).first().legalIdentities.first()

            logger.info("all notaries: $notaries")
            val notary = notaries.first()
            logger.info("$myIdentity -> $peer (notary $notary)")
            try {
                val response = rpcClient.startFlow(::CashIssueAndPaymentFlow, 100.POUNDS, OpaqueBytes.of(1), peer, false, notary).returnValue.get(30, TimeUnit.SECONDS)
                logger.info("response: $response")
            } catch(e: Exception) {
                logger.info("Flow exception caught", e)
            }
        }
    }
}

fun main(args: Array<String>) {

    val parameters = Parameters()
    val commandline = CommandLine(parameters)


    commandline.parse(*args)
    if (commandline.isUsageHelpRequested) {
        CommandLine.usage(Parameters(), System.out)
        return
    }

    val driver = Driver(parameters)
    driver.start()

    // Credentials are the same for all nodes of the test network by convention.

}