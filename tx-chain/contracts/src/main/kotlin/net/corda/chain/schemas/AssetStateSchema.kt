package net.corda.chain.schemas


import net.corda.chain.states.SnippingStatus
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.util.*
import javax.persistence.*

object AssetStateSchema

object AssetStateSchemaV1 : MappedSchema (
        schemaFamily = AssetStateSchema::class.java,
        version = 1,
        mappedTypes = listOf(PersistentAssetState::class.java)
) {
    @Entity
    @Table(name = "asset_state", uniqueConstraints = [
        UniqueConstraint(name = "linearId_and_status_str_constraint", columnNames = ["identifier", "snipping_status", "str"])
    ], indexes = [
        Index(name = "assetId_idx", columnList = "identifier")
    ]
    )
    data class PersistentAssetState (
        @Column(name = "identifier", nullable = false)
        val id: UUID,
        @Column(name = "snipping_status", nullable = false)
        var status: SnippingStatus,
        @Column(name = "str", nullable = false)
        var str: String
    ) : PersistentState()

}