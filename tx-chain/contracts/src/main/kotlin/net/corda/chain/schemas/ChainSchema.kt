package net.corda.chain.schemas


import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.util.*
import javax.persistence.*

object ChainContractsSchema

object ChainContractsSchemaV1 : MappedSchema (
        schemaFamily = ChainContractsSchema::class.java,
        version = 1,
        mappedTypes = listOf(PersistentChainState::class.java)
) {
    @Entity
    @Table(name = "chain_state")
    data class PersistentChainState (
        @Column(name = "identifier", nullable = false)
        val id: UUID,
        @Column(name = "value", nullable = false)
        var amount: Long
    ) : PersistentState() {
            constructor () : this (UUID.randomUUID(), 0)
    }

}