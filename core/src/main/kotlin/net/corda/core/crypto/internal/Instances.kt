package net.corda.core.crypto.internal

import java.security.Provider
import java.security.Signature

/**
 * This is a collection of crypto related getInstance methods that tend to be quite inefficient and we want to be able to
 * optimise them en masse.
 */
object Instances {
    fun getSignatureInstance(algorithm: String, provider: Provider?): Signature {
        println("getSignatureInstance: $algorithm, ${provider?.info}, entryCount=${provider?.count()}, contains($algorithm=${provider?.containsValue(algorithm)})")
        try {
            return Signature.getInstance(algorithm, provider)
        }
        catch  (e: Exception) {
            println("Signature.getInstance() failed to locate $algorithm for provider ${provider?.info}.")
            provider?.forEach { println("${it.key} = ${it.value}") }
            e.printStackTrace()
            throw e
        }
    }
}