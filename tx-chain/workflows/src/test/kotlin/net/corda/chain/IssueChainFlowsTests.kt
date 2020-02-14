package net.corda.chain


import net.corda.chain.flows.issue.IssueChainFlowAllParticipants
import net.corda.chain.flows.move.MoveChainFlowAllParticipantsFlow
import net.corda.chain.states.ChainStateAllParticipants
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
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

    fun invokeChainFlowAllParticipants (node: StartedMockNode, partyA: Party, partyB: Party) : CordaFuture<SignedTransaction> {
        val d = node.startFlow(IssueChainFlowAllParticipants (partyA = partyA, partyB = partyB))
        mockNetwork.runNetwork()
        return d
    }

    fun moveChainFlowAllParticipants (node: StartedMockNode, partyA: Party, partyB: Party, linearId: UniqueIdentifier) : CordaFuture<SignedTransaction> {
        val d = node.startFlow(MoveChainFlowAllParticipantsFlow (partyA = partyA, partyB = partyB, linearId = linearId))
        mockNetwork.runNetwork()
        return d
    }

    @Test
    fun `Chain Test`() {
        val partyA = nodeA.info.legalIdentities.single()
        val partyB = nodeB.info.legalIdentities.single()
        val partyC = nodeC.info.legalIdentities.single()


        // issue tokens
//        val txIssue1 = issueTokens(nodeA,600.0).getOrThrow()
//        val fungibleToken1 = txIssue1.coreTransaction.outputsOfType<FungibleToken>().single()
//        assertEquals (fungibleToken1.amount.quantity, 600L)

        val nodeAVaultUpdate = nodeA.services.vaultService.updates.toFuture()
        val nodeBVaultUpdate = nodeB.services.vaultService.updates.toFuture()
        val nodeCVaultUpdate = nodeC.services.vaultService.updates.toFuture()

        val txChainFlow1= invokeChainFlowAllParticipants (nodeA, partyB, partyC).getOrThrow()
        val chainState1= txChainFlow1.coreTransaction.outputsOfType<ChainStateAllParticipants>().single()

        assertEquals(chainState1.participants.toSet(), setOf(partyA, partyB, partyC))
        println("chainState1 = $chainState1")

        val a1= nodeAVaultUpdate.get()
        val b1= nodeBVaultUpdate.get()
       // val c1= nodeCVaultUpdate.get()

        val chainStatesA1= nodeA.services.vaultService.queryBy (contractStateType = ChainStateAllParticipants::class.java)
        val chainStatesB1= nodeB.services.vaultService.queryBy (contractStateType = ChainStateAllParticipants::class.java)
      //  val chainStatesC1= nodeC.services.vaultService.queryBy (contractStateType = ChainState::class.java)

        println("a1 = $a1")
        println("b1 = $b1")
       // println("c1 = $c1")

        println("chainStatesA1 = $chainStatesA1")
        println("chainStatesB1 = $chainStatesB1")
       // println("chainStatesC1 = $chainStatesC1")

        //val a2= nodeAVaultUpdate.get()
        //val b2= nodeBVaultUpdate.get()
        //val c2= nodeCVaultUpdate.get()

        // Move Chain States
        //val nodeAVaultUpdate2 = nodeA.services.vaultService.updates.toFuture()
        //val nodeBVaultUpdate2 = nodeB.services.vaultService.updates.toFuture()
        //val nodeCVaultUpdate2 = nodeC.services.vaultService.updates.toFuture()

        val txChainFlow2= moveChainFlowAllParticipants(nodeA, partyB, partyC, chainState1.linearId).getOrThrow()

        // default is VAULT.UNCONSUMED
        val criteria = QueryCriteria.VaultQueryCriteria (status = Vault.StateStatus.ALL)

        //net.corda.chain.flows

        val chainStatesA2= nodeA.services.vaultService.queryBy (criteria = criteria,
                contractStateType = ChainStateAllParticipants::class.java)

        val chainStatesB2= nodeB.services.vaultService.queryBy (criteria = criteria,
                contractStateType = ChainStateAllParticipants::class.java)

        val chainStatesC2= nodeC.services.vaultService.queryBy (criteria = criteria,
                contractStateType = ChainStateAllParticipants::class.java)
//


//        println("a2 = ${nodeAVaultUpdate2.get()}")
//        println("b2 = ${nodeBVaultUpdate2.get()}")
//        println("c2 = ${nodeCVaultUpdate2.get()}")

        println("chainStatesA2 = $chainStatesA2")
        println("chainStatesB2 = $chainStatesB2")
        println("chainStatesC2 = $chainStatesC2")

        moveChainFlowAllParticipants(nodeA, partyB, partyC, chainState1.linearId).getOrThrow()
        moveChainFlowAllParticipants(nodeA, partyB, partyC, chainState1.linearId).getOrThrow()
        moveChainFlowAllParticipants(nodeA, partyB, partyC, chainState1.linearId).getOrThrow()

        mockNetwork.stopNodes()
    }

}

