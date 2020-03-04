package net.corda.experimental.issuerwhitelist.contract

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.FungibleState
import net.corda.core.contracts.LinearState
import net.corda.core.internal.readFully
import net.corda.core.serialization.deserialize
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.toBase58String
import java.security.PublicKey

object NotaryWhitelistVerifier {
    const val NOTARY_WHITELIST_JAR_ENTRY = "whitelist"
    private val log = loggerFor<NotaryWhitelistVerifier>()

    /**
     * Verifies that:
     * - A notary whitelist attachment exists for each state that implements [NotaryRestrictedState]
     * - Each notary whitelist for a state is signed by the key specified in [NotaryRestrictedState]
     * - Optionally checks that the [issuerKey] does not get modified when transitioning a state.
     */
    fun verify(tx: LedgerTransaction, checkIssuerUnmodified: Boolean = true) {
        val restrictedStates =
                tx.inputStates.filterIsInstance<NotaryRestrictedState>() + tx.outputStates.filterIsInstance<NotaryRestrictedState>()

        val notaryWhitelistAttachments = getWhitelists(tx.attachments)
        val attachmentsByStateType = notaryWhitelistAttachments.groupBy { it.first.stateType }

        for (state in restrictedStates) {
            log.info("State issuer public key: ${state.whitelistSigningKey}")
            val whitelists = attachmentsByStateType[state::class.java]
                    ?: throw Exception("Notary whitelist not attached for restricted state type $state")

            val whitelist = whitelists.single { state.whitelistSigningKey.toBase58String() in (it.second.map { it.toBase58String() }) }.first
            require(tx.notary in whitelist.notaries) {
                "Transaction notary (${tx.notary}) is not in the issuer-specified notary whitelist: ${whitelist.notaries.joinToString()}"
            }
        }

        if (checkIssuerUnmodified) checkIssuerKeysUnmodified(tx)
    }

    /**
     * For every input state, find the corresponding output state(s).
     *
     * We must check that the issuer key remains unmodified in the outputs,
     * to make sure the issuer notary whitelist is still enforced.
     *
     * In the general case it's not possible to know which output states correspond to which input states,
     * but we can implement checks for [LinearState] and [FungibleState], and assume that for all other state
     * types, outputs of the same class will form the state group.
     */
    private fun checkIssuerKeysUnmodified(tx: LedgerTransaction) {
        for (input in tx.inputStates) {
            if (input !is NotaryRestrictedState) continue
            val outputGroup = findOutputGroupForInput(input, tx)
            require(outputGroup.all { it is NotaryRestrictedState && it.whitelistSigningKey == input.whitelistSigningKey }) {
                "Cannot modify the issuer key for restricted transactions"
            }
        }
    }

    private fun findOutputGroupForInput(input: ContractState, tx: LedgerTransaction): List<ContractState> {
        return when (input) {
            is LinearState -> {
                listOf(tx.outputStates.single { (it as? LinearState)?.linearId == input.linearId })
            }
            is FungibleState<*> -> {
                tx.outputStates.filter { ((it) as? FungibleState<*>)?.amount?.token == input.amount.token }
            }
            else -> {
                tx.outputStates.filter { it::class == input::class }
            }
        }
    }

    private fun getWhitelists(attachments: List<Attachment>): List<Pair<NotaryWhitelist, List<PublicKey>>> {
        val whitelistsAndSignerKeys = mutableListOf<Pair<NotaryWhitelist, List<PublicKey>>>()
        for (attachment in attachments) {
            if (attachment is ContractAttachment) continue
            else {
                tryParseWhitelistAttachment(attachment, whitelistsAndSignerKeys)
            }
        }
        return whitelistsAndSignerKeys
    }

    private fun tryParseWhitelistAttachment(attachment: Attachment, whitelistsAndSignerKeys: MutableList<Pair<NotaryWhitelist, List<PublicKey>>>) {
        try {
            val jar = attachment.openAsJAR()
            do {
                val entry = jar.nextJarEntry
                if (entry.name == NOTARY_WHITELIST_JAR_ENTRY) {
                    val bytes = jar.readFully()
                    val whitelistAndSignerKeys = bytes.deserialize<NotaryWhitelist>() to attachment.signerKeys
                    whitelistsAndSignerKeys.add(whitelistAndSignerKeys)
                }
            } while (jar.available() == 1 && entry != null)
        } catch (e: Exception) {
            log.info("Failed to parse attachment entry: ${e.message}")
        }
    }
}