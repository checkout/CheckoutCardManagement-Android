package com.checkout.cardmanagement.logging

import com.checkout.cardmanagement.model.CardManagementDesignSystem
import com.checkout.cardmanagement.model.CardState
import com.checkout.cardmanagement.model.DigitizationState

/** Analytics event wrappers */
internal sealed class LogEvent {
    /** Describe an initialisation of the CardManager */
    internal data class Initialized(
        val designSystem: CardManagementDesignSystem,
    ) : LogEvent()

    /** Describe a successful retrieval of the card list */
    internal data class CardList(
        val cardIds: List<String>,
        val requestedStatuses: Set<CardState>,
    ) : LogEvent()

    /** Describe a successful call to retrieve a pin */
    internal data class GetPin(
        val cardId: String,
        val cardState: CardState,
    ) : LogEvent()

    /** Describe a successful call to retrieve a card number */
    internal data class GetPan(
        val cardId: String,
        val cardState: CardState,
    ) : LogEvent()

    /** Describe a successful call to retrieve a security code */
    internal data class GetCVV(
        val cardId: String,
        val cardState: CardState,
    ) : LogEvent()

    /** Describe a successful call to retrieve a pan and a security code */
    internal data class GetPanCVV(
        val cardId: String,
        val cardState: CardState,
    ) : LogEvent()

    /** Describe a successful call to copy PAN to clipboard */
    internal data class CopyPan(
        val cardId: String,
        val cardState: CardState,
    ) : LogEvent()

    /** Describe a successful event where a card state change was completed */
    internal data class StateManagement(
        val cardId: String,
        val originalState: CardState,
        val requestedState: CardState,
        val reason: String?,
    ) : LogEvent()

    /** Describe a Configure Push Provisioning event */
    internal data class ConfigurePushProvisioning(
        val cardholderId: String,
    ) : LogEvent()

    /** Describe a Get Card Digitization State event */
    internal data class GetCardDigitizationState(
        val cardId: String,
        val digitizationState: DigitizationState,
    ) : LogEvent()

    /** Describe a Push Provisioning event */
    internal data class PushProvisioning(
        val cardId: String,
    ) : LogEvent()

    /** Describe an unexpected but non critical failure */
    internal data class Failure(
        val source: String,
        val error: Throwable,
    ) : LogEvent()
}

internal object LogEventSource {
    internal const val GET_CARDS = "Get Cards"
    internal const val GET_PAN = "Get Pan"
    internal const val GET_PIN = "Get Pin"
    internal const val GET_CVV = "Get Security Code"
    internal const val GET_PAN_AND_CVV = "Get Pan and SecurityCode"
    internal const val COPY_PAN = "Copy Pan"
    internal const val CONFIGURE_PUSH_PROVISIONING = "Configure Push Provisioning"
    internal const val GET_CARD_DIGITIZATION_STATE = "Get Card Digitization State"
    internal const val PUSH_PROVISIONING = "Push Provisioning"
    internal const val ACTIVATE_CARD = "Activate Card"
    internal const val SUSPEND_CARD = "Suspend Card"
    internal const val REVOKE_CARD = "Revoke Card"
}
