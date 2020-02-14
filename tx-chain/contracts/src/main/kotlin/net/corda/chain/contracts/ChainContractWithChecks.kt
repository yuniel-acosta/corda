package net.corda.chain.contracts

import net.corda.chain.states.ChainStateAllParticipants
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

// Contract and state.
class ChainContractWithChecks: Contract {
    companion object {
        @JvmStatic
        val ID = "net.corda.chain.contracts.ChainContractWithChecks"
    }

    // Command.
    interface Commands : CommandData {
        class ConsumeState : TypeOnlyCommandData(), Commands
        class ReIssueState : TypeOnlyCommandData(), Commands
        class Issue : TypeOnlyCommandData(), Commands
        class Move : TypeOnlyCommandData(), Commands
    }

    // Contract code.
    override fun verify(tx: LedgerTransaction) = requireThat {
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.ConsumeState -> {
                //"There must be one output" using(tx.outputs.size == 1)
                //val state = tx.outputsOfType<ChainState>().single()
            }
            is Commands.ReIssueState -> {
                //"There must be one output" using(tx.outputs.size == 1)
                //val state = tx.outputsOfType<ChainState>().single()
            }
            is Commands.Issue -> {
                //"There must be one output" using(tx.outputs.size == 1)
                //val state = tx.outputsOfType<ChainState>().single()
            }
            is Commands.Move -> {
//                val inputState = tx.inputsOfType<ChainStateAllParticipants>().single()
                // Make sure that the signers of the commands and the input states participants are the same
                // The signers of the command of the transaction of the input state
                // should be the same as the signers of the output state?
//                val signers = tx.commands.single().signers
//                val inputStateParticipants = inputState.participants.map { it.owningKey }
//                "Make sure that the signers of the commands and the input states participants are the same" using
//                        (signers == inputStateParticipants)

                // for each state, input state participants == output state participants
//                val outputState = tx.outputsOfType<ChainStateAllParticipants>().single()
//                "The participants of the input state should be the same as the participants of the output state" using
//                        (inputState.participants.toSet() == outputState.participants.toSet())
            }
        }
    }
}
