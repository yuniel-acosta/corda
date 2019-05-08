package com.r3.corda.sgx.enclave

import com.r3.corda.sgx.common.TrustedNodeServices
import com.r3.corda.sgx.common.transactions.ResolvableWireTransaction
import com.r3.corda.sgx.enclave.internal.MyAMQPSerializationScheme
import com.r3.corda.sgx.enclave.internal.asContextEnv
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import java.net.URLClassLoader
import java.nio.file.Paths
import java.util.function.Supplier

object TestData {
    val testDataClassLoader = URLClassLoader(arrayOf(Paths.get("/home/igor/projects/sgxjvm/samples/tx-verifying-enclave/cordapp/build/libs/cordapp-test-data.jar").toUri().toURL()))
    val SERIALIZATION_ENV = MyAMQPSerializationScheme.createSerializationEnv()
    val DUMMY_TOKEN_ISSUE_SERIALIZED =         (testDataClassLoader.loadClass("com.r3.corda.sgx.poc.X").newInstance() as Supplier<SerializedBytes<ResolvableWireTransaction>>).get()
    val DUMMY_TOKEN_ISSUE = SERIALIZATION_ENV.asContextEnv {
        DUMMY_TOKEN_ISSUE_SERIALIZED.deserialize()
    }

    val MockTxResolutionServices = testDataClassLoader.loadClass("com.r3.corda.sgx.poc.MockServices").newInstance() as TrustedNodeServices
}