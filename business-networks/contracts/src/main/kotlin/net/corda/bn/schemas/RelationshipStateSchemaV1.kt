package net.corda.bn.schemas

import net.corda.bn.states.RelationshipState
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

@CordaSerializable
object RelationshipStateSchemaV1 : MappedSchema(schemaFamily = RelationshipState::class.java, version = 1, mappedTypes = listOf(PersistentRelationshipState::class.java)) {
    @Entity
    @Table(name = "relationship_state")
    class PersistentRelationshipState(
            @Column(name = "membership_id")
            val membershipId: String
    ) : PersistentState()
}