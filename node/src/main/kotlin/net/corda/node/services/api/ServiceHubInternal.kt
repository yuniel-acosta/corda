package net.corda.node.services.api

import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.*
import net.corda.core.internal.concurrent.OpenFuture
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.StateMachineTransactionMapping
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.NetworkMapCacheBase
import net.corda.core.utilities.contextLogger
import net.corda.node.internal.InitiatedFlowFactory
import net.corda.node.internal.cordapp.CordappProviderInternal
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.network.NetworkMapUpdater
import net.corda.node.services.statemachine.ExternalEvent
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.nodeapi.internal.persistence.CordaPersistence
import java.security.PublicKey
import java.util.*

interface NetworkMapCacheInternal : NetworkMapCache, NetworkMapCacheBase {
    override val nodeReady: OpenFuture<Void?>

    val allNodeHashes: List<SecureHash>

    fun getNodeByHash(nodeHash: SecureHash): NodeInfo?

    /** Find nodes from the [PublicKey] toShortString representation.
     * This is used for Artemis bridge lookup process. */
    fun getNodesByOwningKeyIndex(identityKeyIndex: String): List<NodeInfo>

    /** Adds a node to the local cache (generally only used for adding ourselves). */
    fun addNode(node: NodeInfo)

    fun addNodes(nodes: List<NodeInfo>)

    /** Removes a node from the local cache. */
    fun removeNode(node: NodeInfo)
}

interface ServiceHubInternal : ServiceHubCoreInternal {
    companion object {
        private val log = contextLogger()
    }

    val monitoringService: MonitoringService
    val schemaService: SchemaService
    override val networkMapCache: NetworkMapCacheInternal
    val auditService: AuditService
    val rpcFlows: List<Class<out FlowLogic<*>>>
    val networkService: MessagingService
    val database: CordaPersistence
    val configuration: NodeConfiguration
    val nodeProperties: NodePropertiesStore
    val networkMapUpdater: NetworkMapUpdater
    override val cordappProvider: CordappProviderInternal

    fun getFlowFactory(initiatingFlowClass: Class<out FlowLogic<*>>): InitiatedFlowFactory<*>?
    val cacheFactory: NamedCacheFactory
}

interface FlowStarter {

    /**
     * Starts an already constructed flow. Note that you must be on the server thread to call this method. This method
     * just synthesizes an [ExternalEvent.ExternalStartFlowEvent] and calls the method below.
     * @param context indicates who started the flow, see: [InvocationContext].
     */
    fun <T> startFlow(logic: FlowLogic<T>, context: InvocationContext): CordaFuture<FlowStateMachine<T>>

    /**
     * Starts a flow as described by an [ExternalEvent.ExternalStartFlowEvent].  If a transient error
     * occurs during invocation, it will re-attempt to start the flow.
     */
    fun <T> startFlow(event: ExternalEvent.ExternalStartFlowEvent<T>): CordaFuture<FlowStateMachine<T>>

    /**
     * Will check [logicType] and [args] against a whitelist and if acceptable then construct and initiate the flow.
     * Note that you must be on the server thread to call this method. [context] points how flow was started,
     * See: [InvocationContext].
     *
     * @throws net.corda.core.flows.IllegalFlowLogicException or IllegalArgumentException if there are problems with the
     * [logicType] or [args].
     */
    fun <T> invokeFlowAsync(
            logicType: Class<out FlowLogic<T>>,
            context: InvocationContext,
            vararg args: Any?): CordaFuture<FlowStateMachine<T>>
}

interface StartedNodeServices : ServiceHubInternal, FlowStarter