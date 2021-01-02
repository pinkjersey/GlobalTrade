package tr.com.citlembik.contracts

import net.corda.finance.DOLLARS
import net.corda.testing.node.MockServices
import org.junit.Test
import tr.com.citlembik.states.ItemState
import kotlin.test.assertEquals

class StateTests {
    private val ledgerServices = MockServices()

    @Test
    fun `ItemState_Create_Created`() {
        ItemState(ALICE.party, "Fidget spinner", "001", 5.DOLLARS,
                listOf(MEGACORP.party.anonymise(), BOB.party.anonymise()))
    }

    @Test
    fun `ItemState_UpdatePrice_Updated`() {
        val state = ItemState(ALICE.party, "Fidget spinner", "001", 5.DOLLARS,
                listOf(MEGACORP.party.anonymise(), BOB.party.anonymise()))
        val newState = state.withNewPrice(6.DOLLARS)
        assertEquals(state.name, newState.name)
        assertEquals(state.sku, newState.sku)
        assertEquals(state.potentialBuyers, newState.potentialBuyers)
        assertEquals(6.DOLLARS, newState.price)
    }

    @Test
    fun `ItemState_UpdateBuyers_Updated`() {
        val state = ItemState(ALICE.party, "Fidget spinner", "001", 5.DOLLARS,
                listOf(MEGACORP.party.anonymise(), BOB.party.anonymise()))
        val newBuyerList = state.potentialBuyers.plus(MINICORP.party.anonymise())
        val newState = state.withNewBuyers(newBuyerList)
        assertEquals(state.name, newState.name)
        assertEquals(state.sku, newState.sku)
        assertEquals(newBuyerList, newState.potentialBuyers)
        assertEquals(state.price, newState.price)
    }
}