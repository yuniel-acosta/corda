package net.corda.mappedschemademo.contracts.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.CordaSerializable
import java.io.Serializable
import java.util.*
import javax.persistence.*

object InvoiceFinanceDealSchema

object InvoiceFinanceDealSchemaV1 : MappedSchema(
        schemaFamily = InvoiceFinanceDealSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentInvoiceFinanceDeal::class.java, PersistentInvoice::class.java)) {
    @CordaSerializable
    @Entity
    @Table(name = "invoice_finance_deal")
    class PersistentInvoiceFinanceDeal(
            @Column(name = "invoice_finance_deal_id")
            var invoiceFinanceDealId: UUID = UUID.randomUUID(),

            @Column(name = "reference")
            var reference: String = "",

            @Column(name = "borrower")
            var borrower: String = "",

            @Column(name = "lender")
            var lender: String = "",

            @Column(name = "currency")
            var currency: String = "",

            @Column(name = "loan_amount")
            var loanAmount: Long = 0,

            @Column(name = "fee_amount")
            var feeAmount: Long = 0,

            @OneToMany(targetEntity = PersistentInvoice::class, cascade = [CascadeType.ALL])
            @JoinColumns(JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id"), JoinColumn(name = "output_index", referencedColumnName = "output_index"))
            var invoiceList: MutableList<PersistentInvoice> = mutableListOf()
    ) : PersistentState()  {
        override var stateRef: PersistentStateRef?
            get() = super.stateRef
            set(value) {
                invoiceList.forEach { it.invoiceId.stateRef = value!! }
                super.stateRef = value
            }
    }

    // https://stackoverflow.com/questions/7146671/hibernate-foreign-key-as-part-of-primary-key

    @Embeddable
    data class PersistentInvoiceKey(
            var stateRef: PersistentStateRef = PersistentStateRef(txId =  "", index = 0),
            var invoiceId: UUID = UUID.randomUUID()
    ) : Serializable

    @CordaSerializable
    @Entity
    @Table(name = "invoice")
    class PersistentInvoice(
            @Column(name = "invoice_number")
            var invoiceNumber: String = "",

            @Column(name = "supplier")
            var supplier: String = "",

            @Column(name = "value_currency")
            var currency: String = "",

            @Column(name = "value_amount")
            var valueAmount: Long = 0,

            @Column(name = "paid_amount")
            var paidAmount: Long = 0,

            @Column(name = "invoice_finance_deal_id")
            var invoiceFinanceDealId: UUID = UUID.randomUUID(),

            @OneToOne
            var parentDeal: PersistentInvoiceFinanceDeal = PersistentInvoiceFinanceDeal(),

            @EmbeddedId
            var invoiceId: PersistentInvoiceKey = PersistentInvoiceKey()
    )
}
