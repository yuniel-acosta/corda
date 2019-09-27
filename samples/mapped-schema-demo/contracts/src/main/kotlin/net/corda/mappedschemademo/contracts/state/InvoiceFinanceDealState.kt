package net.corda.mappedschemademo.contracts.state

import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.mappedschemademo.contracts.contract.InvoiceFinanceDealContract
import net.corda.mappedschemademo.contracts.schema.InvoiceFinanceDealSchemaV1
import java.util.*

@CordaSerializable
enum class InvoiceFinanceDealStatus {
    PROPOSAL,
    ACCEPTED
}

@BelongsToContract(InvoiceFinanceDealContract::class)
data class InvoiceFinanceDealState(
        override val linearId: UniqueIdentifier = UniqueIdentifier(),
        val reference: String = "",
        val borrower: Party,
        val lender: Party,
        val loan: Amount<Currency>,
        val fee: Amount<Currency>,
        val status: InvoiceFinanceDealStatus = InvoiceFinanceDealStatus.PROPOSAL,
        val invoiceList: List<Invoice>)
    : LinearState, QueryableState {

    override val participants: List<AbstractParty> get() {
        return listOf(borrower, lender)
    }

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is InvoiceFinanceDealSchemaV1 -> {
                val deal = InvoiceFinanceDealSchemaV1.PersistentInvoiceFinanceDeal(
                        this.linearId.id,
                        this.reference,
                        this.borrower.name.toString(),
                        this.lender.name.toString(),
                        this.loan.token.toString(),
                        this.loan.quantity,
                        this.fee.quantity)
                deal.invoiceList =
                    invoiceList.map { InvoiceFinanceDealSchemaV1.PersistentInvoice(
                                it.invoiceNumber,
                                it.supplier,
                                it.value.token.toString(),
                                it.value.quantity,
                                it.paid.quantity,
                                it.invoiceId,
                                deal,
                                InvoiceFinanceDealSchemaV1.PersistentInvoiceKey(invoiceId = it.invoiceId))
                        }.toMutableList()
                deal
            } else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(InvoiceFinanceDealSchemaV1)
}

@CordaSerializable
data class Invoice (
        var invoiceId: UUID = UUID.randomUUID(),
        var invoiceNumber: String = "",
        var supplier: String = "",
        var value: Amount<Currency>,
        var paid: Amount<Currency>
)
