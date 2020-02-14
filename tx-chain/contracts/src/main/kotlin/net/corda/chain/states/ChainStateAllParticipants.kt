package net.corda.chain.states


import net.corda.chain.contracts.ChainContractWithChecks
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party


@BelongsToContract(ChainContractWithChecks::class)
data class ChainStateAllParticipants (
        val partyA: Party,
        val partyB: Party,
        val me: Party,
        val id: UniqueIdentifier
) : LinearState {

    override val linearId = id

    override val participants: List<AbstractParty> get() = listOf(partyA, partyB, me)

}