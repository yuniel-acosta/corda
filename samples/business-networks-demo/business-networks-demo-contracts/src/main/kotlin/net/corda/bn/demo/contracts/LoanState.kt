package net.corda.bn.demo.contracts

import net.corda.bn.states.BNPermission
import net.corda.bn.states.BNRole
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
        val networkId: String,
        override val linearId: UniqueIdentifier = UniqueIdentifier(),
        override val participants: List<AbstractParty> = listOf(lender, borrower)
) : LinearState {

    fun settle(amountToSettle: Int) = copy(amount = amount - amountToSettle)
}

class LoanIssuerRole : BNRole("LoanIssuer", setOf(LoanPermissions.CAN_ISSUE_LOAN))

enum class LoanPermissions : BNPermission { CAN_ISSUE_LOAN }