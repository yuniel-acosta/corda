package net.corda.core.node

import net.corda.core.CordaRuntimeException

import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.time.Duration
import java.time.Instant

// DOCSTART 1
/**
 * Network parameters are a set of values that every node participating in the zone needs to agree on and use to
 * correctly interoperate with each other.
 *
 * @property minimumPlatformVersion Minimum version of Corda platform that is required for nodes in the network.
 * @property notaries List of well known and trusted notary identities with information on validation type.
 * @property maxMessageSize This is currently ignored. However, it will be wired up in a future release.
 * @property maxTransactionSize Maximum permitted transaction size in bytes.
 * @property modifiedTime ([AutoAcceptable]) Last modification time of network parameters set.
 * @property epoch ([AutoAcceptable]) Version number of the network parameters. Starting from 1, this will always increment on each new set
 * of parameters.
 * @property whitelistedContractImplementations ([AutoAcceptable]) List of whitelisted jars containing contract code for each contract class.
 *  This will be used by [net.corda.core.contracts.WhitelistedByZoneAttachmentConstraint].
 *  [You can learn more about contract constraints here](https://docs.corda.net/api-contract-constraints.html).
 * @property packageOwnership ([AutoAcceptable]) List of the network-wide java packages that were successfully claimed by their owners.
 * Any CorDapp JAR that offers contracts and states in any of these packages must be signed by the owner.
 * @property eventHorizon Time after which nodes will be removed from the network map if they have not been seen
 * during this period
 */

@CordaSerializable
data class NetworkParameters(
        val minimumPlatformVersion: Int,
        val maxMessageSize: Int,
        val maxTransactionSize: Int,
        @AutoAcceptable val modifiedTime: Instant,
        @AutoAcceptable val epoch: Int,
        val eventHorizon: Duration
) {
    override fun toString(): String {
        return """NetworkParameters {
      minimumPlatformVersion=$minimumPlatformVersion
      maxMessageSize=$maxMessageSize
      maxTransactionSize=$maxTransactionSize
      eventHorizon=$eventHorizon
      modifiedTime=$modifiedTime
      epoch=$epoch
  }"""
    }
}

/**
 * Data class storing information about notaries available in the network.
 * @property identity Identity of the notary (note that it can be an identity of the distributed node).
 * @property validating Indicates if the notary is validating.
 */

@CordaSerializable
data class NotaryInfo(val identity: Party, val validating: Boolean)

/**
 * When a Corda feature cannot be used due to the node's compatibility zone not enforcing a high enough minimum platform
 * version.
 */
class ZoneVersionTooLowException(message: String) : CordaRuntimeException(message)
