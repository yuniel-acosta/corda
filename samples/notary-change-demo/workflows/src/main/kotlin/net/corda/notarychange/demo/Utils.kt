package net.corda.notarychange.demo

import net.corda.core.node.ServiceHub

fun ServiceHub.nodeLegalIdentity() = this.myInfo.legalIdentities.first()