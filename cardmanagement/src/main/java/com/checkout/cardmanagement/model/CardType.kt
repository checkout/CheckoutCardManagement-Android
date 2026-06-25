package com.checkout.cardmanagement.model

/**
 * Type of the [Card], indicating whether it is a physical or virtual card.
 *
 * This can be used to conditionally enable or disable actions, such as Reveal PIN,
 * based on whether the card is physical or virtual.
 */
public enum class CardType {
    /** A physical card that has been manufactured and issued to the cardholder */
    PHYSICAL,

    /** A virtual card that exists only in digital form */
    VIRTUAL,

    /** An unknown card type not yet supported by this SDK version */
    UNKNOWN,
}
