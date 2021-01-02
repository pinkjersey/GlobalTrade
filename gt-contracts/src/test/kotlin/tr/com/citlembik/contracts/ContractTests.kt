package tr.com.citlembik.contracts

import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.finance.DOLLARS
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test
import tr.com.citlembik.states.ItemState

class ContractTests {
    class DummyCommand : TypeOnlyCommandData()
    private val ledgerServices = MockServices(listOf("tr.com.citlembik.contracts"))

    @Test
    fun stateContract_DummyCommand_Fails() {
        val item = ItemState(ALICE.party, "Fidget spinner", "001", 5.DOLLARS,
                listOf(MEGACORP.party.anonymise(), BOB.party.anonymise()))
        ledgerServices.ledger {
            transaction {
                output(ItemContract.ID,  item)
                command(listOf(ALICE.publicKey, BOB.publicKey, MEGACORP.publicKey), DummyCommand())
                this.fails()
            }
        }
    }

    @Test
    fun stateContract_CreateCommand_Validates() {
        val item = ItemState(ALICE.party, "Fidget spinner", "001", 5.DOLLARS,
                listOf(MEGACORP.party.anonymise(), BOB.party.anonymise()))
        ledgerServices.ledger {
            transaction {
                output(ItemContract.ID, item)
                command(listOf(ALICE.publicKey, BOB.publicKey, MEGACORP.publicKey), ItemContract.Commands.Create())
                this.verifies()
            }
        }
    }

    @Test
    fun stateContract_SellerIsBuyer_Fails() {
        val item = ItemState(ALICE.party, "Fidget spinner", "001", 5.DOLLARS,
                listOf(MEGACORP.party.anonymise(), ALICE.party.anonymise()))
        ledgerServices.ledger {
            transaction {
                output(ItemContract.ID, item)
                command(listOf(ALICE.publicKey, BOB.publicKey, MEGACORP.publicKey), ItemContract.Commands.Create())
                this.fails()
            }
        }
    }
}