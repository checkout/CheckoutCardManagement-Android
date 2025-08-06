package com.checkout.cardmanagement.model

/** General card details */
public data class Card(
    /** Current state of the card */
    public val state: CardState = CardState.INACTIVE,
    /** Last 4 digits from the long card number */
    public val panLast4Digits: String,
    /** Expiry date for the card */
    public val expiryDate: CardExpiryDate,
    /** Name of the cardholder */
    public val cardholderName: String,
    /** Identifier used to identify object for external operations */
    public val id: String,
    /** A reference to the manager is required to enable sharing of the design system and the card service
     *	Enables object to carry operations that depend on it
     */
    internal val manager: com.checkout.cardmanagement.CheckoutCardManager,
) {
    /** Possible [CardState] to change */
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
