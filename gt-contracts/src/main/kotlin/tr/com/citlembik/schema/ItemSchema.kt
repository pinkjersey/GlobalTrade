package tr.com.citlembik.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table
import org.hibernate.annotations.Type

// this is the family of Item schemas
object ItemSchema

/**
 * An IOUState schema.
 */
object ItemSchemaV1 : MappedSchema(
        schemaFamily = ItemSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentItem::class.java)) {

    override val migrationResource: String?
        get() = "item.changelog-master";

    @Entity
    @Table(name = "item_states")
    class PersistentItem(
            @Column(name = "seller")
            var sellerName: String,

            @Column(name = "name")
            var itemName: String,

            @Column(name = "sku")
            var sku: String,

            @Column(name = "value")
            var value: Long,

            @Column(name = "currency")
            var ccy: String,

            @Column(name = "for_sale")
            var forSale: Boolean,

            @Column(name = "linear_id")
            @Type(type = "uuid-char")
            var linearId: UUID
    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this("", "", "",0, "", false, UUID.randomUUID())
    }
}