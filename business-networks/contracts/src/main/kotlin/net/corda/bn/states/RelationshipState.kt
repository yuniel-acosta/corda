package net.corda.bn.states

import net.corda.bn.contracts.RelationshipContract
import net.corda.bn.schemas.RelationshipStateSchemaV1
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable

@BelongsToContract(RelationshipContract::class)
data class RelationshipState(val membershipId: UniqueIdentifier, val groups: Set<BNGroup>) : QueryableState {

    override val participants: List<AbstractParty> = listOf()

    override fun generateMappedObject(schema: MappedSchema) = when (schema) {
        is RelationshipStateSchemaV1 -> RelationshipStateSchemaV1.PersistentRelationshipState(
                membershipId = membershipId.toString()
        )
        else -> throw IllegalArgumentException("Unrecognised schema $schema")
    }

    override fun supportedSchemas() = listOf(RelationshipStateSchemaV1)
}

@CordaSerializable
data class BNGroup(val id: UniqueIdentifier, val name: String? = null)