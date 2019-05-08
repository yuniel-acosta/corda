package com.r3.corda.sgx.poc

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

//@BelongsToContract(TheContract::class)
data class SecretToken(val what: String, val owner: Party): ContractState {
    override val participants: List<AbstractParty> get() = listOf(owner)
}
