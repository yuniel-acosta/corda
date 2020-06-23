package net.corda.bn.contracts

import net.corda.bn.states.MembershipState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException

/**
 * Contract that verifies an evolution of [MembershipState].
 */
open class MembershipContract : Contract {

    companion object {
        const val CONTRACT_NAME = "net.corda.bn.contracts.MembershipContract"
    }

    open class Commands : CommandData, TypeOnlyCommandData() {
        class Request : Commands()
        class Activate : Commands()
        class Suspend : Commands()
        class Revoke : Commands()
        class ModifyPermissions : Commands()
    }

    @Suppress("ComplexMethod")
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
                "Input state's modified timestamp should be greater or equal to issued timestamp" using (inputState.issued <= inputState.modified)
            }
            outputState?.apply {
                "Output state's modified timestamp should be greater or equal to issued timestamp" using (outputState.issued <= outputState.modified)
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
                "Input and output state should have same participants" using (inputState.participants.toSet() == outputState.participants.toSet())
            }
        }

        when (command.value) {
            is Commands.Request -> verifyRequest(tx, command, outputState!!)
            is Commands.Activate -> verifyActivate(tx, command, inputState!!, outputState!!)
            is Commands.Suspend -> verifySuspend(tx, command, inputState!!, outputState!!)
            is Commands.Revoke -> verifyRevoke(tx, command, inputState!!)
            is Commands.ModifyPermissions -> verifyModifyPermissions(tx, command, inputState!!, outputState!!)
            else -> throw IllegalArgumentException("Unsupported command ${command.value}")
        }
    }

    open fun contractName() = CONTRACT_NAME

    open fun verifyRequest(tx: LedgerTransaction, command: CommandWithParties<Commands>, outputMembership: MembershipState) = requireThat {
        "Membership request transaction shouldn't contain any inputs" using (tx.inputs.isEmpty())
        "Membership request transaction should contain output state in PENDING status" using (outputMembership.isPending())
        "Membership request transaction should issue membership with empty roles set" using (outputMembership.roles.isEmpty())
    }

    open fun verifyActivate(
            tx: LedgerTransaction,
            command: CommandWithParties<Commands>,
            inputMembership: MembershipState,
            outputMembership: MembershipState
    ) = requireThat {
        "Input state of membership activation transaction shouldn't be already active" using (!inputMembership.isActive())
        "Output state of membership activation transaction should be active" using (outputMembership.isActive())
        "Input and output state of membership activation transaction should have same roles set" using (inputMembership.roles == outputMembership.roles)
    }

    open fun verifySuspend(
            tx: LedgerTransaction,
            command: CommandWithParties<Commands>,
            inputMembership: MembershipState,
            outputMembership: MembershipState
    ) = requireThat {
        "Input state of membership suspension transaction shouldn't be already suspended" using (!inputMembership.isSuspended())
        "Output state of membership suspension transaction should be suspended" using (outputMembership.isSuspended())
        "Input and output state of membership suspension transaction should have same roles set" using (inputMembership.roles == outputMembership.roles)
    }

    open fun verifyRevoke(
            tx: LedgerTransaction,
            command: CommandWithParties<Commands>,
            inputMembership: MembershipState
    ) = requireThat {
        "Membership revocation transaction shouldn't contain any outputs" using (tx.outputs.isEmpty())
    }

    open fun verifyModifyPermissions(
            tx: LedgerTransaction,
            command: CommandWithParties<Commands>,
            inputMembership: MembershipState,
            outputMembership: MembershipState
    ) = requireThat {
        "Input and output state of membership permissions modification transaction should have same status" using (inputMembership.status == outputMembership.status)
        "Membership permissions modification transaction can only be performed on active or suspended state" using (inputMembership.isActive() || inputMembership.isSuspended())
        "Input and output state of membership permissions modification transaction should have different set of roles" using (inputMembership.roles != outputMembership.roles)
    }
}