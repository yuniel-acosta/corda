package net.corda.core.contracts

import net.corda.core.crypto.SecureHash

/**
 * Interface that should be implemented by any [Contract] that wants to generate a transaction summary
 * to be present in a [WireTransaction].
 */
interface DescribableContract {

    /**
     * Used to construct a human-readable summary of the transaction that can be determined from the input, output, commands and attachments.
     * If this is to be sent over Firebase the maximum size of the list of strings is 625 bytes which equates to 15 elements with each
     * element being 240 characters long.
     */
    fun describeTransaction(
            inputs: List<TransactionState<ContractState>>,
            outputs: List<TransactionState<ContractState>>,
            commands: List<CommandData>,
            attachments: List<SecureHash>): List<String>
}