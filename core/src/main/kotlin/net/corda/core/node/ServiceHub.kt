package net.corda.core.node


import net.corda.core.DoNotImplement
import net.corda.core.contracts.*
import net.corda.core.cordapp.CordappContext
import net.corda.core.cordapp.CordappProvider
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.TransactionSignature
import net.corda.core.node.services.*
import net.corda.core.node.services.diagnostics.DiagnosticsService
import net.corda.core.serialization.SerializeAsToken
import java.security.PublicKey
import java.sql.Connection
import java.time.Clock
import java.util.function.Consumer
import javax.persistence.EntityManager

/**
 * Subset of node services that are used for loading transactions from the wire into fully resolved, looked up
 * forms ready for verification.
 */

@DoNotImplement
interface ServicesForResolution {
    /**
     * An identity service maintains a directory of parties by their associated distinguished name/public keys and thus
     * supports lookup of a party given its key, or name. The service also manages the certificates linking confidential
     * identities back to the well known identity (i.e. the identity in the network map) of a party.
     */
    val identityService: IdentityService

    /** Provides access to anything relating to cordapps including contract attachment resolution and app context */
    val cordappProvider: CordappProvider

    /** Provides access to historical network parameters that are used in transaction resolution. */
    val networkParametersService: NetworkParametersService

    /** Returns the network parameters the node is operating under. */
    val networkParameters: NetworkParameters
}

/**
 * A service hub is the starting point for most operations you can do inside the node. You are provided with one
 * when a class annotated with [CordaService] is constructed, and you have access to one inside flows. Most RPCs
 * simply forward to the services found here after some access checking.
 *
 * The APIs are organised roughly by category, with a few very important top level APIs available on the ServiceHub
 * itself. Inside a flow, it's safe to keep a reference to services found here on the stack: checkpointing will do the
 * right thing (it won't try to serialise the internals of the service).
 *
 * In unit test environments, some of those services may be missing or mocked out.
 */

interface ServiceHub : ServicesForResolution {
    // NOTE: Any services exposed to flows (public view) need to implement [SerializeAsToken] or similar to avoid
    // their internal state from being serialized in checkpoints.

    /**
     * The key management service is responsible for storing and using private keys to sign things. An
     * implementation of this may, for example, call out to a hardware security module that enforces various
     * auditing and frequency-of-use requirements.
     *
     * You don't normally need to use this directly. If you have a [TransactionBuilder] and wish to sign it to
     * get a [SignedTransaction], look at [signInitialTransaction].
     */
    val keyManagementService: KeyManagementService

    /**
     * A network map contains lists of nodes on the network along with information about their identity keys, services
     * they provide and host names or IP addresses where they can be connected to. The cache wraps around a map fetched
     * from an authoritative service, and adds easy lookup of the data stored within it. Generally it would be initialised
     * with a specified network map service, which it fetches data from and then subscribes to updates of.
     */
    val networkMapCache: NetworkMapCache

    /**
     * The [DiagnosticsService] provides diagnostic level information about the node, including the current version of the node, and the
     * CorDapps currently installed on the node. Note that this information should be used to provide diagnostics only, and no functional
     * decisions should be made based on this.
     */
    val diagnosticsService: DiagnosticsService

    /**
     * A [Clock] representing the node's current time. This should be used in preference to directly accessing the
     * clock so the current time can be controlled during unit testing.
     */
    val clock: Clock

    /** The [NodeInfo] object corresponding to our own entry in the network map. */
    val myInfo: NodeInfo

    /**
     * Return the singleton instance of the given Corda service type. This is a class that is annotated with
     * [CordaService] and will have automatically been registered by the node.
     * @throws IllegalArgumentException If [type] is not annotated with [CordaService] or if the instance is not found.
     */
    fun <T : SerializeAsToken> cordaService(type: Class<T>): T

    /**
     * Exposes a JDBC connection (session) object using the currently configured database.
     * Applications can use this to execute arbitrary SQL queries (native, direct, prepared, callable)
     * against its Node database tables (including custom contract tables defined by extending
     * [net.corda.core.schemas.QueryableState]).
     *
     * When used within a flow, this session automatically forms part of the enclosing flow transaction boundary,
     * and thus queryable data will include everything committed as of the last checkpoint.
     *
     * @throws IllegalStateException if called outside of a transaction.
     * @return A [Connection]
     */
    fun jdbcSession(): Connection

    /**
     * Exposes the Java Persistence API (JPA) to flows via a restricted [EntityManager]. This method can be used to
     * persist and query entities which inherit from [MappedSchema]. This is particularly useful if off-ledger data
     * needs to be kept in conjunction with on-ledger state data.
     *
     * NOTE: Suspendable flow operations such as send, receive, subFlow and sleep, cannot be called within the lambda.
     *
     * @param block a lambda function with access to an [EntityManager].
     */
    fun <T : Any?> withEntityManager(block: EntityManager.() -> T): T

    /**
     * Exposes the Java Persistence API (JPA) to flows via a restricted [EntityManager]. This method can be used to
     * persist and query entities which inherit from [MappedSchema]. This is particularly useful if off-ledger data
     * needs to be kept in conjunction with on-ledger state data.
     *
     * NOTE: Suspendable flow operations such as send, receive, subFlow and sleep, cannot be called within the lambda.
     *
     * @param block a lambda function with access to an [EntityManager].
     */
    fun withEntityManager(block: Consumer<EntityManager>)

    /**
     * Allows the registration of a callback that may inform services when the app is shutting down.
     *
     * The intent is to allow the cleaning up of resources - e.g. releasing ports.
     *
     * You should not rely on this to clean up executing flows - that's what quasar is for.
     *
     * Please note that the shutdown handler is not guaranteed to be called. In production the node process may crash,
     * be killed by the operating system and other forms of fatal termination may occur that result in this code never
     * running. So you should use this functionality only for unit/integration testing or for code that can optimise
     * this shutdown e.g. by cleaning up things that would otherwise trigger a slow recovery process next time the
     * node starts.
     */
    fun registerUnloadHandler(runOnStop: () -> Unit)

    /**
     * See [CordappProvider.getAppContext]
     */
    fun getAppContext(): CordappContext = cordappProvider.getAppContext()
}
