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
}
