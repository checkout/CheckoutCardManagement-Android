package com.checkout.cardmanagement.model

/** Expiry date of a [Card] */
public data class CardExpiryDate(
	/** Expiry month for the card */
	val month: String,
	/** Expiry year for the card */
	val year: String
)
