package tr.com.citlembik.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.Requirements.using
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.node.services.Vault
import net.corda.core.node.services.Vault.StateStatus
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import tr.com.citlembik.contracts.ItemContract
import tr.com.citlembik.states.ItemState


/**
 * This flow advertises the item to a new potential buyer or buyers
 */
@InitiatingFlow
@StartableByRPC
class ItemAddBuyerFlow(private val sku: String, private val newPotentialBuyers: List<AnonymousParty>) :
        ItemFlow() {
    override val progressTracker = ProgressTracker()



    @Suspendable
    override fun call() : SignedTransaction {
        val itemStateAndRef =  getItemUsingSku(sku)
        val inputItem = itemStateAndRef.state.data

        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        // only the seller can add a buyer
        if (ourIdentity != inputItem.seller) {
            throw java.lang.IllegalArgumentException("Only the seller can make changes to an item.")
        }

        // ensure seller is not in the list of buyers
        if (newPotentialBuyers.contains(inputItem.seller.anonymise())) {
            throw IllegalArgumentException("Seller is part of the new buyer's list.")
        }
        val mergedBuyersList = inputItem.potentialBuyers.union(newPotentialBuyers).toList()
        val output = inputItem.withNewBuyers(mergedBuyersList)

        val addBuyerCommand = Command(ItemContract.Commands.AddBuyer(), output.participants.map { it.owningKey })

        val builder = TransactionBuilder(notary = notary)
        builder.addInputState(itemStateAndRef)
        builder.addOutputState(output)
        builder.addCommand(addBuyerCommand)
        builder.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(builder)

        val sessions = (output.participants - ourIdentity).map { initiateFlow(it) }.toSet()
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        return subFlow(FinalityFlow(stx, sessions))
    }
}

@InitiatedBy(ItemAddBuyerFlow::class)
class ItemAddBuyerResponder(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {
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