package net.corda.client.rpc.internal

import com.google.common.util.concurrent.ThreadFactoryBuilder
import net.corda.core.messaging.DataFeed
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import rx.Observable
import rx.Subscription
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

class ReconnectingObservableImpl<T> internal constructor(
        val reconnectingSubscriber: ReconnectingSubscriber<T>
) : Observable<T>(reconnectingSubscriber), ReconnectingObservable<T> by reconnectingSubscriber {

    private companion object {
        private val log = contextLogger()
    }

    constructor(reconnectingRPCConnection: ReconnectingCordaRPCOps.ReconnectingRPCConnection, observersPool: ExecutorService, initial: DataFeed<*, T>, createDataFeed: () -> DataFeed<*, T>):
            this(ReconnectingSubscriber(reconnectingRPCConnection, observersPool, initial, createDataFeed))

    class ReconnectingSubscriber<T>(val reconnectingRPCConnection: ReconnectingCordaRPCOps.ReconnectingRPCConnection,
                                    val observersPool: ExecutorService,
                                    val initial: DataFeed<*, T>,
                                    val createDataFeed: () -> DataFeed<*, T>): OnSubscribe<T>, ReconnectingObservable<T> {

        private val activeSubscriptions = ConcurrentHashMap<ObserverHandle, Subscription>()

        override fun call(child: rx.Subscriber<in T>) {
            val handle = subscribe(child::onNext, {}, {}, {})
            // this additional subscription allows us to detect un-subscription calls from clients and un-subscribe any subscription that resulted from re-connections.
            child.add(object: Subscription {
                @Volatile
                var unsubscribed: Boolean = false

                override fun unsubscribe() {
                    handle.stop()
                    unsubscribed = true
                }

                override fun isUnsubscribed(): Boolean {
                    return unsubscribed
                }
            })
        }

        private var initialStartWith: Iterable<T>? = null
        private fun subscribeWithReconnect(observerHandle: ObserverHandle, onNext: (T) -> Unit, onStop: () -> Unit, onDisconnect: () -> Unit, onReconnect: () -> Unit, startWithValues: Iterable<T>? = null) {
            try {
                val subscription = initial.updates.let { if (startWithValues != null) it.startWith(startWithValues) else it }
                        .subscribe(onNext,
                                { error ->
                                    activeSubscriptions[observerHandle]!!.unsubscribe()
                                    onDisconnect()
                                    reconnectingRPCConnection.error(error)
                                    log.debug { "Recreating data feed." }
                                    val newObservable = createDataFeed().updates as ReconnectingObservableImpl<T>
                                    onReconnect()
                                    if (!observerHandle.stopped)
                                        newObservable.reconnectingSubscriber.subscribeWithReconnect(observerHandle, onNext, onStop, onDisconnect, onReconnect, startWithValues)
                                },
                                {
                                    onStop()
                                    activeSubscriptions[observerHandle]!!.unsubscribe()
                                })
                activeSubscriptions[observerHandle] = subscription
                observerHandle.activeSubscription = subscription
            } catch (e: Exception) {
                log.error("Failed to register subscriber .", e)
            }
        }

        override fun subscribe(onNext: (T) -> Unit, onStop: () -> Unit, onDisconnect: () -> Unit, onReconnect: () -> Unit): ObserverHandle {
            val observerNotifier = ObserverHandle()
            subscribeWithReconnect(observerNotifier, onNext, onStop, onDisconnect, onReconnect, initialStartWith)
            return observerNotifier
        }

        override fun startWithValues(values: Iterable<T>): ReconnectingObservable<T> {
            initialStartWith = values
            return this
        }

    }

}