package com.checkout.cardmanagement.model

import com.checkout.cardmanagement.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

internal class CardTest {
    private val cardManager: com.checkout.cardmanagement.CheckoutCardManager = mock()

    @Test
    fun `card state is default to Inactive`() {
        val card =
            Card(
                panLast4Digits = "8888",
                expiryDate = CardExpiryDate("05", "2045"),
                cardholderName = "CARD_HOLDER_NAME",
                id = "1234567890",
                manager = cardManager,
            )
        assertEquals(card.state, CardState.INACTIVE)
    }

    @Test
    fun `fromNetworkCard parser should parse every field properly`() {
        val networkCard = Fixtures.NETWORK_CARD
        val card =
            Card.fromNetworkCard(
                networkCard = networkCard,
                manager = cardManager,
            )

        assertEquals(networkCard.state.name, card.state.name)
        assertEquals(networkCard.panLast4Digits, card.panLast4Digits)
        assertEquals(networkCard.expiryYear, card.expiryDate.year)
        assertEquals(networkCard.expiryMonth, card.expiryDate.month)
        assertEquals(networkCard.displayName, card.cardholderName)
        assertEquals(networkCard.id, card.id)
    }
}
