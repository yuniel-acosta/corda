# Attachment-based issuer notary whitelist PoC

To support issuer notary whitelists a state has to implement the ```NotaryRestrictedState``` interface, which requires specifying a signing key for whitelist.

```kotlin
interface NotaryRestrictedState {
    val whitelistSigningKey: PublicKey
}
```

Every transaction then has to include a signed whitelist as an attachment.

Additionally the contract for this state has to invoke notary whitelist-specific verification logic during verify:

```kotlin
RestrictedNotaryWhitelistVerifier.verify(tx)
```

This checks that a correct signed whitelist is attached and the transaction notary is within the whitelist.
It also checks that during state transition the signing key remains unmodified. 
However, in some cases it may be required for the contract to specify its own logic for verifying that the signing key is not modified, as the helper logic may not detect input-output state groups
correctly for all possible scenarios.

When issuing a state, the issuer has to create and sign a jar containing the serialized whitelist.
A mechanism is also required for distributing any updates to the whitelist.

# Example


```kotlin
class MyContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        // Each contract using RestrictedNotaryState(s) has to manually invoke the verification logic.
        RestrictedNotaryWhitelistVerifier.verify(tx)
    }

    interface Commands : CommandData {
        class Move : TypeOnlyCommandData()
        class Issue : TypeOnlyCommandData()
    }
}

@BelongsToContract(MyContract::class)
data class MyState(
        override val whitelistSigningKey: PublicKey,
        override val owner: AbstractParty,
        override val participants: List<AbstractParty>
) : OwnableState, NotaryRestrictedState {
    override fun withNewOwner(newOwner: AbstractParty): CommandAndState {
        return CommandAndState(MyContract.Commands.Move(), copy(owner = newOwner, participants = listOf(newOwner)))
    }
}
```
