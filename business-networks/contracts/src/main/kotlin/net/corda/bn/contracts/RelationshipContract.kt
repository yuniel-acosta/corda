package net.corda.bn.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

open class RelationshipContract : Contract {

    companion object {
        const val CONTRACT_NAME = "net.corda.bn.contracts.RelationshipContract"
    }

    open class Commands : CommandData {
        class Issue : Commands()
        class Modify : Commands()
        class Exit : Commands()
    }

    override fun verify(tx: LedgerTransaction) {}

    open fun contractName() = CONTRACT_NAME
}