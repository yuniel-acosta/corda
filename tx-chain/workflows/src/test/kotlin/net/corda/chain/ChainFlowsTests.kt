package net.corda.chain


import net.corda.chain.flows.history.ChainHistoryFlow
import net.corda.chain.flows.issue.IssueChainFlow
import net.corda.chain.flows.move.TransactFlow
import net.corda.chain.schemas.AssetStateSchemaV1
import net.corda.chain.states.AssetState
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.packageName
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.vault.DummyLinearStateSchemaV1
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class ChainFlowsTests {

    private var notaryName = "O=Notary,L=London,C=GB"
    private var minimumPlatformVersion = 4

    lateinit var mockNetwork : MockNetwork
    lateinit var nodeA : StartedMockNode
    lateinit var nodeB : StartedMockNode
    lateinit var nodeC : StartedMockNode

    @Before
    fun setup () {
        mockNetwork = MockNetwork(
                // legacy API is used on purpose as otherwise flows defined in tests are not picked up by the framework
                cordappPackages = listOf("com.r3.corda.lib.tokens.workflows",
                        "com.r3.corda.lib.tokens.contracts",
                        "com.r3.corda.lib.tokens.money",
                        "com.r3.corda.lib.accounts.contracts",
                        "com.r3.corda.lib.accounts.workflows",
                        "com.r3.corda.lib.ci.workflows",
                        "net.corda.chain.flows",
                        "net.corda.chain.contracts",
                        "net.corda.chain.schema",
                        "net.corda.chain.schemas.AssetStateSchemaV1",
                        AssetStateSchemaV1::class.packageName),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name.parse(notaryName))),
                networkParameters = testNetworkParameters(minimumPlatformVersion = minimumPlatformVersion)
        )

        nodeA = mockNetwork.createNode(
                MockNodeParameters(legalName = CordaX500Name.parse("O=NodeA,L=London,C=GB")))
        nodeB = mockNetwork.createNode(
                MockNodeParameters(legalName = CordaX500Name.parse("O=NodeB,L=London,C=GB")))
        nodeC = mockNetwork.createNode(
                MockNodeParameters(legalName = CordaX500Name.parse("O=NodeC,L=London,C=GB")))

        mockNetwork.runNetwork()
    }

    private fun StartedMockNode.issueFlow (str: String, parties: List<Party>) : CordaFuture<SignedTransaction> {
        val d = startFlow(IssueChainFlow (str = str, parties = parties))
        mockNetwork.runNetwork()
        return d
    }

    private fun StartedMockNode.transactFlow (str: String, parties: List<Party>, linearId: UniqueIdentifier) : CordaFuture<SignedTransaction> {
        val d = startFlow(TransactFlow (str = str, parties = parties, linearId = linearId))
        mockNetwork.runNetwork()
        return d
    }

    private fun StartedMockNode.chainHistoryFlow () : CordaFuture<Int> {
        val d = startFlow(ChainHistoryFlow())
        mockNetwork.runNetwork()
        return d
    }

    @Test
    fun `Chain Test`() {
        val partyA = nodeA.info.legalIdentities.single()
        val partyB = nodeB.info.legalIdentities.single()
        val partyC = nodeC.info.legalIdentities.single()

        val notary = mockNetwork.notaryNodes.single().info.legalIdentities.single().owningKey

        val txChainFlow1= nodeA.issueFlow ("One" , listOf(partyB, partyC)).getOrThrow()
        val chainState1= txChainFlow1.coreTransaction.outputsOfType<AssetState>().single()
        val chainLinearID = chainState1.id

        assertEquals(chainState1.parties.toSet(), setOf(partyA, partyB, partyC))

        val txChainFlow2= nodeA.transactFlow("Two", listOf(partyB, partyC), chainLinearID).getOrThrow()
        var prevSize = 1
        var newSize= nodeA.chainHistoryFlow().getOrThrow()
        assertEquals(newSize-prevSize,2)

        val txChainFlow3 = nodeB.transactFlow("Three", listOf(partyA, partyC), chainLinearID).getOrThrow()
        prevSize = newSize
        newSize = nodeB.chainHistoryFlow().getOrThrow()
        assertEquals(newSize-prevSize,2)

        val txChainFlow4= nodeC.transactFlow("Four", listOf(partyA, partyB), chainLinearID).getOrThrow()
        prevSize = newSize
        newSize = nodeC.chainHistoryFlow().getOrThrow()
        assertEquals(newSize-prevSize,2)

        mockNetwork.stopNodes()
    }

}

