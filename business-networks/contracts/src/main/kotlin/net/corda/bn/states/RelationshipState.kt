package net.corda.bn.states

import net.corda.bn.contracts.RelationshipContract
import net.corda.bn.schemas.RelationshipStateSchemaV1
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import java.time.Instant

@BelongsToContract(RelationshipContract::class)
data class RelationshipState(
        val membershipId: UniqueIdentifier,
        val groups: Map<String, Group>,
        val issued: Instant = Instant.now(),
        val modified: Instant = issued,
        override val participants: List<AbstractParty>
) : ContractState, QueryableState {

    override fun generateMappedObject(schema: MappedSchema) = when (schema) {
        is RelationshipStateSchemaV1 -> RelationshipStateSchemaV1.PersistentRelationshipState(membershipId = membershipId.toString())
        else -> throw IllegalArgumentException("Unrecognised schema $schema")
    }

    override fun supportedSchemas() = listOf(RelationshipStateSchemaV1)
}

@CordaSerializable
data class Group(val participants: List<Party>)