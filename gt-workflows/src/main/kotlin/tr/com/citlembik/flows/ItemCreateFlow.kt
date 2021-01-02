package tr.com.citlembik.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import tr.com.citlembik.contracts.ItemContract
import tr.com.citlembik.states.ItemState
import java.util.*
import kotlin.math.sign

/**
 * This flow creates a new item
 */
@InitiatingFlow
@StartableByRPC
class ItemCreateFlow(private val itemName: String, private val itemSku: String,
                     private val itemPrice: Amount<Currency>, private val potentialBuyers: List<AnonymousParty>) : ItemFlow() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call() : SignedTransaction {
        val state = ItemState(ourIdentity, itemName, itemSku, itemPrice, potentialBuyers)

        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        val createCommand = Command(ItemContract.Commands.Create(), state.participants.map { it.owningKey })

        val builder = TransactionBuilder(notary = notary)
        builder.addOutputState(state)
        builder.addCommand(createCommand)
        builder.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(builder)

        val sessions = (state.participants - ourIdentity).map { initiateFlow(it) }.toSet()
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        return subFlow(FinalityFlow(stx, sessions))
    }
}


@InitiatedBy(ItemCreateFlow::class)
class ItemCreateResponder(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call() : SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an Item transaction" using (output is ItemState)
            }
        }
        val txWeJustSignedId = subFlow(signedTransactionFlow)
        return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = txWeJustSignedId.id))
    }
}
