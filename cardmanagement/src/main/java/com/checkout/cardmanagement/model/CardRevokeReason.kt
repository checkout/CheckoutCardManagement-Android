package com.checkout.cardmanagement.model

public enum class CardRevokeReason(public val value: String) {
	LOST("reported_lost"),
	STOLEN("reported_stolen")
}
