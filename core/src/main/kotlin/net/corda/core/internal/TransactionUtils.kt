package net.corda.core.internal


import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.componentHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.serialization.*
import net.corda.core.utilities.OpaqueBytes
import java.io.ByteArrayOutputStream
import java.security.PublicKey
import kotlin.reflect.KClass

/** Check that network parameters hash on this transaction is the current hash for the network. */
fun FlowLogic<*>.checkParameterHash(networkParametersHash: SecureHash?) {
    // Transactions created on Corda 3.x or below do not contain network parameters,
    // so no checking is done until the minimum platform version is at least 4.
    if (networkParametersHash == null) {
        if (serviceHub.networkParameters.minimumPlatformVersion < 4) return
        else throw IllegalArgumentException("Transaction for notarisation doesn't contain network parameters hash.")
    } else {
        serviceHub.networkParametersService.lookup(networkParametersHash) ?: throw IllegalArgumentException("Transaction for notarisation contains unknown parameters hash: $networkParametersHash")
    }

    // TODO: [ENT-2666] Implement network parameters fuzzy checking. By design in Corda network we have propagation time delay.
    //       We will never end up in perfect synchronization with all the nodes. However, network parameters update process
    //       lets us predict what is the reasonable time window for changing parameters on most of the nodes.
    //       For now we don't check whether the attached network parameters match the current ones.
}

//val SignedTransaction.dependencies: Set<SecureHash>
//    get() = (inputs.asSequence() + references.asSequence()).map { it.txhash }.toSet()

