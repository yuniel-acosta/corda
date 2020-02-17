package net.corda.chain


import net.corda.chain.flows.issue.IssueChainFlowAllParticipants
import net.corda.chain.flows.move.MoveChainFlowAllParticipantsFlow
import net.corda.chain.states.ChainStateAllParticipants
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.ColumnPredicate
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.node.services.vault.SortAttribute
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class IssueChainFlowsTests {

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
                        "net.corda.chain.schema.ChainContractsSchemaV1"),
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

//    fun issueTokens (node: StartedMockNode, quantity: Double) : CordaFuture<SignedTransaction> {
//        val d = node.startFlow(
//                IssueChainTokens(party = node.info.legalIdentities.single(), quantity = quantity))
//        mockNetwork.runNetwork()
//        return d
//    }

    private fun invokeChainFlowAllParticipants (node: StartedMockNode, partyA: Party, partyB: Party) : CordaFuture<SignedTransaction> {
        val d = node.startFlow(IssueChainFlowAllParticipants (partyA = partyA, partyB = partyB))
        mockNetwork.runNetwork()
        return d
    }

    private fun moveChainFlowAllParticipants (node: StartedMockNode, partyA: Party, partyB: Party, linearId: UniqueIdentifier) : CordaFuture<SignedTransaction> {
        val d = node.startFlow(MoveChainFlowAllParticipantsFlow (partyA = partyA, partyB = partyB, linearId = linearId))
        mockNetwork.runNetwork()
        return d
    }

    @Test
    fun `Chain Test`() {

        var newSize = 0
        var prevSize = 0

        val partyA = nodeA.info.legalIdentities.single()
        val partyB = nodeB.info.legalIdentities.single()
        val partyC = nodeC.info.legalIdentities.single()

        // issue tokens
//        val txIssue1 = issueTokens(nodeA,600.0).getOrThrow()
//        val fungibleToken1 = txIssue1.coreTransaction.outputsOfType<FungibleToken>().single()
//        assertEquals (fungibleToken1.amount.quantity, 600L)

        val txChainFlow1= invokeChainFlowAllParticipants (nodeA, partyB, partyC).getOrThrow()
        val chainState1= txChainFlow1.coreTransaction.outputsOfType<ChainStateAllParticipants>().single()
        val chainLinearID = chainState1.linearId

        assertEquals(chainState1.participants.toSet(), setOf(partyA, partyB, partyC))
        println("chainState1 = $chainState1")

        val txChainFlow2= moveChainFlowAllParticipants(nodeA, partyB, partyC, chainState1.linearId).getOrThrow()
        newSize = printVaultResults (index = 1)
        assertEquals(newSize-prevSize,3)

        val txChainFlow3= moveChainFlowAllParticipants(nodeB, partyA, partyC, chainLinearID).getOrThrow()
        prevSize = newSize
        newSize = printVaultResults (index = 2)
        assertEquals(newSize-prevSize,2)

        val txChainFlow4= moveChainFlowAllParticipants(nodeC, partyA, partyB, chainLinearID).getOrThrow()
        prevSize = newSize
        newSize = printVaultResults (index = 3)
        assertEquals(newSize-prevSize,2)

        mockNetwork.stopNodes()
    }

    private fun printVaultResults (index: Int) : Int {
        val vaultCriteria = QueryCriteria.VaultQueryCriteria (status = Vault.StateStatus.ALL)
        val sortAttribute = SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME)
        val sorter = Sort(setOf(Sort.SortColumn(sortAttribute, Sort.Direction.ASC)))

        val queryResult = nodeA.services.vaultService
                .queryBy (contractStateType = ChainStateAllParticipants::class.java,
                    criteria = vaultCriteria,
                    sorting = sorter
                )

        val states = queryResult.states
        states.forEach { println("states [$index] = ${it.state.data}") }
        queryResult.statesMetadata.forEach { println("states metadata [$index] = $it") }
        return states.size
    }


//      set observable
//      val nodeAVaultUpdate2 = nodeA.services.vaultService.updates.toFuture()
//      val a1= nodeAVaultUpdate.get()

}

