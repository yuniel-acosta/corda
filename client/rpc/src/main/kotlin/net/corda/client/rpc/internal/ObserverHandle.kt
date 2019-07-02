package net.corda.client.rpc.internal

import rx.Subscription

/**
 * Handle to externally control a subscribed observer for [ReconnectingObservable]s.
 */
class ObserverHandle {
    var stopped = false
    var activeSubscription: Subscription? = null

    fun stop() {
        activeSubscription?.unsubscribe()
        stopped = true
    }

}