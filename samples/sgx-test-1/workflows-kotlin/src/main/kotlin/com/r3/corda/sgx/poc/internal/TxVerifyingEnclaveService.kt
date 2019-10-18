/*
package com.r3.corda.sgx.poc.internal

import com.r3.corda.sgx.common.AttestedOutput
import com.r3.corda.sgx.common.TrustedNodeServices
import com.r3.corda.sgx.common.transactions.ResolvableWireTransaction
import com.r3.corda.sgx.host.GrpcCordaEnclaveletClient
import com.r3.sgx.enclavelethost.client.EnclaveletMetadata
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.EnclaveIdentity
import net.corda.core.crypto.SecureHash
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.OpaqueBytes
import java.security.PublicKey

@CordaService
class TxVerifyingEnclaveService(val services: ServiceHub) : SingletonSerializeAsToken() {

    private val nodeServices = object : TrustedNodeServices {
        override val resolveIdentity = { it: PublicKey -> services.identityService.partyFromKey(it)!! }
        override val resolveAttachment = { it: SecureHash -> services.attachments.openAttachment(it)!! }
        override val resolveStateRef = { it: StateRef -> WireTransaction.resolveStateRefBinaryComponent(it, services)!! }
        override val resolveParameters = { it: SecureHash ->
            val hashToResolve = it ?: services.networkParametersService.defaultHash
            services.networkParametersService.lookup(hashToResolve)!!
        }
        override val resolveContractAttachment = { it: StateRef -> services.loadContractAttachment(it)!! }
    }

    private val enclaveletClient = lazy {
        GrpcCordaEnclaveletClient<ResolvableWireTransaction, SecureHash>(
                server = "localhost:40000",
                nodeServices = nodeServices)
    }

    fun invoke(tx: WireTransaction): AttestedOutput<SecureHash> {
            val resolvableTx = ResolvableWireTransaction(tx, services)
            return enclaveletClient.value.invoke(resolvableTx)
    }

    companion object {

        val enclaveId: EnclaveIdentity

        init {
            val metadata = EnclaveletMetadata.read(this::class.java.getResourceAsStream("/enclave.metadata.yml"))
            val measurement = OpaqueBytes(metadata.measurement.data)
            enclaveId = EnclaveIdentity(measurement, EnclaveIdentity.EnclaveMode.DEBUG)
        }
    }
}
*/
