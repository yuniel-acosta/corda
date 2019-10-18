package com.r3.corda.sgx.enclave.internal

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationContext.UseCase.P2P
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.internal._inheritableContextSerializationEnv
import net.corda.core.utilities.sequence
import net.corda.serialization.internal.*
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import net.corda.serialization.internal.amqp.AccessOrderLinkedHashMap
import net.corda.serialization.internal.amqp.SerializerFactory
import java.nio.ByteBuffer


/**
 * Some boilerplate for setting up the Corda serialization machine
 */
class MyAMQPSerializationScheme :
        AbstractAMQPSerializationScheme(emptySet(), emptySet(), AccessOrderLinkedHashMap { 128 }) {

    companion object {
        fun createSerializationEnv(classLoader: ClassLoader? = null): SerializationEnvironment {
            return SerializationEnvironment.with(
                SerializationFactoryImpl().apply {
                    registerScheme(MyAMQPSerializationScheme())
                },
                p2pContext = if (classLoader != null) AMQP_P2P_CONTEXT.withClassLoader(classLoader) else AMQP_P2P_CONTEXT
            )
        }
    }

    override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
        return canDeserializeVersion(magic) && target == P2P
    }

    override fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory {
        throw UnsupportedOperationException("Client serialization not supported")
    }

    override fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory {
        throw UnsupportedOperationException("Server serialization not supported")
    }
}

fun <T> SerializationEnvironment.asContextEnv(inheritable: Boolean = false, callable: (SerializationEnvironment) -> T): T {
    /*
    val property = if (inheritable) _inheritableContextSerializationEnv else _contextSerializationEnv
    property.set(this)
    try {
        return callable(this)
    } finally {
        property.set(null)
    }

       DISABLED IN MOCK MODE

     */
    return callable(this)
}

inline fun <reified T: Any> ByteBuffer.deserialize(env: SerializationEnvironment): T {
    return deserialize(env, T::class.java)
}

inline fun <T: Any> ByteBuffer.deserialize(env: SerializationEnvironment, clazz: Class<T>): T {
    val inputBytes = ByteArray(remaining()).also {
        this.get(it)
    }
    return env.asContextEnv {
        SerializationFactory.defaultFactory.deserialize(
                inputBytes.sequence(),
                clazz,
                SerializationFactory.defaultFactory.defaultContext)
    }
}
