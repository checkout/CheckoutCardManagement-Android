package com.checkout.cardmanagement

import com.checkout.cardmanagement.model.Card
import com.checkout.cardmanagement.model.CardExpiryDate
import com.checkout.cardmanagement.model.CardState.ACTIVE
import com.checkout.cardmanagement.model.NetworkCard
import com.checkout.cardnetwork.data.dto.CardList
import com.checkout.cardnetwork.data.dto.CardState

internal object Fixtures {
	internal val NETWORK_CARD: NetworkCard = NetworkCard(
		cardholderId = "crh_shw5giae4mjufep6jdrdfvz5vu",
		createdDate = "2023-02-01 T16:39:13.76Z",
		displayName = "CARD_HOLDER_NAME",
		expiryMonth = "2",
		expiryYear = "2025",
		id = "crd_a3o34dts4geuvp7dg3wwuh27wy",
		panLast4Digits = "0243",
		lastModifiedDate = "2023-02 - 01T16:39:13.76Z",
		reference = null,
		state = CardState.ACTIVE,
		type = "virtual",
		isSingleUse = false
	)
	internal val NETWORK_CARD_LIST = CardList(
		listOf(NETWORK_CARD, NETWORK_CARD, NETWORK_CARD)
	)

	internal fun createCard(manager: CheckoutCardManager) = Card(
		state = ACTIVE,
		panLast4Digits = "8888",
		expiryDate = CardExpiryDate("05", "2045"),
		cardholderName = "CARD_HOLDER_NAME",
		id = "1234567890",
		manager = manager
	)
}
