package net.corda.mappedschemademo.contracts.test

import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import java.util.*

open class InvoiceFinanceDealContractTestsBase {
    protected val ledgerServices = MockServices(listOf("net.corda.mappedschemademo.contracts", "net.corda.finance.contracts"))

    protected val lender = TestIdentity(CordaX500Name("LendingCorp", "London", "GB"))
    protected val borrower = TestIdentity(CordaX500Name("BorrowingCorp", "New York", "US"))
    protected val issuer = TestIdentity(CordaX500Name("MegaBank", "", "US"))

    protected val defaultRef = Byte.MAX_VALUE
    protected val defaultIssuer = issuer.ref(defaultRef)

    protected fun createCashState(amount: Amount<Currency>, owner: AbstractParty): Cash.State {
        return Cash.State(amount = amount `issued by` defaultIssuer, owner = owner)
    }

}