package net.corda.mappedschemademo.workflows.test

import com.google.common.collect.ImmutableList
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.withoutIssuer
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.mappedschemademo.contracts.schema.InvoiceFinanceDealSchemaV1
import net.corda.mappedschemademo.contracts.state.Invoice
import net.corda.mappedschemademo.contracts.state.InvoiceFinanceDealState
import net.corda.mappedschemademo.workflows.AcceptInvoiceFinanceDealFlow
import net.corda.mappedschemademo.workflows.ProposeInvoiceFinanceDealFlow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import javax.persistence.EntityManager
import kotlin.test.assertEquals
import kotlin.test.fail

class ProposeInvoiceFinanceDealFlowTest {
    private lateinit var network: MockNetwork
    private lateinit var borrower: StartedMockNode
    private lateinit var lender: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("net.corda.mappedschemademo.contracts"),
                TestCordapp.findCordapp("net.corda.mappedschemademo.workflows")
        ) + FINANCE_CORDAPPS, threadPerNode = true))
        borrower = network.createNode()
        lender = network.createNode()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    val logger = loggerFor<ProposeInvoiceFinanceDealFlowTest>()

    private fun getStateAndReference(linearId: UniqueIdentifier): StateAndRef<InvoiceFinanceDealState> {
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
                null,
                ImmutableList.of(linearId),
                Vault.StateStatus.UNCONSUMED, null)
        val states = lender.services.vaultService.queryBy<InvoiceFinanceDealState>(queryCriteria).states

        if (states.size == 1) {
            return states.single()
        }
        fail("State not found in vault")
    }

    private fun EntityManager.getPersistentInvoiceFromStateRef(ref: StateRef): InvoiceFinanceDealSchemaV1.PersistentInvoiceFinanceDeal {
        val query2 = criteriaBuilder.createQuery(InvoiceFinanceDealSchemaV1.PersistentInvoiceFinanceDeal::class.java)

        val type2 = query2.from(InvoiceFinanceDealSchemaV1.PersistentInvoiceFinanceDeal::class.java)
        val p2 = criteriaBuilder.equal(type2.get<PersistentStateRef>(InvoiceFinanceDealSchemaV1.PersistentInvoiceFinanceDeal::stateRef.name), PersistentStateRef(ref))
        query2.where(p2)
        return createQuery(query2).resultList.firstOrNull() ?: throw Exception("something")
    }

    @Test
    fun `create a deal proposal`() {
        val flow = ProposeInvoiceFinanceDealFlow.Initiator(
                "10000",
                lender.info.singleIdentity(),
                100.POUNDS,
                5.POUNDS,
                listOf(Invoice(invoiceNumber = "20000", supplier = "MegaCorp", value = 150.POUNDS, paid = 0.POUNDS), Invoice(invoiceNumber = "20001", supplier = "MegaCorp", value = 150.POUNDS, paid = 0.POUNDS)))
        val future = borrower.startFlow(flow)
        network.runNetwork()

        val linearId = future.getOrThrow()

        lender.transaction {
            val deal = lender.services.withEntityManager {
                val state = getStateAndReference(linearId)
                getPersistentInvoiceFromStateRef(state.ref)
            }
            assertEquals(2, deal.invoiceList.size)

            //For debugging
            println("Deal reference ${deal.reference} for ${deal.loanAmount} and ${deal.feeAmount} fee")
            deal.invoiceList.forEach { invoice ->
                println("Invoice ${invoice.supplier} for ${invoice.valueAmount}")
            }
        }
    }

    @Test
    fun `create a deal proposal and accept`() {
        val proposalFlow = ProposeInvoiceFinanceDealFlow.Initiator(
                "10000",
                lender.info.singleIdentity(),
                100.POUNDS,
                5.POUNDS,
                listOf(Invoice(invoiceNumber = "20000", supplier = "MegaCorp", value = 150.POUNDS, paid = 0.POUNDS), Invoice(invoiceNumber = "20001", supplier = "MegaCorp", value = 150.POUNDS, paid = 0.POUNDS)))
        val proposalResultFuture = borrower.startFlow(proposalFlow)
        val linearId = proposalResultFuture.getOrThrow()
        logger.warn("Wait Quiescent 1")
        network.waitQuiescent()
        logger.warn("Wait Quiescent 1 end")

//        val issueCashFlow = CashIssueFlow(CashIssueFlow.IssueRequest(1000.POUNDS, OpaqueBytes.of(0), lender.services.networkMapCache.notaryIdentities.firstOrNull()
//                ?: throw IllegalStateException("Could not find a notary.")))
//        lender.startFlow(issueCashFlow).getOrThrow()
//        logger.warn("Wait Quiescent 2")
//        network.waitQuiescent()
//        logger.warn("Wait Quiescent 2 end")

//        val acceptanceFlow = AcceptInvoiceFinanceDealFlow.Initiator(linearId)
////        lender.startFlow(acceptanceFlow).getOrThrow()
////        logger.warn("Wait Quiescent 3")
////        network.waitQuiescent()
////        logger.warn("Wait Quiescent 3 end")
////
////        borrower.transaction {
////            val allCash = borrower.services.vaultService.queryBy<Cash.State>().states
////            val relevantCash = allCash.filter { it.state.data.amount.token.product == Currency.getInstance("GBP") }
////                    .map { it.state.data.amount.withoutIssuer() }
////                    .reduce { acc, amt -> acc + amt }
////            assertEquals(relevantCash, 100.POUNDS)
////        }
    }
}