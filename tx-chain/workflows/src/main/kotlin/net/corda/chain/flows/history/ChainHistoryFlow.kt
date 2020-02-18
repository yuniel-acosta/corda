package net.corda.chain.flows.history

import net.corda.chain.states.AssetState
import net.corda.core.flows.FlowLogic
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.node.services.vault.SortAttribute

class ChainHistoryFlow () : FlowLogic <Int> (){

    override fun call() : Int {

        val vaultCriteria = QueryCriteria.VaultQueryCriteria (status = Vault.StateStatus.ALL)
        val sortAttribute = SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME)
        val sorter = Sort(setOf(Sort.SortColumn(sortAttribute, Sort.Direction.ASC)))

        val queryResult = serviceHub.vaultService
                .queryBy (contractStateType = AssetState::class.java,
                        criteria = vaultCriteria,
                        sorting = sorter
                )

        val states = queryResult.states
        println("ChainHistoryFlow")
        states.forEach { println("states = ${it.state.data}") }
        queryResult.statesMetadata.forEach { println("states metadata = $it") }
        return states.size

    }
}
