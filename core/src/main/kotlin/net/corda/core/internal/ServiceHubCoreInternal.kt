package net.corda.core.internal

import co.paralleluniverse.fibers.Suspendable

import net.corda.core.node.ServiceHub
import java.util.concurrent.ExecutorService

// TODO: This should really be called ServiceHubInternal but that name is already taken by net.corda.node.services.api.ServiceHubInternal.

interface ServiceHubCoreInternal : ServiceHub {

    val externalOperationExecutor: ExecutorService
}