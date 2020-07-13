package net.corda.bn.custom.contracts

import net.corda.bn.contracts.MembershipContract
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class CustomMembershipContract : MembershipContract(), Contract {

    companion object {
        const val CONTRACT_NAME = "net.corda.bn.custom.contracts.CustomMembershipContract"
    }

    override fun contractName() = CONTRACT_NAME

    override fun verifyRequest(tx: LedgerTransaction, command: CommandWithParties<Commands>, outputMembership: MembershipState) {
        super.verifyRequest(tx, command, outputMembership)
        requireThat {
            "Output membership must have non trivial business identity since it is important for BNO for activation decision" using (outputMembership.identity.businessIdentity != null)
        }
    }
}