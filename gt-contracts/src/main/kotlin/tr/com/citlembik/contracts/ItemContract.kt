package tr.com.citlembik.contracts

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import tr.com.citlembik.states.ItemState

/**
 * An item is something someone can sell. We advertise this to
 * potential buyers.
 */
class ItemContract : Contract {
    companion object {
        @JvmStatic
        val ID = "tr.com.citlembik.contracts.ItemContract"
    }

    /**
     * A seller can create an item, update its price and advertise it
     * to buyers.
     */
    interface Commands : CommandData {
        class Create : TypeOnlyCommandData(), Commands
        class UpdatePrice : TypeOnlyCommandData(), Commands
        class AddBuyer : TypeOnlyCommandData(), Commands
        class NoLongerForSale : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.Create -> requireThat {
                "This is the initial item so no input should exist." using (tx.inputs.isEmpty())
                "The item should be created." using (tx.outputs.size == 1)
                val item = tx.outputsOfType<ItemState>().single()
                "The price should be a zero or greater" using (item.price.quantity >= 0)
                "The seller cannot be in the buyer list" using (!item.potentialBuyers.contains(item.seller.anonymise()))
                "the name must be filled in" using (item.name.isNotEmpty())
                "the item sku must be filled in" using (item.sku.isNotEmpty())
                "the for sale flag must be set" using item.forSale
            }
            is Commands.UpdatePrice -> requireThat {
                "An update should consume the previous state." using (tx.inputs.size == 1)
                "An update should only create one state." using (tx.outputs.size == 1)
                val input = tx.inputsOfType<ItemState>().single()
                val output = tx.outputsOfType<ItemState>().single()
                "Only the price may change." using (input == output.withNewPrice(input.price))
                "The price must change." using (input.price != output.price)
                "The new price must be greater or equal to zero." using (output.price.quantity >= 0)
                "the for sale flag must be set" using input.forSale
                "The seller and the potential buyers must sign the transaction" using
                        (command.signers.toSet() == (input.participants.map { it.owningKey }.toSet() `union`
                                output.participants.map { it.owningKey }.toSet()))
            }
            is Commands.AddBuyer -> requireThat {
                "An update should consume the previous state." using (tx.inputs.size == 1)
                "An update should only create one state." using (tx.outputs.size == 1)
                val input = tx.inputsOfType<ItemState>().single()
                val output = tx.outputsOfType<ItemState>().single()
                "Only the buyer list may change." using (input == output.withNewBuyers(input.potentialBuyers))
                "The new buyer list must contain more buyers." using (input.potentialBuyers.count() < output.potentialBuyers.count())
                "The old buyer list must be a subset of the new." using (output.potentialBuyers.containsAll(input.potentialBuyers))
                "the for sale flag must be set" using input.forSale
                "The seller and the potential buyers must sign the transaction" using
                        (command.signers.toSet() == (input.participants.map { it.owningKey }.toSet() `union`
                                output.participants.map { it.owningKey }.toSet()))
            }
            is Commands.NoLongerForSale -> requireThat {
                "An update should consume the previous state." using (tx.inputs.size == 1)
                "An update should only create one state." using (tx.outputs.size == 1)
                val input = tx.inputsOfType<ItemState>().single()
                val output = tx.outputsOfType<ItemState>().single()
                "The for sale flag must be false" using !output.forSale
                "Only the for sale flag may change" using (input == output.copy(forSale = true))
            }
        }
    }
}