package com.r3.corda.sgx.poc.contracts

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

@BelongsToContract(CoinContract::class)
data class Coin(
        val owner: Party,
        val issuer: Party
): ContractState {

    override val participants: List<AbstractParty> = listOf(owner)

}
