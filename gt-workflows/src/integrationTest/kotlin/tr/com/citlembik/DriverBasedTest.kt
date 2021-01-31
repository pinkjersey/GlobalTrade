package tr.com.citlembik

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultTrackBy
import net.corda.core.node.services.Vault
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.testing.core.*
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.startNode
import org.junit.Test
import tr.com.citlembik.flows.ItemCreateFlow
import tr.com.citlembik.states.ItemState
import java.util.*
import java.util.concurrent.Future
import kotlin.test.assertEquals

class DriverBasedTest {
    private val sellerName = CordaX500Name("Seller", "", "GB")
    private val buyerAName = CordaX500Name("BuyerA", "", "US")
    private val buyerBName = CordaX500Name("BuyerB", "", "US")
    private val buyerCName = CordaX500Name("BuyerC", "", "US")

    @Test
    fun `should_add_item`() = withDriver {
        val sellerUser = User("sellerUser", "testPassword1", permissions = setOf(
                startFlow<ItemCreateFlow>(),
                invokeRpc("nodeInfo"),
                invokeRpc("wellKnownPartyFromX500Name"),
                invokeRpc("wellKnownPartyFromAnonymous"),
                invokeRpc("vaultTrackBy")
        ))

        val buyerAUser = User("buyerAUser", "testPassword1", permissions = setOf(
                startFlow<ItemCreateFlow>(),
                invokeRpc("nodeInfo"),
                invokeRpc("wellKnownPartyFromX500Name"),
                invokeRpc("wellKnownPartyFromAnonymous"),
                invokeRpc("vaultTrackBy")
        ))

        val sellerHandle = startNode(providedName = sellerName, rpcUsers = listOf(sellerUser)).getOrThrow()
        val buyerAHandle = startNode(providedName = buyerAName, rpcUsers = listOf(buyerAUser)).getOrThrow()

        val resolvedNameBuyerA = sellerHandle.resolveName(buyerAName)
        assertEquals(buyerAName, resolvedNameBuyerA)
        assertEquals(sellerName, buyerAHandle.resolveName(sellerName))
        val anonymousBuyerA = buyerAHandle.rpc.nodeInfo().singleIdentity().anonymise()
        val resolveFromAnonymous = sellerHandle.resolveFromAnonymous(anonymousBuyerA)
        assertEquals(buyerAName, resolveFromAnonymous)

        val sellerClient = CordaRPCClient(sellerHandle.rpcAddress)
        val sellerProxy: CordaRPCOps = sellerClient.start("sellerUser", "testPassword1").proxy

        val buyerAClient = CordaRPCClient(buyerAHandle.rpcAddress)
        val buyerAProxy: CordaRPCOps = buyerAClient.start(buyerAUser.username, buyerAUser.password).proxy

        val buyerAUpdates = buyerAProxy.vaultTrackBy<ItemState>().updates
        sellerProxy.startFlow(::ItemCreateFlow, "testItem", "testSku", 1.DOLLARS, listOf(anonymousBuyerA))

        buyerAUpdates.expectEvents {
            expect { create ->
                println("BuyerA got a vault update of $create")
                assertEquals("testSku", create.produced.first().state.data.sku)
            }
        }
    }

    // Runs a test inside the Driver DSL, which provides useful functions for starting nodes, etc.
    private fun withDriver(test: DriverDSL.() -> Unit) = driver(
        DriverParameters(isDebug = true, startNodesInProcess = true)
    ) { test() }

    // Makes an RPC call to retrieve another node's name from the network map.
    private fun NodeHandle.resolveName(name: CordaX500Name) = rpc.wellKnownPartyFromX500Name(name)!!.name

    private fun NodeHandle.resolveFromAnonymous(name: AbstractParty) = rpc.wellKnownPartyFromAnonymous(name)!!.name

    // Resolves a list of futures to a list of the promised values.
    private fun <T> List<Future<T>>.waitForAll(): List<T> = map { it.getOrThrow() }

    // Starts multiple nodes simultaneously, then waits for them all to be ready.
    private fun DriverDSL.startNodes(vararg identities: TestIdentity) = identities
        .map { startNode(providedName = it.name) }
        .waitForAll()
}