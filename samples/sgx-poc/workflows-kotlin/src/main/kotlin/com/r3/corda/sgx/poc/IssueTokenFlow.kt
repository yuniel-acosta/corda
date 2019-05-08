package com.r3.corda.sgx.poc.internal

import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sgx.poc.SecretToken
import com.r3.corda.sgx.poc.TheContract
import com.r3.corda.sgx.poc.internal.TxVerifyingEnclaveService
import net.corda.core.contracts.Command
import net.corda.core.crypto.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.core.utilities.unwrap

@InitiatingFlow
@StartableByRPC
class IssueTokenFlow() : FlowLogic<SignedTransaction>() {
    /**
     * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
     * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
     */
    companion object {
        object GENERATING_TRANSACTION : Step("Generating transaction")
        object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
        object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    /**
     * The flow logic is encapsulated within the call() method.
     */
    @Suspendable
    override fun call(): SignedTransaction {
        // Obtain a reference to the notary we want to use.
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        // Stage 1.
        progressTracker.currentStep = GENERATING_TRANSACTION
        // Generate an unsigned transaction.
        val secretToken = SecretToken("deal", serviceHub.myInfo.legalIdentities.first())
        val txCommand = Command(TheContract.TheCommand.Issue(), secretToken.owner.owningKey)
        val txBuilder = TransactionBuilder(notary)
                .addOutputState(secretToken, TheContract.ID)
                .addCommand(txCommand)

        // Stage 2.
        progressTracker.currentStep = VERIFYING_TRANSACTION
        // Verify that the transaction is valid.
        txBuilder.verify(serviceHub)

        // Stage 3.
        progressTracker.currentStep = SIGNING_TRANSACTION
        // Sign the transaction.
        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

        // Ask verifying enclave to sign and check
        serviceHub.cordaService(TxVerifyingEnclaveService::class.java)
                .invoke(partSignedTx.tx)
                .validate(TxVerifyingEnclaveService.enclaveId)

        progressTracker.currentStep = GATHERING_SIGS

        // Stage 5.
        progressTracker.currentStep = FINALISING_TRANSACTION
        val notarySig = subFlow(NotaryFlow.Client(partSignedTx))
        val fullySignedTx = partSignedTx + notarySig

        // Notarise and record the transaction in both parties' vaults.
        return fullySignedTx + notarySig
    }
}

