package net.corda.notarychange.demo.contracts

import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException

class SwapNotaryTokenContract: Contract {

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.single()
        when(command.value) {
            is SwapNotaryCommand -> {
//                val issuer = command.value.token.issuer
            }
            else -> throw IllegalArgumentException("")
        }
    }
}

data class SwapNotaryCommand(val token: IssuedTokenType) : TypeOnlyCommandData()