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
        val seller = a.info.chooseIdentityAndCert().party
        val buyer = b.info.chooseIdentityAndCert().party
        val item = ItemState(seller, "Fidget spinner", "001", 6.DOLLARS, listOf(buyer.anonymise()))
        val flow = ItemCreateFlow(item)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        // Return the unsigned(!) SignedTransaction object from the IOUIssueFlow.
        val ptx: SignedTransaction = future.getOrThrow()
        // Print the transaction for debugging purposes.
        println(ptx.tx)
        // Check the transaction is well formed...
        // No outputs, one input IOUState and a command with the right properties.
        assert(ptx.tx.inputs.isEmpty())
        assert(ptx.tx.outputs.single().data is ItemState)
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
        val badItem = ItemState(seller, "", "001", 6.DOLLARS, listOf(buyer.anonymise()))
        val futureOne = a.startFlow(ItemCreateFlow(badItem))
        mockNetwork.runNetwork()
        assertFailsWith<TransactionVerificationException> { futureOne.getOrThrow() }
        // Check that an IOU with the same participants fails.
        val sellerIsBuyerItem = ItemState(seller, "Fidget spinner", "001", 6.DOLLARS, listOf(seller.anonymise()))
        val futureTwo = a.startFlow(ItemCreateFlow(sellerIsBuyerItem))
        mockNetwork.runNetwork()
        assertFailsWith<TransactionVerificationException> { futureTwo.getOrThrow() }
        // Check a good IOU passes.
        val item = ItemState(seller, "Fidget spinner", "001", 6.DOLLARS, listOf(buyer.anonymise()))
        val futureThree = a.startFlow(ItemCreateFlow(item))
        mockNetwork.runNetwork()
        futureThree.getOrThrow()
    }

    @Test
    fun flowReturnsTransactionSignedAllParties() {
        val seller = a.info.chooseIdentityAndCert().party
        val buyer1 = b.info.chooseIdentityAndCert().party
        val buyer2 = c.info.chooseIdentityAndCert().party
        val item = ItemState(seller, "Fidget spinner", "001", 6.DOLLARS, listOf(buyer1.anonymise(), buyer2.anonymise()))
        val flow = ItemCreateFlow(item)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        val stx = future.getOrThrow()
        stx.verifyRequiredSignatures()
    }

    @Test
    fun flowReturnsTransactionSignedAllPartiesFollowedByPriceUpdate() {
        val seller = a.info.chooseIdentityAndCert().party
        val buyer1 = b.info.chooseIdentityAndCert().party
        val item = ItemState(seller, "Fidget spinner", "007", 6.DOLLARS, listOf(buyer1.anonymise()))
        val flow = ItemCreateFlow(item)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        val stx = future.getOrThrow()
        stx.verifyRequiredSignatures()
        val updateFlow = ItemUpdateFlow(item.linearId, 7.DOLLARS)
        val updateFuture = a.startFlow(updateFlow)
        mockNetwork.runNetwork()
        val signedUpdateTx = updateFuture.getOrThrow()
        signedUpdateTx.verifyRequiredSignatures()
    }

    @Test(expected = IllegalArgumentException::class)
    fun flowReturnsTransactionSignedAllPartiesFollowedByPriceUpdateBadPrice() {
        val seller = a.info.chooseIdentityAndCert().party
        val buyer1 = b.info.chooseIdentityAndCert().party
        val item = ItemState(seller, "Fidget spinner", "007", 6.DOLLARS, listOf(buyer1.anonymise()))
        val flow = ItemCreateFlow(item)
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        val stx = future.getOrThrow()
        stx.verifyRequiredSignatures()
        val updateFlow = ItemUpdateFlow(item.linearId, 6.DOLLARS)
        val updateFuture = a.startFlow(updateFlow)
        mockNetwork.runNetwork()
        val signedUpdateTx = updateFuture.getOrThrow()
        signedUpdateTx.verifyRequiredSignatures()
    }

    @Test
    fun flowRecordsTheSameTransactionInAllPartyVaults() {
        val seller = a.info.chooseIdentityAndCert().party
        val buyer1 = b.info.chooseIdentityAndCert().party
        val buyer2 = c.info.chooseIdentityAndCert().party
        val item = ItemState(seller, "Fidget spinner", "001", 6.DOLLARS, listOf(buyer1.anonymise(), buyer2.anonymise()))
        val flow = ItemCreateFlow(item)
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