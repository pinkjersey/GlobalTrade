package tr.com.citlembik

import net.corda.core.contracts.TransactionVerificationException
import tr.com.citlembik.flows.ItemCreateResponder
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.testing.core.internal.ContractJarTestUtils.makeTestJar
import net.corda.testing.internal.chooseIdentityAndCert
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import tr.com.citlembik.contracts.ItemContract
import tr.com.citlembik.flows.ItemAddBuyerFlow
import tr.com.citlembik.flows.ItemCreateFlow
import tr.com.citlembik.flows.ItemUpdateFlow
import tr.com.citlembik.states.ItemState
import java.lang.IllegalArgumentException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


class FlowTests {

    lateinit var mockNetwork: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode
    lateinit var c: StartedMockNode

    @Before
    fun setup() {
        mockNetwork = MockNetwork(
                listOf("tr.com.citlembik"),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB")))
        )
        a = mockNetwork.createNode(MockNodeParameters())
        b = mockNetwork.createNode(MockNodeParameters())
        c = mockNetwork.createNode(MockNodeParameters())
        mockNetwork.runNetwork()
    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
    }

    @Test
    fun flowReturnsCorrectlyFormedPartiallySignedTransaction() {
        val buyer = b.info.chooseIdentityAndCert().party
        val flow = ItemCreateFlow("Fidget spinner", "001", 6.DOLLARS, listOf(buyer.anonymise()))
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        // Return the unsigned(!) SignedTransaction object from the flow.
        val ptx: SignedTransaction = future.getOrThrow()
        // Print the transaction for debugging purposes.
        println(ptx.tx)
        // Check the transaction is well formed...
        // No outputs, one input IOUState and a command with the right properties.
        assert(ptx.tx.inputs.isEmpty())
        assert(ptx.tx.outputs.single().data is ItemState)
        val item = ptx.tx.outputs.single().data
        val command = ptx.tx.commands.single()
        assert(command.value is ItemContract.Commands.Create)
        assert(command.signers.toSet() == item.participants.map { it.owningKey }.toSet())
        ptx.verifySignaturesExcept(
                buyer.owningKey,
                mockNetwork.defaultNotaryNode.info.legalIdentitiesAndCerts.first().owningKey
        )
    }

    @Test
    fun flowReturnsVerifiedPartiallySignedTransaction() {
        // Check that item with no name fails
        val seller = a.info.chooseIdentityAndCert().party
        val buyer = b.info.chooseIdentityAndCert().party
        //val badItem = ItemState(seller, "", "001", 6.DOLLARS, listOf(buyer.anonymise()))
        // attempts to create bad item (blank item name)
        val futureOne = a.startFlow(ItemCreateFlow("", "001", 6.DOLLARS, listOf(buyer.anonymise())))
        mockNetwork.runNetwork()
        assertFailsWith<TransactionVerificationException> { futureOne.getOrThrow() }
        // attempts to create bad item (blank sku)
        val futureTwo = a.startFlow(ItemCreateFlow("Fidget spinner", "", 6.DOLLARS, listOf(buyer.anonymise())))
        mockNetwork.runNetwork()
        assertFailsWith<TransactionVerificationException> { futureTwo.getOrThrow() }
        // attempts to create a bad item (seller is also buyer
        val futureThree = a.startFlow(ItemCreateFlow("Fidget spinner", "001", 6.DOLLARS, listOf(seller.anonymise())))
        mockNetwork.runNetwork()
        assertFailsWith<TransactionVerificationException> { futureThree.getOrThrow() }
        // Check a good item passes.
        val futureFour = a.startFlow(ItemCreateFlow("Fidget spinner", "001", 6.DOLLARS, listOf(buyer.anonymise())))
        mockNetwork.runNetwork()
        futureFour.getOrThrow()
    }

    @Test
    fun flowReturnsTransactionSignedAllParties() {
        val buyer1 = b.info.chooseIdentityAndCert().party
        val buyer2 = c.info.chooseIdentityAndCert().party
        val flow = ItemCreateFlow("Fidget spinner", "001", 6.DOLLARS, listOf(buyer1.anonymise(), buyer2.anonymise()))
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        val stx = future.getOrThrow()
        stx.verifyRequiredSignatures()
    }

    @Test
    fun flowReturnsTransactionSignedAllPartiesFollowedByPriceUpdate() {
        val buyer1 = b.info.chooseIdentityAndCert().party
        val flow = ItemCreateFlow("Fidget spinner", "007", 6.DOLLARS, listOf(buyer1.anonymise()))
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        val stx = future.getOrThrow()
        stx.verifyRequiredSignatures()
        val item = stx.tx.outputs.single().data as ItemState
        val updateFlow = ItemUpdateFlow(item.sku, 7.DOLLARS)
        val updateFuture = a.startFlow(updateFlow)
        mockNetwork.runNetwork()
        val signedUpdateTx = updateFuture.getOrThrow()
        signedUpdateTx.verifyRequiredSignatures()
    }

    @Test(expected = IllegalArgumentException::class)
    fun flowReturnsTransactionSignedAllPartiesFollowedByPriceUpdateBadPrice() {
        val buyer1 = b.info.chooseIdentityAndCert().party
        val flow = ItemCreateFlow("Fidget spinner", "007", 6.DOLLARS, listOf(buyer1.anonymise()))
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        val stx = future.getOrThrow()
        stx.verifyRequiredSignatures()
        val item = stx.tx.outputs.single().data as ItemState
        val updateFlow = ItemUpdateFlow(item.sku, 6.DOLLARS)
        val updateFuture = a.startFlow(updateFlow)
        mockNetwork.runNetwork()
        val signedUpdateTx = updateFuture.getOrThrow()
        signedUpdateTx.verifyRequiredSignatures()
    }

    @Test
    fun flowReturnsTransactionSignedAllPartiesFollowedByNewBuyer() {
        val buyer1 = b.info.chooseIdentityAndCert().party
        val buyer2 = c.info.chooseIdentityAndCert().party

        // create item
        val flow = ItemCreateFlow("Fidget spinner", "007", 6.DOLLARS, listOf(buyer1.anonymise()))
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        val stx = future.getOrThrow()
        stx.verifyRequiredSignatures()

        // add new buyer
        val item = stx.tx.outputs.single().data as ItemState
        val updateFlow = ItemAddBuyerFlow(item.sku, listOf(buyer2.anonymise()))
        val updateFuture = a.startFlow(updateFlow)
        mockNetwork.runNetwork()
        val signedUpdateTx = updateFuture.getOrThrow()
        signedUpdateTx.verifyRequiredSignatures()
    }

    @Test
    fun flowRecordsTheSameTransactionInAllPartyVaults() {
        val buyer1 = b.info.chooseIdentityAndCert().party
        val buyer2 = c.info.chooseIdentityAndCert().party
        val flow = ItemCreateFlow("Fidget spinner", "001", 6.DOLLARS, listOf(buyer1.anonymise(), buyer2.anonymise()))
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        val stx = future.getOrThrow()
        println("Signed transaction hash: ${stx.id}")
        listOf(a, b, c).map {
            it.services.validatedTransactions.getTransaction(stx.id)
        }.forEach {
            val txHash = (it as SignedTransaction).id
            println("$txHash == ${stx.id}")
            assertEquals(stx.id, txHash)
        }
    }
}