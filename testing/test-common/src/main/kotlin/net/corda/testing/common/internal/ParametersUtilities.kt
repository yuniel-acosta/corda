package net.corda.testing.common.internal

import net.corda.core.identity.Party
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.utilities.days
import java.security.PublicKey
import java.time.Duration
import java.time.Instant

@JvmOverloads
fun testNetworkParameters(
        minimumPlatformVersion: Int = 1,
        modifiedTime: Instant = Instant.now(),
        maxMessageSize: Int = 10485760,
        // TODO: Make this configurable and consistence across driver, bootstrapper, demobench and NetworkMapServer
        maxTransactionSize: Int = maxMessageSize * 50,
        epoch: Int = 1,
        eventHorizon: Duration = 30.days
): NetworkParameters {
    return NetworkParameters(
            minimumPlatformVersion = minimumPlatformVersion,
            maxMessageSize = maxMessageSize,
            maxTransactionSize = maxTransactionSize,
            modifiedTime = modifiedTime,
            epoch = epoch,
            eventHorizon = eventHorizon
    )
}

