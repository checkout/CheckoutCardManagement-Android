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
        assertEquals(CardType.VIRTUAL, card.type)
        assertEquals(CardScheme.VISA, card.cardScheme)
    }

    @Test
    fun `fromNetworkCard should map physical card type correctly`() {
        val networkCard = Fixtures.NETWORK_CARD_PHYSICAL
        val card =
            Card.fromNetworkCard(
                networkCard = networkCard,
                manager = cardManager,
            )

        assertEquals(CardType.PHYSICAL, card.type)
    }

    @Test
    fun `fromNetworkCard should map unknown card type to UNKNOWN`() {
        val networkCard = Fixtures.NETWORK_CARD.copy(type = "prepaid")
        val card =
            Card.fromNetworkCard(
                networkCard = networkCard,
                manager = cardManager,
            )

        assertEquals(CardType.UNKNOWN, card.type)
    }

    @Test
    fun `card type defaults to UNKNOWN`() {
        val card =
            Card(
                panLast4Digits = "8888",
                expiryDate = CardExpiryDate("05", "2045"),
                cardholderName = "CARD_HOLDER_NAME",
                id = "1234567890",
                manager = cardManager,
            )
        assertEquals(CardType.UNKNOWN, card.type)
    }

    @Test
    fun `fromNetworkCard should map visa scheme correctly`() {
        val networkCard = Fixtures.NETWORK_CARD
        val card = Card.fromNetworkCard(networkCard = networkCard, manager = cardManager)

        assertEquals(CardScheme.VISA, card.cardScheme)
    }

    @Test
    fun `fromNetworkCard should map mastercard scheme correctly`() {
        val networkCard = Fixtures.NETWORK_CARD_PHYSICAL
        val card = Card.fromNetworkCard(networkCard = networkCard, manager = cardManager)

        assertEquals(CardScheme.MASTERCARD, card.cardScheme)
    }

    @Test
    fun `fromNetworkCard should map null scheme to UNKNOWN`() {
        val networkCard = Fixtures.NETWORK_CARD.copy(scheme = null)
        val card = Card.fromNetworkCard(networkCard = networkCard, manager = cardManager)

        assertEquals(CardScheme.UNKNOWN, card.cardScheme)
    }

    @Test
    fun `fromNetworkCard should map unrecognised scheme to UNKNOWN`() {
        val networkCard = Fixtures.NETWORK_CARD.copy(scheme = "amex")
        val card = Card.fromNetworkCard(networkCard = networkCard, manager = cardManager)

        assertEquals(CardScheme.UNKNOWN, card.cardScheme)
    }

    @Test
    fun `cardScheme defaults to UNKNOWN`() {
        val card =
            Card(
                panLast4Digits = "8888",
                expiryDate = CardExpiryDate("05", "2045"),
                cardholderName = "CARD_HOLDER_NAME",
                id = "1234567890",
                manager = cardManager,
            )
        assertEquals(CardScheme.UNKNOWN, card.cardScheme)
    }
}
