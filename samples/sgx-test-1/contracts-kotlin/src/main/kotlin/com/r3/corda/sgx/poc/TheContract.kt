package com.r3.corda.sgx.poc

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

/**
 * A implementation of a basic smart contract in Corda.
 *
 * This contract enforces rules regarding the creation of a valid [SecretDeal], which in turn encapsulates an [SecretDeal].
 *
 * For a new [SecretDeal] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [SecretDeal].
 * - An Create() command with the public keys of both the lender and the borrower.
 *
 * All contracts must sub-class the [Contract] interface.
 */
class TheContract : Contract {
    companion object {
        val ID = "com.r3.corda.sgx.poc.TheContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<TheCommand>().value
        when (command) {
            is TheCommand.Issue -> requireThat {
                "No inputs" using (tx.inputs.isEmpty())
                "Only one output state should be created." using (tx.outputs.size == 1)
                val out = tx.outputsOfType<SecretToken>().single()
                "Non empty clause" using (out.what.isNotEmpty())
            }

            is TheCommand.Transfer -> requireThat {
                "Single input token" using (tx.inputs.size == 1)
                "Single output token" using (tx.outputs.size == 1)
                val input = tx.inputsOfType<SecretToken>().single()
                val out = tx.outputsOfType<SecretToken>().single()
                "Same token ID" using (input.what == out.what)
            }
        }
    }

    /**
     * This contract only implements one command, Create.
     */
    sealed class TheCommand : CommandData {
        class Issue: TheCommand()
        class Transfer : TheCommand()
    }
}
