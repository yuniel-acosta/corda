package net.corda.bn.contracts

import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException

open class MembershipContract : Contract {

    companion object {
        private const val CONTRACT_NAME = "net.corda.bn.contracts.MembershipContract"
    }

    open class Commands : CommandData, TypeOnlyCommandData() {
        class Request : Commands()
        class Activate : Commands()
        class Suspend : Commands()
        class Revoke : Commands()
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val input = if (tx.inputStates.isNotEmpty()) tx.inputs.single() else null
        val inputState = input?.state?.data as? MembershipState
        val output = if (tx.outputStates.isNotEmpty()) tx.outputs.single() else null
        val outputState = output?.data as? MembershipState

        requireThat {
            input?.apply {
                "Input state has to be validated by ${contractName()}" using (input.state.contract == contractName())
            }
            inputState?.apply {
                "Modified timestamp should be greater or equal to issued timestamp" using (inputState.issued <= inputState.modified)
            }
            outputState?.apply {
                "Modified timestamp should be greater or equal to issued timestamp" using (outputState.issued <= outputState.modified)
            }
            output?.apply {
                "Output state has to be validated by ${contractName()}" using (output.contract == contractName())
            }
            if (inputState != null && outputState != null) {
                "Input and output state should have same Corda identity" using (inputState.identity == outputState.identity)
                "Input and output state should have same network IDs" using (inputState.networkId == outputState.networkId)
                "Input and output state should have same issued timestamps" using (inputState.issued == outputState.issued)
                "Output state's modified timestamp should be greater or equal than input's" using (inputState.modified <= outputState.modified)
                "Input and output state should have same linear IDs" using (inputState.linearId == outputState.linearId)
            }
        }

        when (command.value) {
            is Commands.Request -> verifyRequest(tx, command, tx.outputsOfType<MembershipState>().single())
            is Commands.Activate -> verifyActivate(tx, command, tx.inputsOfType<MembershipState>().single(), tx.outputsOfType<MembershipState>().single())
            is Commands.Suspend -> verifySuspend(tx, command, tx.inputsOfType<MembershipState>().single(), tx.outputsOfType<MembershipState>().single())
            is Commands.Revoke -> verifyRevoke(tx, command, tx.inputsOfType<MembershipState>().single())
            else -> throw IllegalArgumentException("Unsupported command ${command.value}")
        }
    }

    open fun contractName() = CONTRACT_NAME

    open fun verifyRequest(tx: LedgerTransaction, command: CommandWithParties<Commands>, outputMembership: MembershipState) = requireThat {
        "Membership request transaction shouldn't contain any inputs" using (tx.inputs.isEmpty())
        "Membership request transaction should contain output state in PENDING status" using (outputMembership.isPending())
    }

    open fun verifyActivate(
            tx: LedgerTransaction,
            command: CommandWithParties<Commands>,
            inputMembership: MembershipState,
            outputMembership: MembershipState
    ) = requireThat {
        "Input state of membership activation transaction shouldn't be already active" using (!inputMembership.isActive())
        "Output state of membership activation transaction should be active" using (outputMembership.isActive())
        "Input and output state of membership activation transaction should only have different status and modified timestamp field" using (
                inputMembership.copy(status = MembershipStatus.ACTIVE, modified = outputMembership.modified) == outputMembership)
    }

    open fun verifySuspend(
            tx: LedgerTransaction,
            command: CommandWithParties<Commands>,
            inputMembership: MembershipState,
            outputMembership: MembershipState
    ) = requireThat {
        "Input state of membership suspension transaction shouldn't be already suspended" using (!inputMembership.isSuspended())
        "Output state of membership suspension transaction should be suspended" using (outputMembership.isSuspended())
        "Input and output state of membership suspension transaction should only have different status and modified timestamp field" using (
                inputMembership.copy(status = MembershipStatus.SUSPENDED, modified = outputMembership.modified) == outputMembership)
    }

    open fun verifyRevoke(
            tx: LedgerTransaction,
            command: CommandWithParties<Commands>,
            inputMembership: MembershipState
    ) = requireThat {
        "Membership revocation transaction shouldn't contain any outputs" using (tx.outputs.isEmpty())
    }
}