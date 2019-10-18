/*
package com.r3.corda.sgx.host

import com.r3.corda.sgx.common.AttestedOutput
import com.r3.corda.sgx.common.TrustedNodeServices
import com.r3.corda.sgx.host.connector.GrpcHandlerConnector
import com.r3.corda.sgx.host.connector.HandlerConnector
import com.r3.sgx.enclavelethost.grpc.EnclaveletHostGrpc
import com.r3.sgx.enclavelethost.grpc.GetEpidAttestationRequest
import com.r3.sgx.enclavelethost.grpc.GetEpidAttestationResponse
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import com.r3.corda.sgx.common.EnclaveSignature
import net.corda.core.crypto.SignedData
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.serialize
import net.corda.core.utilities.sequence
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

class GrpcCordaEnclaveletClient<U: Any, V: Any>(
        private val server: String,
        private val nodeServices: TrustedNodeServices
) {

    private val channel = ManagedChannelBuilder.forTarget(server)
            .usePlaintext()
            .build()

    private val attestationReport = lazy<EnclaveSignature.AttestationReport> {
        val result = CompletableFuture<EnclaveSignature.AttestationReport>()
        createRpcProxy()
                .getEpidAttestation(GetEpidAttestationRequest.getDefaultInstance(),
                        object: StreamObserver<GetEpidAttestationResponse> {
                            override fun onCompleted() {}
                            override fun onNext(value: GetEpidAttestationResponse?) {
                                require(result.getNow(null) == null)
                                if (value == null) {
                                    result.completeExceptionally(RuntimeException("Failed to receive attestation"))
                                } else {
                                    result.complete(EnclaveSignature.AttestationReport.EpidAttestationReport(
                                            epidAttestation = value.attestation
                                    ))
                                }
                            }
                            override fun onError(t: Throwable?) {
                                result.completeExceptionally(t)
                            }
                        })
        result.get()
    }

    fun invoke(input: U): AttestedOutput<V> {
        val enclaveConnection = createEnclaveletConnector()
                .setDownstream(CordaEnclaveletClientHandler(nodeServices))
        val serialized = input.serialize().bytes
        val response = enclaveConnection.sendAndReceive(ByteBuffer.wrap(serialized))
        val responseBytes = ByteArray(response.remaining()).also { response.get(it)}
        val output = uncheckedCast(SerializationFactory.defaultFactory.deserialize(
                responseBytes.sequence(),
                SignedData::class.java,
                SerializationFactory.defaultFactory.defaultContext))
        val attestedOutput = AttestedOutput(output.raw, EnclaveSignature(output.sig, attestationReport.value))
        return uncheckedCast(attestedOutput)
    }

    private fun createRpcProxy(): EnclaveletHostGrpc.EnclaveletHostStub {
        return EnclaveletHostGrpc.newStub(channel)
                .withCompression("gzip")
                .withWaitForReady()
    }

    private fun createEnclaveletConnector(): HandlerConnector {
        return GrpcHandlerConnector(createRpcProxy())
    }
}
 */