package com.checkout.cardmanagement.model

/**
 * Represents payment card information returned from the SDK.
 *
 * This data class contains card details for display and management purposes, including
 * the card's current state, masked PAN details, expiry information, and cardholder name.
 * Card instances are obtained by calling [com.checkout.cardmanagement.CheckoutCardManager.getCards] and can be used
 * to perform operations such as state management (activate, suspend, revoke) and push
 * provisioning to digital wallets.
 *
 * @see com.checkout.cardmanagement.CheckoutCardManager.getCards
 * @see CardState
 * @see CardExpiryDate
 */
public data class Card(
    /** Current state of the card */
    public val state: CardState = CardState.INACTIVE,
    /** Last 4 digits from the long card number */
    public val panLast4Digits: String,
    /** Expiry date for the card */
    public val expiryDate: CardExpiryDate,
    /** Name of the cardholder */
    public val cardholderName: String,
    /** Unique identifier for this card, used for card operations and tracking (not the card PAN) */
    public val id: String,
    /** A reference to the manager is required to enable sharing of the design system and the card service
     *	Enables object to carry operations that depend on it
     */
    internal val manager: com.checkout.cardmanagement.CheckoutCardManager,
) {
    /** Valid state transitions available for this card based on its current [state] */
    public val possibleStateChanges: List<CardState> by lazy { state.getPossibleStateChanges() }

    internal companion object {
        internal fun fromNetworkCard(
            networkCard: NetworkCard,
            manager: com.checkout.cardmanagement.CheckoutCardManager,
        ) = Card(
            state = networkCard.state.fromNetworkCardState(),
            panLast4Digits = networkCard.panLast4Digits,
            expiryDate =
                CardExpiryDate(
                    month = networkCard.expiryMonth,
                    year = networkCard.expiryYear,
                ),
            cardholderName = networkCard.displayName ?: "",
            id = networkCard.id,
            manager = manager,
        )
    }
}

internal typealias NetworkCard = com.checkout.cardnetwork.data.dto.Card
