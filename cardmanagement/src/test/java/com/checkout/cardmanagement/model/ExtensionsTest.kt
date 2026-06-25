package com.checkout.cardmanagement.model

import org.junit.Assert.assertEquals
import org.junit.Test

internal class ExtensionsTest {
    @Test
    fun `toNetworkCardState should map all CardState values correctly`() {
        assertEquals(
            com.checkout.cardnetwork.data.dto.CardState.ACTIVE,
            CardState.ACTIVE.toNetworkCardState(),
        )
        assertEquals(
            com.checkout.cardnetwork.data.dto.CardState.INACTIVE,
            CardState.INACTIVE.toNetworkCardState(),
        )
        assertEquals(
            com.checkout.cardnetwork.data.dto.CardState.SUSPENDED,
            CardState.SUSPENDED.toNetworkCardState(),
        )
        assertEquals(
            com.checkout.cardnetwork.data.dto.CardState.REVOKED,
            CardState.REVOKED.toNetworkCardState(),
        )
    }

    @Test
    fun `fromNetworkCardState should map INVALID to INACTIVE as fallback`() {
        assertEquals(
            CardState.INACTIVE,
            com.checkout.cardnetwork.data.dto.CardState.INVALID
                .fromNetworkCardState(),
        )
    }

    @Test
    fun `toCardType should map physical string to PHYSICAL`() {
        assertEquals(CardType.PHYSICAL, "physical".toCardType())
    }

    @Test
    fun `toCardType should map virtual string to VIRTUAL`() {
        assertEquals(CardType.VIRTUAL, "virtual".toCardType())
    }

    @Test
    fun `toCardType should be case insensitive`() {
        assertEquals(CardType.PHYSICAL, "Physical".toCardType())
        assertEquals(CardType.PHYSICAL, "PHYSICAL".toCardType())
        assertEquals(CardType.VIRTUAL, "Virtual".toCardType())
        assertEquals(CardType.VIRTUAL, "VIRTUAL".toCardType())
    }

    @Test
    fun `toCardType should map unknown values to UNKNOWN`() {
        assertEquals(CardType.UNKNOWN, "prepaid".toCardType())
        assertEquals(CardType.UNKNOWN, "".toCardType())
        assertEquals(CardType.UNKNOWN, "other".toCardType())
    }

    @Test
    fun `toCardScheme should map visa string to VISA`() {
        assertEquals(CardScheme.VISA, "visa".toCardScheme())
    }

    @Test
    fun `toCardScheme should map mastercard string to MASTERCARD`() {
        assertEquals(CardScheme.MASTERCARD, "mastercard".toCardScheme())
    }

    @Test
    fun `toCardScheme should be case insensitive`() {
        assertEquals(CardScheme.VISA, "Visa".toCardScheme())
        assertEquals(CardScheme.VISA, "VISA".toCardScheme())
        assertEquals(CardScheme.MASTERCARD, "Mastercard".toCardScheme())
        assertEquals(CardScheme.MASTERCARD, "MASTERCARD".toCardScheme())
    }

    @Test
    fun `toCardScheme should map null to UNKNOWN`() {
        assertEquals(CardScheme.UNKNOWN, null.toCardScheme())
    }

    @Test
    fun `toCardScheme should map unknown values to UNKNOWN`() {
        assertEquals(CardScheme.UNKNOWN, "amex".toCardScheme())
        assertEquals(CardScheme.UNKNOWN, "".toCardScheme())
        assertEquals(CardScheme.UNKNOWN, "other".toCardScheme())
    }
}
