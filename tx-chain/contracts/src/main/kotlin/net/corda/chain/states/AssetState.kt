package net.corda.chain.states


import net.corda.chain.contracts.EmptyContract
import net.corda.chain.schemas.AssetStateSchemaV1
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class SnippingStatus {
    ISSUE,
    REISSUE
}

@BelongsToContract(EmptyContract::class)
data class AssetState (
        val status: SnippingStatus,
        val str: String,
        val id: UniqueIdentifier,
        val parties: List<Party>
) : LinearState, QueryableState {

    override val linearId: UniqueIdentifier get() = id

    override val participants: List<AbstractParty> get() = parties

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        if (schema is AssetStateSchemaV1) {
            return AssetStateSchemaV1.PersistentAssetState (
                    status = status,
                    str = str,
                    id = id.id
            )
        } else {
            throw IllegalStateException("Cannot construct instance of ${this.javaClass} from Schema: $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> {
        return listOf(AssetStateSchemaV1)
    }

}