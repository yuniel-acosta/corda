package net.corda.experimental.issuerwhitelist.contract

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandAndState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.OwnableState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class MyContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        // Each contract using RestrictedNotaryState(s) has to manually invoke the verification logic.
        NotaryWhitelistVerifier.verify(tx)
    }

    interface Commands : CommandData {
        class Move : TypeOnlyCommandData()
        class Issue : TypeOnlyCommandData()
    }
}

@BelongsToContract(MyContract::class)
data class MyState(
        override val issuerKey: PublicKey,
        override val owner: AbstractParty,
        override val participants: List<AbstractParty>
) : OwnableState, RestrictedNotaryState {
    override fun withNewOwner(newOwner: AbstractParty): CommandAndState {
        return CommandAndState(MyContract.Commands.Move(), copy(owner = newOwner, participants = listOf(newOwner)))
    }
}