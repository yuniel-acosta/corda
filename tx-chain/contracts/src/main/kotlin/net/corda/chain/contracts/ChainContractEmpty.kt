package net.corda.chain.contracts


import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

// Contract and state.
class ChainContractEmpty: Contract {
    companion object {
        @JvmStatic
        val ID = "net.corda.chain.contracts.ChainContractEmpty"
    }

    // Command.
    interface Commands : CommandData {
        class Issue : TypeOnlyCommandData(), Commands
        class Move : TypeOnlyCommandData(), Commands
    }

    // Contract code.
    override fun verify(tx: LedgerTransaction) = requireThat {
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.Issue -> {

            }
            is Commands.Move -> {

            }
        }
    }
}
