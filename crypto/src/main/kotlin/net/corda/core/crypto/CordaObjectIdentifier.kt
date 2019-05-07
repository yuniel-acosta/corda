package net.corda.core.crypto

import net.corda.core.KeepForDJVM
import org.bouncycastle.asn1.ASN1ObjectIdentifier

@KeepForDJVM
object CordaObjectIdentifier {
    // UUID-based OID
    // TODO define and use an official Corda OID in [CordaOID]. We didn't do yet for backwards compatibility purposes,
    //      because key.encoded (serialised version of keys) and [PublicKey.hash] for already stored [CompositeKey]s
    //      will not match.
    @JvmField
    val COMPOSITE_KEY = ASN1ObjectIdentifier("2.25.30086077608615255153862931087626791002")
    @JvmField
    val COMPOSITE_SIGNATURE = ASN1ObjectIdentifier("2.25.30086077608615255153862931087626791003")
}
