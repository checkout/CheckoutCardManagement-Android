package com.checkout.cardmanagement.model

/** Card state of the [Card] */
public enum class CardState {
    /** Card can be used */
    ACTIVE,

    /** Card is recently issued and has not been activated */
    INACTIVE,

    /** Card is suspended and can not be used until activated again. */
    SUSPENDED,

    /** Card is revoked and can no longer be used or activated */
    REVOKED,
}
