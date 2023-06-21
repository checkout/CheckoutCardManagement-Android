package com.checkout.cardmanagement.logging

import com.checkout.cardmanagement.model.CardManagementDesignSystem
import com.checkout.cardmanagement.model.CardState

/** Analytics event wrappers */
internal sealed class LogEvent {
	/** Describe an initialisation of the CardManager */
	internal data class Initialized(val designSystem: CardManagementDesignSystem) : LogEvent()

	/** Describe a successful retrieval of the card list */
	internal data class CardList(val idSuffixes: List<String>) : LogEvent()

	/** Describe a successful call to retrieve a pin */
	internal data class GetPin(val idLast4: String, val cardState: CardState) : LogEvent()

	/** Describe a successful call to retrieve a card number */
	internal data class GetPan(val idLast4: String, val cardState: CardState) : LogEvent()

	/** Describe a successful call to retrieve a security code */
	internal data class GetCVV(val idLast4: String, val cardState: CardState) : LogEvent()

	/** Describe a successful call to retrieve a pan and a security code */
	internal data class GetPanCVV(val idLast4: String, val cardState: CardState) : LogEvent()

	/** Describe a successful event where a card state change was completed */
	internal data class StateManagement(
		val idLast4: String,
		val originalState: CardState,
		val requestedState: CardState,
		val reason: String?
	) : LogEvent()

	/** Describe a Push Provisioning event */
	internal data class PushProvisioning(
		val last4CardID: String,
		val last4CardholderID: String
	) : LogEvent()

	/** Describe an unexpected but non critical failure */
	internal data class Failure(val source: String, val error: Throwable) : LogEvent()
}

internal object LogEventSource {
	internal const val GET_CARDS = "Get Cards"
	internal const val GET_PAN = "Get Pan"
	internal const val GET_PIN = "Get Pin"
	internal const val GET_CVV = "Get Security Code"
	internal const val GET_PAN_AND_CVV = "Get Pan and SecurityCode"
	internal const val PUSH_PROVISIONING = "Push Provisioning"
	internal const val ACTIVATE_CARD = "Activate Card"
	internal const val SUSPEND_CARD = "Suspend Card"
	internal const val REVOKE_CARD = "Revoke Card"
}
