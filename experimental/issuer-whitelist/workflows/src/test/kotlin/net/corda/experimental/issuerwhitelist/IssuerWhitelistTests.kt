package net.corda.experimental.issuerwhitelist

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.AttachmentId
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.experimental.issuerwhitelist.contract.MyContract
import net.corda.experimental.issuerwhitelist.contract.MyState
import net.corda.experimental.issuerwhitelist.workflow.ChangeNotaries
import net.corda.experimental.issuerwhitelist.workflow.GenerateNotaryWhitelist
import net.corda.experimental.issuerwhitelist.workflow.IssueMyState
import net.corda.experimental.issuerwhitelist.workflow.MoveMyState
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.TestCordapp
import org.junit.Test
import kotlin.test.assertFailsWith

class IssuerWhitelistTests {
    private val nodeALegalName = DUMMY_BANK_A_NAME
    private val nodeBLegalName = DUMMY_BANK_B_NAME

    private val notary1 = NotarySpec(DUMMY_NOTARY_NAME, validating = true)
    private val notary2 = NotarySpec(DUMMY_NOTARY_NAME.copy(organisation = "Notary Service 2"), validating = true)

    private val driverParameters = DriverParameters(isDebug = true,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
            startNodesInProcess = true,
            cordappsForAllNodes = listOf(
                    TestCordapp.findCordapp("net.corda.experimental.issuerwhitelist.contract"),
                    TestCordapp.findCordapp("net.corda.experimental.issuerwhitelist.workflow")
            ),
            notarySpecs = listOf(notary1, notary2)
    )

    @Test
    fun `issue and move state succeeds`() {
        driver(driverParameters) {
            val nodeAFuture = startNode(providedName = nodeALegalName)
            val nodeBFuture = startNode(providedName = nodeBLegalName)
            val nodeA = nodeAFuture.get()
            val nodeB = nodeBFuture.get()

            val allowedNotaries = nodeA.rpc.notaryIdentities().first()
            val whitelistForState = nodeA.rpc.startFlow(::GenerateNotaryWhitelist, MyState::class.java, listOf(allowedNotaries))
                    .returnValue.get()
            val issuedState = nodeA.rpc.startFlow(::IssueMyState, whitelistForState).returnValue.get()
            nodeA.rpc.startFlow(::MoveMyState, issuedState, nodeB.nodeInfo.legalIdentities.first(), whitelistForState).returnValue.get()
        }
    }

    @Test
    fun `changing to unauthorised notary makes state unusable`() {
        driver(driverParameters) {
            val nodeAFuture = startNode(providedName = nodeALegalName)
            val nodeBFuture = startNode(providedName = nodeBLegalName)
            val nodeA = nodeAFuture.get()
            val nodeB = nodeBFuture.get()

            // A: create and move state to B
            val allowedNotaries = nodeA.rpc.notaryIdentities().first()
            val whitelistForState = nodeA.rpc.startFlow(::GenerateNotaryWhitelist, MyState::class.java, listOf(allowedNotaries))
                    .returnValue.get()
            val issuedState = nodeA.rpc.startFlow(::IssueMyState, whitelistForState).returnValue.get()
            val movedState = nodeA.rpc.startFlow(::MoveMyState, issuedState, nodeB.nodeInfo.legalIdentities.first(), whitelistForState)
                    .returnValue.get()
            // B: change notaries and try to move state again
            val unauthorisedNotary = nodeA.rpc.notaryIdentities().last()
            val changedState = nodeB.rpc.startFlow(::ChangeNotaries, movedState, unauthorisedNotary).returnValue.get()
            // The notary change transaction will verify, but will render the state unusable
            assertFailsWith<TransactionVerificationException.ContractRejection> {
                nodeB.rpc.startFlow(::MoveMyState, changedState, nodeA.nodeInfo.legalIdentities.first(), whitelistForState)
                        .returnValue.getOrThrow()
            }
        }
    }

    @Test
    fun `can't transition state without attaching notary whitelist`() {
        driver(driverParameters) {
            val nodeAFuture = startNode(providedName = nodeALegalName)
            val nodeBFuture = startNode(providedName = nodeBLegalName)
            val nodeA = nodeAFuture.get()
            val nodeB = nodeBFuture.get()

            // A: create and move state to B
            val allowedNotaries = nodeA.rpc.notaryIdentities().first()
            val whitelistForState = nodeA.rpc.startFlow(::GenerateNotaryWhitelist, MyState::class.java, listOf(allowedNotaries))
                    .returnValue.get()
            val issuedState = nodeA.rpc.startFlow(::IssueMyState, whitelistForState).returnValue.get()
            assertFailsWith<TransactionVerificationException.ContractRejection> {
                nodeA.rpc.startFlow(::MaliciousMoveMyState, issuedState, nodeB.nodeInfo.legalIdentities.first(), whitelistForState)
                        .returnValue.getOrThrow()
            }
        }
    }

    /** Same as [MoveMyState] but does not attach the whitelist to the move transaction. */
    @StartableByRPC
    @InitiatingFlow
    class MaliciousMoveMyState(
            private val state: StateAndRef<MyState>,
            private val recipient: Party,
            private val whitelistAttachmentId: AttachmentId
    ) : FlowLogic<StateAndRef<MyState>>() {
        @Suspendable
        override fun call(): StateAndRef<MyState> {
            val me = serviceHub.myInfo.legalIdentities.first()
            val notary = state.state.notary

            val outputState = state.state.data.withNewOwner(recipient).ownableState
            val output = state.state.copy(data = outputState as MyState)

            val tx = TransactionBuilder(notary)
                    .addInputState(state)
                    .addOutputState(output)
                    .addCommand(MyContract.Commands.Move(), me.owningKey)

            val stx = serviceHub.signInitialTransaction(tx)
            val session = initiateFlow(recipient)
            subFlow(FinalityFlow(stx, session))
            return stx.tx.outRef<MyState>(0)
        }
    }

    @InitiatedBy(MaliciousMoveMyState::class)
    class ReceiveMyState(private val counterParty: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(ReceiveFinalityFlow(counterParty))
        }
    }
}

