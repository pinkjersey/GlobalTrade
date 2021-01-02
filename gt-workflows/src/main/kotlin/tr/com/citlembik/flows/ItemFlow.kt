package tr.com.citlembik.flows

import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import tr.com.citlembik.states.ItemState

abstract class ItemFlow : FlowLogic<SignedTransaction>() {
    fun getItemUsingSku(sku: String) : StateAndRef<ItemState> {
        val linearStateCriteria = QueryCriteria.LinearStateQueryCriteria(externalId = listOf(sku),
                status = Vault.StateStatus.UNCONSUMED)
        val vaultCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)
        val results = serviceHub.vaultService.queryBy<ItemState>(linearStateCriteria and vaultCriteria)
        return results.states.single()
    }
}