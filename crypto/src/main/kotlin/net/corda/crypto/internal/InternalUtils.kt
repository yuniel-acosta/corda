package net.corda.crypto.internal

import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import java.security.PublicKey
import java.security.cert.*

val PublicKey.hash: SecureHash get() = encoded.sha256()

// TODO: Currently the certificate revocation status is not handled here. Nowhere in the code the second parameter is used. Consider adding the support in the future.
fun CertPath.validate(trustAnchor: TrustAnchor, checkRevocation: Boolean = false): PKIXCertPathValidatorResult {
    val parameters = PKIXParameters(setOf(trustAnchor)).apply { isRevocationEnabled = checkRevocation }
    try {
        return CertPathValidator.getInstance("PKIX").validate(this, parameters) as PKIXCertPathValidatorResult
    } catch (e: CertPathValidatorException) {
        throw CertPathValidatorException(
                """Cert path failed to validate.
Reason: ${e.reason}
Offending cert index: ${e.index}
Cert path: $this

Trust anchor:
$trustAnchor""", e, this, e.index)
    }
}

