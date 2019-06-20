package net.corda.corenode.identity

import net.corda.core.contracts.PartyAndReference
import net.corda.core.identity.AbstractParty
import net.corda.core.utilities.OpaqueBytes

fun AbstractParty.ref(bytes: OpaqueBytes) = PartyAndReference(this, bytes)

fun AbstractParty.ref(vararg bytes: Byte) = ref(OpaqueBytes.of(*bytes))
