package com.checkout.cardmanagement.model

import com.checkout.cardnetwork.data.core.CardDigitizationState

public enum class DigitizationState {
    /** Card is already digitized */
    DIGITIZED,

    /** Card is not digitized */
    NOT_DIGITIZED,

    /** Activation is required for card digitization on mobile device.  Integrator should request end user to activate the card. */
    PENDING_IDV,

    /** Digitization currently in progress */
    DIGITIZATION_IN_PROGRESS,
    ;

    internal companion object {
        internal fun from(receivedState: CardDigitizationState): DigitizationState {
            return when (receivedState) {
                CardDigitizationState.DIGITIZED -> DIGITIZED
                CardDigitizationState.NOT_DIGITIZED -> NOT_DIGITIZED
                CardDigitizationState.PENDING_IDV -> PENDING_IDV
                CardDigitizationState.DIGITIZATION_IN_PROGRESS -> DIGITIZATION_IN_PROGRESS
            }
        }
    }
}
