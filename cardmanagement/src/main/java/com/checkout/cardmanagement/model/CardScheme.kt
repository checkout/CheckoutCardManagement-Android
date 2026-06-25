package com.checkout.cardmanagement.model

/**
 * Payment scheme of the [Card].
 *
 * This can be used to conditionally enable or disable actions based on the card's scheme,
 * such as Visa tokenization flows.
 */
public enum class CardScheme {
    /** A Visa card */
    VISA,

    /** A Mastercard card */
    MASTERCARD,

    /** A card scheme not yet supported by this SDK version */
    UNKNOWN,
}
