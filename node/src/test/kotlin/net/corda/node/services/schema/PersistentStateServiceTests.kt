package net.corda.node.services.schema

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.node.services.api.SchemaService
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.currentDBSession
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.LogHelper
import net.corda.testing.internal.configureDatabase
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.assertj.core.api.Assertions
import org.junit.After
import org.junit.Before
import org.junit.Test
import javax.persistence.criteria.JoinType
import kotlin.test.assertEquals

class PersistentStateServiceTests {
    @Before
    fun setUp() {
        LogHelper.setLevel(PersistentStateService::class)
    }

    @After
    fun cleanUp() {
        LogHelper.reset(PersistentStateService::class)
    }

    class TestState : QueryableState {
        override fun supportedSchemas(): Iterable<MappedSchema> {
            throw UnsupportedOperationException()
        }

        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            throw UnsupportedOperationException()
        }

        override val participants: List<AbstractParty>
            get() = throw UnsupportedOperationException()
    }

    @Test
    fun `test child objects are persisted`() {
        val testSchema = TestSchema
        val schemaService = object : SchemaService {
            override val schemaOptions: Map<MappedSchema, SchemaService.SchemaOptions> = mapOf(testSchema to SchemaService.SchemaOptions())

            override fun selectSchemas(state: ContractState): Iterable<MappedSchema> = setOf(testSchema)

            override fun generateMappedObject(state: ContractState, schema: MappedSchema): PersistentState {
                val parent = TestSchema.Parent()
                parent.children.add(TestSchema.Child())
                parent.children.add(TestSchema.Child())

                val buddy = TestSchema.Buddy()
                buddy.parent = parent

                return parent
            }
        }
        val database = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(), rigorousMock(), rigorousMock(), schemaService)
        val persistentStateService = PersistentStateService(schemaService)
        database.transaction {
            val MEGA_CORP = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).party
            persistentStateService.persist(setOf(StateAndRef(TransactionState(TestState(), DummyContract.PROGRAM_ID, MEGA_CORP, constraint = AlwaysAcceptAttachmentConstraint), StateRef(SecureHash.sha256("dummy"), 0))))
            currentDBSession().flush()
            val parentRowCountResult = connection.prepareStatement("select count(*) from Parents").executeQuery()
            parentRowCountResult.next()
            val parentRows = parentRowCountResult.getInt(1)
            parentRowCountResult.close()
            val childrenRowCountResult = connection.prepareStatement("select count(*) from Children").executeQuery()
            childrenRowCountResult.next()
            val childrenRows = childrenRowCountResult.getInt(1)
            childrenRowCountResult.close()
            assertEquals(1, parentRows, "Expected one parent")
            assertEquals(2, childrenRows, "Expected two children")
        }

        database.transaction {
            val session = currentDBSession()
            val cb = session.criteriaBuilder

            val cq = cb.createQuery(TestSchema.Parent::class.java)
            val parentRoot = cq.from(TestSchema.Parent::class.java)
            val joinChild = parentRoot.join<TestSchema.Parent, TestSchema.Child>("children", JoinType.LEFT)
            val joinBuddy = parentRoot.join<TestSchema.Parent, TestSchema.Buddy>("buddy", JoinType.LEFT)

            val childPredicate = cb.equal(joinChild.get<Int?>(TestSchema.Child::childId.name), 2)
            val buddyPredicate = cb.equal(joinBuddy.get<Int?>(TestSchema.Buddy::buddyId.name), 1)
            val predicates = cb.or(childPredicate, buddyPredicate)

            cq.where(predicates)
            val results =session.createQuery(cq).resultList
            Assertions.assertThat(results).hasSize(1)
        }

        database.close()
    }
}