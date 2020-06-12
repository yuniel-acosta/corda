package net.corda.bn.flows

import net.corda.bn.flows.extensions.PublicCentralisedBNMemberAuth
import net.corda.bn.flows.extensions.PublicDecentralisedBNMemberAuth

object BNUtils {
    fun loadBNMemberAuth() = PublicCentralisedBNMemberAuth()
}