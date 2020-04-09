package net.corda.client.jfx.model

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import net.corda.client.jfx.utils.distinctBy
import net.corda.client.jfx.utils.fold
import net.corda.client.jfx.utils.map
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.services.Vault
import rx.Observable

data class Diff<out T : ContractState>(
        val added: Collection<StateAndRef<T>>,
        val removed: Collection<StateAndRef<T>>
)