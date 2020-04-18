package net.corda.node.internal

import net.corda.core.contracts.*
import net.corda.core.cordapp.CordappProvider
import net.corda.core.node.NetworkParameters
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.NetworkParametersService

data class ServicesForResolutionImpl(
        override val identityService: IdentityService,
        override val cordappProvider: CordappProvider,
        override val networkParametersService: NetworkParametersService
) : ServicesForResolution {
    override val networkParameters: NetworkParameters get() = networkParametersService.lookup(networkParametersService.currentHash) ?:
            throw IllegalArgumentException("No current parameters in network parameters storage")
}
