@file:JvmName("NodeTestUtils")

package net.corda.testing.node

import net.corda.core.context.Actor
import net.corda.core.context.AuthServiceId
import net.corda.core.context.InvocationContext
import net.corda.core.identity.CordaX500Name

/** Creates a new [Actor] for use in testing with the given [owningLegalIdentity]. */
fun testActor(owningLegalIdentity: CordaX500Name = CordaX500Name("Test Company Inc.", "London", "GB")) = Actor(Actor.Id("Only For Testing"), AuthServiceId("TEST"), owningLegalIdentity)

/** Creates a new [InvocationContext] for use in testing with the given [owningLegalIdentity]. */
fun testContext(owningLegalIdentity: CordaX500Name = CordaX500Name("Test Company Inc.", "London", "GB")) = InvocationContext.rpc(testActor(owningLegalIdentity))
