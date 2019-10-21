package net.corda.core.internal

import net.corda.core.crypto.DigitalSignature
import net.corda.core.transactions.WireTransaction

interface SigningEnclaveClient {

    fun getEnclaveSignature(tx: WireTransaction): DigitalSignature.WithKey

}