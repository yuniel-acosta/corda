package net.corda.node.services.api

import net.corda.core.schemas.MappedSchema

//DOCSTART SchemaService
/**
 * A configuration and customisation point for Object Relational Mapping of contract state objects.
 */
interface SchemaService {
    /**
     * All available schemas in this node
     */
    val schemas: Set<MappedSchema>
}
//DOCEND SchemaService
