package tr.com.citlembik.states

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import tr.com.citlembik.schema.ItemSchemaV1
import java.util.*

@BelongsToContract(tr.com.citlembik.contracts.ItemContract::class)
data class ItemState(
        val seller: Party,
        val name: String,
        val sku: String,
        val price: Amount<Currency>,
        val potentialBuyers: List<AnonymousParty>,
        override val linearId: UniqueIdentifier) : LinearState, QueryableState {

    constructor(seller: Party, name: String, sku: String, price: Amount<Currency>, potentialBuyers: List<AnonymousParty>) :
            this(seller, name, sku, price, potentialBuyers, UniqueIdentifier(sku))

    override val participants: List<AbstractParty>
        get() = listOf(seller).plus(potentialBuyers)

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(ItemSchemaV1)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is ItemSchemaV1 -> ItemSchemaV1.PersistentItem(
                    this.seller.name.toString(),
                    this.name,
                    this.sku,
                    this.price.quantity,
                    this.price.token.currencyCode,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    fun withNewPrice(newPrice: Amount<Currency>) = copy(price = newPrice)
    fun withNewBuyers(newBuyerList: List<AnonymousParty>) = copy(potentialBuyers = newBuyerList)

}

/*
data class State(
        override val participants: List<AbstractParty>,
        override val linearId: UniqueIdentifier) : LinearState, QueryableState {
    constructor(participants: List<AbstractParty> = listOf(),
                ref: String) : this(participants, UniqueIdentifier(ref))

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(UniqueDummyLinearStateSchema)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return UniqueDummyLinearStateSchema.UniquePersistentLinearDummyState(id = linearId.externalId!!)
    }
}*/