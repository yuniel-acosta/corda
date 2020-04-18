package net.corda.testing.internal

import net.corda.core.cordapp.Cordapp
import net.corda.nodeapi.internal.cordapp.CordappLoader
import net.corda.node.internal.cordapp.CordappProviderImpl

class MockCordappProvider(
        cordappLoader: CordappLoader,
        cordappConfigProvider: MockCordappConfigProvider = MockCordappConfigProvider()
) : CordappProviderImpl(cordappLoader, cordappConfigProvider) {
    private val cordappRegistry = mutableListOf<Cordapp>()
}
