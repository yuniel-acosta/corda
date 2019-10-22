package com.r3.corda.sgx.poc.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.lang.Integer.min

class CoinContract : Contract {
    companion object {
        val ID = "com.r3.corda.sgx.poc.contracts.CoinContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Command>()

        when (command.value) {
            is Command.Issue -> requireThat {
                "No inputs" using (tx.inputs.isEmpty())
                val outputs = tx.outputsOfType<Coin>()
                "All coins in output should have the same issuer" using (outputs.map { it.issuer }.toSet().size == 1)
                "Issuer and owner should match" using (outputs.all { it.issuer == it.owner })
            }

            is Command.Transfer -> requireThat {
                val inputsCountByIssuer = tx.inputsOfType<Coin>().groupBy { it.issuer }.mapValues { it.value.size }
                val outputsCountByIssuer = tx.outputsOfType<Coin>().groupBy { it.issuer }.mapValues { it.value.size }
                "Total amount of coints by issuer id must be conserved" using (inputsCountByIssuer == outputsCountByIssuer)
            }
        }

        val requiredSigners = (tx.inputsOfType<Coin>() + tx.outputsOfType<Coin>()).map {
            when (command.value) {
                is Command.Issue -> it.issuer.owningKey
                is Command.Transfer -> it.owner.owningKey
            }
        }.toSet()

        requireThat { "Missing signer" using (command.signers.containsAll(requiredSigners)) }

        logTx(tx)
    }

    /**
     * This contract only implements one command, Create.
     */
    sealed class Command : CommandData {
        class Issue: Command()
        class Transfer : Command()
    }


    fun logTx(tx: LedgerTransaction) {
            //
            if (System.getProperty("sgx.mode") != null) {
                val str = tx.toString()
                var offset = 0;
                while (offset < str.length) {
                    val nextOffset = min(offset + 512, str.length - 1)
                    System.out.print(str.substring(offset, nextOffset + 1))
                    offset = nextOffset + 1
                }
                System.out.println("")
            }
    }

}
