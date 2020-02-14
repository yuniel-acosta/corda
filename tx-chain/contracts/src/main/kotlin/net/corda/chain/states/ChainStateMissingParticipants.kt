package net.corda.chain.states


import net.corda.chain.contracts.ChainContractEmpty
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party


@BelongsToContract(ChainContractEmpty::class)
data class ChainStateMissingParticipants (
        val partyA: Party,
        val partyB: Party,
        val me: Party,
        val id: UniqueIdentifier
) : LinearState {

    override val linearId = id

    // partyB -> Is missing deliberately
    override val participants: List<AbstractParty> get() = listOf(partyA, me)

}