package net.corda.bn.demo.contracts

import net.corda.bn.states.BNPermission
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

@BelongsToContract(LoanContract::class)
data class LoanState(
        val lender: Party,
        val borrower: Party,
        val amount: Int,
        override val linearId: UniqueIdentifier = UniqueIdentifier(),
        override val participants: List<AbstractParty> = listOf(lender, borrower)
) : LinearState {

    fun settle(amountToSettle: Int) = copy(amount = amount - amountToSettle)
}

enum class LoanPermissions : BNPermission { CAN_ISSUE_LOAN }