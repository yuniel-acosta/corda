package net.corda.corenode.internal

import net.corda.core.DeleteForDJVM
import net.corda.core.crypto.Crypto
import net.corda.core.internal.castIfPossible
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.serialize
import net.corda.flows.utilities.UntrustworthyData
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.AbstractList

@DeleteForDJVM
inline fun <T : Any> T.signWithCert(signer: (SerializedBytes<T>) -> DigitalSignatureWithCert): SignedDataWithCert<T> {
    val serialised = serialize()
    return SignedDataWithCert(serialised, signer(serialised))
}

@DeleteForDJVM
fun <T : Any> T.signWithCert(privateKey: PrivateKey, certificate: X509Certificate): SignedDataWithCert<T> {
    return signWithCert {
        val signature = Crypto.doSign(privateKey, it.bytes)
        DigitalSignatureWithCert(certificate, signature)
    }
}

fun <T : Any> SerializedBytes<Any>.checkPayloadIs(type: Class<T>): UntrustworthyData<T> {
    val payloadData: T = try {
        val serializer = SerializationDefaults.SERIALIZATION_FACTORY
        serializer.deserialize(this, type, SerializationDefaults.P2P_CONTEXT)
    } catch (ex: Exception) {
        throw IllegalArgumentException("Payload invalid", ex)
    }
    return type.castIfPossible(payloadData)?.let { UntrustworthyData(it) }
            ?: throw IllegalArgumentException("We were expecting a ${type.name} but we instead got a ${payloadData.javaClass.name} ($payloadData)")
}

/**
 * List implementation that applies the expensive [transform] function only when the element is accessed and caches calculated values.
 * Size is very cheap as it doesn't call [transform].
 * Used internally by [net.corda.core.transactions.TraversableTransaction].
 */
class LazyMappedList<T, U>(val originalList: List<T>, val transform: (T, Int) -> U) : AbstractList<U>() {
    private val partialResolvedList = MutableList<U?>(originalList.size) { null }
    override val size get() = originalList.size
    override fun get(index: Int): U {
        return partialResolvedList[index]
                ?: transform(originalList[index], index).also { computed -> partialResolvedList[index] = computed }
    }

    internal fun eager(onError: (TransactionDeserialisationException, Int) -> U?) {
        for (i in 0 until size) {
            try {
                get(i)
            } catch (ex: TransactionDeserialisationException) {
                partialResolvedList[i] = onError(ex, i)
            }
        }
    }
}

/**
 * Returns a [List] implementation that applies the expensive [transform] function only when an element is accessed and then caches the calculated values.
 * Size is very cheap as it doesn't call [transform].
 */
fun <T, U> List<T>.lazyMapped(transform: (T, Int) -> U): List<U> = LazyMappedList(this, transform)

/**
 * Iterate over a [LazyMappedList], forcing it to transform all of its elements immediately.
 * This transformation is assumed to be "deserialisation". Does nothing for any other kind of [List].
 * WARNING: Any changes made to the [LazyMappedList] contents are PERMANENT!
 */
fun <T> List<T>.eagerDeserialise(onError: (TransactionDeserialisationException, Int) -> T? = { ex, _ -> throw ex }) {
    if (this is LazyMappedList<*, T>) {
        eager(onError)
    }
}
