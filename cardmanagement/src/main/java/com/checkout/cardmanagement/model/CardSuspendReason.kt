package com.checkout.cardmanagement.model

public enum class CardSuspendReason(public val value: String) {
	LOST("suspected_lost"),
	STOLEN("suspected_stolen")
}
