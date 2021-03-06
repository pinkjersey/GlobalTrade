package tr.com.citlembik.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import tr.com.citlembik.contracts.ItemContract
import tr.com.citlembik.states.ItemState
import java.lang.IllegalArgumentException
import java.util.*

@InitiatingFlow
@StartableByRPC
class ItemPriceChangeFlow(private val sku: String, private val newPrice: Amount<Currency>) : ItemFlow() {

    @Suspendable
    override fun call(): SignedTransaction {
        val itemStateAndRef =  getItemUsingSku(sku)
        val inputItem = itemStateAndRef.state.data

        if (ourIdentity != inputItem.seller) {
            throw IllegalArgumentException("Item price can only be adjusted by the seller.")
        }

        if (newPrice == inputItem.price) {
            throw IllegalArgumentException("Item price must change.")
        }

        val outputItem = inputItem.withNewPrice(newPrice)

        val signers = inputItem.participants.map { it.owningKey }
        val updateCommand = Command(ItemContract.Commands.UpdatePrice(), signers)

        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        val builder = TransactionBuilder(notary = notary)

        builder.withItems(itemStateAndRef, StateAndContract(outputItem, ItemContract.ID), updateCommand)

        builder.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(builder)

        val sessions = (inputItem.participants - ourIdentity).map { initiateFlow(it) }.toSet()
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        return subFlow(FinalityFlow(stx, sessions))
    }

}

@InitiatedBy(ItemPriceChangeFlow::class)
class ItemPriceChangeResponder(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {
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