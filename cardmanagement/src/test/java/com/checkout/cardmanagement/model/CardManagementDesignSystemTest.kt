package com.checkout.cardmanagement.model

import androidx.compose.ui.text.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Test

internal class CardManagementDesignSystemTest {

    @Test
    fun `assert getPanViewConfig with single TextStyle constructor`() = assertEquals(
        TEXT_STYLE,
        CardManagementDesignSystem(TEXT_STYLE).panViewConfig.textStyle,
    )

    @Test
    fun `assert getPinViewConfig with single TextStyle constructor`() = assertEquals(
        TEXT_STYLE,
        CardManagementDesignSystem(TEXT_STYLE).pinViewConfig.textStyle,
    )

    @Test
    fun `assert getSecurityCodeViewConfig with single TextStyle constructor`() = assertEquals(
        TEXT_STYLE,
        CardManagementDesignSystem(TEXT_STYLE).securityCodeViewConfig.textStyle,
    )

    @Test
    fun `assert configs with separated TextStyle constructor`() {
        val system = CardManagementDesignSystem(PIN_TEXT_STYLE, PAN_TEXT_STYLE, CVV_TEXT_STYLE)
        assertEquals(PIN_TEXT_STYLE, system.pinViewConfig.textStyle)
        assertEquals(PAN_TEXT_STYLE, system.panViewConfig.textStyle)
        assertEquals(CVV_TEXT_STYLE, system.securityCodeViewConfig.textStyle)
    }

    @Test
    fun `assert default pan separator with separated TextStyle constructor`() {
        val system = CardManagementDesignSystem(PIN_TEXT_STYLE, PAN_TEXT_STYLE, CVV_TEXT_STYLE)
        assertEquals(" ", system.panViewConfig.separator)
    }

    @Test
    fun `assert customised pan separator with separated TextStyle constructor`() {
        val system =
            CardManagementDesignSystem(PIN_TEXT_STYLE, PAN_TEXT_STYLE, CVV_TEXT_STYLE, "++++")
        assertEquals("++++", system.panViewConfig.separator)
    }

    @Test
    fun `assert default pan separator with single TextStyle constructor`() {
        val system = CardManagementDesignSystem(TEXT_STYLE)
        assertEquals(" ", system.panViewConfig.separator)
    }

    @Test
    fun `assert customised pan separator with single TextStyle constructor`() {
        val system = CardManagementDesignSystem(TEXT_STYLE, "++++")
        assertEquals("++++", system.panViewConfig.separator)
    }

    private companion object {
        private val TEXT_STYLE = TextStyle()
        private val PIN_TEXT_STYLE = TextStyle()
        private val PAN_TEXT_STYLE = TextStyle()
        private val CVV_TEXT_STYLE = TextStyle()
    }
}
