package net.corda.node.internal

import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.utilities.contextLogger
import net.corda.node.services.api.ServiceHubInternal

class CordaTestRPCOpsImpl(val serviceHubInternal: ServiceHubInternal) : CordaTestRPCOps {
    companion object {
        private val logger = contextLogger()
    }

    init {
        logger.info("Node exposes CordaTestRPCOps")
    }

    /**
     * Returns the RPC protocol version, which is the same the node's platform Version.
     */
    override val protocolVersion: Int get() = PLATFORM_VERSION
}