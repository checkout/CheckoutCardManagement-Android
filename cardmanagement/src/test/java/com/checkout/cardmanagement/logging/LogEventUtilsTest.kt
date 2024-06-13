package com.checkout.cardmanagement.logging

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.sp
import com.checkout.cardmanagement.logging.LogEventUtils.Companion.KEY_CARD
import com.checkout.cardmanagement.logging.LogEventUtils.Companion.KEY_CARDHOLDER
import com.checkout.cardmanagement.logging.LogEventUtils.Companion.KEY_CARD_STATE
import com.checkout.cardmanagement.logging.LogEventUtils.Companion.KEY_CVV_TEXT_STYLE
import com.checkout.cardmanagement.logging.LogEventUtils.Companion.KEY_DESIGN
import com.checkout.cardmanagement.logging.LogEventUtils.Companion.KEY_DURATION
import com.checkout.cardmanagement.logging.LogEventUtils.Companion.KEY_ERROR
import com.checkout.cardmanagement.logging.LogEventUtils.Companion.KEY_FROM
import com.checkout.cardmanagement.logging.LogEventUtils.Companion.KEY_PAN_TEXT_SEPARATOR
import com.checkout.cardmanagement.logging.LogEventUtils.Companion.KEY_PAN_TEXT_STYLE
import com.checkout.cardmanagement.logging.LogEventUtils.Companion.KEY_PIN_TEXT_STYLE
import com.checkout.cardmanagement.logging.LogEventUtils.Companion.KEY_REASON
import com.checkout.cardmanagement.logging.LogEventUtils.Companion.KEY_SOURCE
import com.checkout.cardmanagement.logging.LogEventUtils.Companion.KEY_SUFFIX_ID
import com.checkout.cardmanagement.logging.LogEventUtils.Companion.KEY_SUFFIX_IDS
import com.checkout.cardmanagement.logging.LogEventUtils.Companion.KEY_TO
import com.checkout.cardmanagement.logging.LogEventUtils.Companion.KEY_VERSION
import com.checkout.cardmanagement.model.CardManagementDesignSystem
import com.checkout.cardmanagement.model.CardState.ACTIVE
import com.checkout.cardmanagement.model.CardState.INACTIVE
import com.checkout.cardmanagement.model.CardState.REVOKED
import com.checkout.cardmanagement.model.CardState.SUSPENDED
import com.checkout.cardnetwork.common.model.CardNetworkError.AuthenticationFailure
import com.checkout.eventlogger.domain.model.MonitoringLevel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

internal class LogEventUtilsTest {
    private val utils = LogEventUtils()

    @Test
    fun identifier() {
        assertEquals(
            "card_management_initialised",
            utils.buildEvent(EVENT_INITIALIZE).typeIdentifier,
        )
        assertEquals("card_list", utils.buildEvent(EVENT_CARD_LIST).typeIdentifier)
        assertEquals("card_pin", utils.buildEvent(EVENT_GET_PIN).typeIdentifier)
        assertEquals("card_pan", utils.buildEvent(EVENT_GET_PAN).typeIdentifier)
        assertEquals("card_cvv", utils.buildEvent(EVENT_GET_CVV).typeIdentifier)
        assertEquals("card_pan_cvv", utils.buildEvent(EVENT_GET_PAN_CVV).typeIdentifier)
        assertEquals(
            "card_state_change",
            utils.buildEvent(EVENT_STATE_MANAGEMENT).typeIdentifier,
        )
        assertEquals(
            "push_provisioning",
            utils.buildEvent(EVENT_PUSH_PROVISIONING).typeIdentifier,
        )
        assertEquals("failure", utils.buildEvent(EVENT_FAILURE).typeIdentifier)
    }

    @Test
    fun monitoringLevel() {
        assertEquals(MonitoringLevel.INFO, utils.buildEvent(EVENT_INITIALIZE).monitoringLevel)
        assertEquals(MonitoringLevel.INFO, utils.buildEvent(EVENT_CARD_LIST).monitoringLevel)
        assertEquals(MonitoringLevel.INFO, utils.buildEvent(EVENT_GET_PIN).monitoringLevel)
        assertEquals(MonitoringLevel.INFO, utils.buildEvent(EVENT_GET_PAN).monitoringLevel)
        assertEquals(MonitoringLevel.INFO, utils.buildEvent(EVENT_GET_CVV).monitoringLevel)
        assertEquals(MonitoringLevel.INFO, utils.buildEvent(EVENT_GET_PAN_CVV).monitoringLevel)
        assertEquals(MonitoringLevel.INFO, utils.buildEvent(EVENT_STATE_MANAGEMENT).monitoringLevel)
        assertEquals(
            MonitoringLevel.INFO,
            utils.buildEvent(EVENT_PUSH_PROVISIONING).monitoringLevel,
        )
        assertEquals(MonitoringLevel.WARN, utils.buildEvent(EVENT_FAILURE).monitoringLevel)
    }

    @Test
    fun `assert Initialized of app version`() {
        utils.buildEvent(EVENT_INITIALIZE).properties.run {
            assertEquals(com.checkout.cardmanagement.BuildConfig.APP_VERSION, get(KEY_VERSION))
        }
    }

    @Test
    fun `assert Initialized of default design system`() {
        (utils.buildEvent(EVENT_INITIALIZE).properties[KEY_DESIGN] as Map<*, *>).run {
            assertEquals(" ", get(KEY_PAN_TEXT_SEPARATOR))
            assertEquals(emptyMap<String, String>(), get(KEY_PAN_TEXT_STYLE))
            assertEquals(emptyMap<String, String>(), get(KEY_PIN_TEXT_STYLE))
            assertEquals(emptyMap<String, String>(), get(KEY_CVV_TEXT_STYLE))
        }
    }

    @Test
    fun `assert Initialized to log customised pan text separator`() {
        listOf("+", " - ", "*").forEach { panTextSeparator ->
            buildInitializedEventAndGetDesignMap<String>(panTextSeparator = panTextSeparator).run {
                assertEquals(panTextSeparator, get(KEY_PAN_TEXT_SEPARATOR))
            }
        }
    }

    @Test
    fun `assert Initialized to log customised pan text style`() =
        (
            buildInitializedEventAndGetDesignMap<Map<String, String>>(
                panTextStyle = CUSTOMISED_TEXT_STYLE,
            )[KEY_PAN_TEXT_STYLE]
            )!!.assertTextStyleProperties()

    @Test
    fun `assert Initialized to log customised pin text style`() =
        buildInitializedEventAndGetDesignMap<Map<String, String>>(
            pinTextStyle = CUSTOMISED_TEXT_STYLE,
        )[KEY_PIN_TEXT_STYLE]!!.assertTextStyleProperties()

    @Test
    fun `assert Initialized to log customised cvv text style`() =
        buildInitializedEventAndGetDesignMap<Map<String, String>>(
            securityCodeTextStyle = CUSTOMISED_TEXT_STYLE,
        )[KEY_CVV_TEXT_STYLE]!!.assertTextStyleProperties()

    @Test
    fun `assert CardList`() {
        utils.buildEvent(EVENT_CARD_LIST).properties.run {
            assertEquals(listOf("111", "222", "333"), get(KEY_SUFFIX_IDS))
        }
    }

    @Test
    fun `assert GetPin`() {
        utils.buildEvent(EVENT_GET_PIN).properties.run {
            assertEquals("1234", get(KEY_SUFFIX_ID))
            assertEquals("active", get(KEY_CARD_STATE))
        }
    }

    @Test
    fun `assert GetPan`() {
        utils.buildEvent(EVENT_GET_PAN).properties.run {
            assertEquals("1234", get(KEY_SUFFIX_ID))
            assertEquals("inactive", get(KEY_CARD_STATE))
        }
    }

    @Test
    fun `assert GetCVV`() {
        utils.buildEvent(EVENT_GET_CVV).properties.run {
            assertEquals("1234", get(KEY_SUFFIX_ID))
            assertEquals("suspended", get(KEY_CARD_STATE))
        }
    }

    @Test
    fun `assert GetPanCVV`() {
        utils.buildEvent(EVENT_GET_PAN_CVV).properties.run {
            assertEquals("1234", get(KEY_SUFFIX_ID))
            assertEquals("revoked", get(KEY_CARD_STATE))
        }
    }

    @Test
    fun `assert StateManagement`() {
        // with the reason
        utils.buildEvent(EVENT_STATE_MANAGEMENT).properties.run {
            assertEquals("1234", get(KEY_SUFFIX_ID))
            assertEquals("active", get(KEY_FROM))
            assertEquals("suspended", get(KEY_TO))
            assertEquals("REASON", get(KEY_REASON))
        }

        // without the reason
        utils.buildEvent(EVENT_STATE_MANAGEMENT.copy(reason = null)).properties.run {
            assertEquals(null, get(KEY_REASON))
        }
    }

    @Test
    fun `assert PushProvisioning`() {
        utils.buildEvent(EVENT_PUSH_PROVISIONING).properties.run {
            assertEquals("1234", get(KEY_CARD))
            assertEquals("5678", get(KEY_CARDHOLDER))
        }
    }

    @Test
    fun `assert Failure`() {
        utils.buildEvent(EVENT_FAILURE).properties.run {
            assertEquals("SOURCE", get(KEY_SOURCE))
            assertEquals(AuthenticationFailure.localizedMessage, get(KEY_ERROR))
        }
    }

    @Test
    fun `duration should be calculate to last two decimal points`() {
        assertDuration(durationMills = 4500, expectedDuration = "4.5")
        assertDuration(durationMills = 4000, expectedDuration = "4")
        assertDuration(durationMills = 1234, expectedDuration = "1.23")
        assertDuration(durationMills = 8901, expectedDuration = "8.9")
        assertDuration(durationMills = 8909, expectedDuration = "8.91")
    }

    @Test
    fun `extraProperties should be added to the event log properties`() {
        utils.buildEvent(
            EVENT_INITIALIZE,
            extraProperties = mapOf(
                "KEY_1" to "VALUE_1",
                "KEY_2" to "VALUE_2",
            ),
        ).properties.run {
            assertEquals("VALUE_1", get("KEY_1"))
            assertEquals("VALUE_2", get("KEY_2"))
            assertEquals(null, get("KEY_3"))
        }
    }

    private fun assertDuration(durationMills: Int, expectedDuration: String) {
        utils.buildEvent(EVENT_INITIALIZE, startedAt(durationMills)).properties.run {
            assertEquals(expectedDuration, get(KEY_DURATION))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> buildInitializedEventAndGetDesignMap(
        pinTextStyle: TextStyle = TextStyle(),
        panTextStyle: TextStyle = TextStyle(),
        securityCodeTextStyle: TextStyle = TextStyle(),
        panTextSeparator: String = " ",
    ) = utils.buildEvent(
        LogEvent.Initialized(
            CardManagementDesignSystem(
                panTextSeparator = panTextSeparator,
                panTextStyle = panTextStyle,
                pinTextStyle = pinTextStyle,
                securityCodeTextStyle = securityCodeTextStyle,
            ),
        ),
    ).properties[KEY_DESIGN] as Map<String, T>

    private fun Map<String, String>.assertTextStyleProperties() {
        assertEquals("Color(0.0, 0.0, 1.0, 1.0, sRGB IEC61966-2.1)", get("background"))
        assertEquals("12.0.sp", get("fontSize"))
        assertEquals("1.0.sp", get("letterSpacing"))
        assertEquals("14.0.sp", get("lineHeight"))
        assertEquals("FontWeight(weight=700)", get("fontWeight"))
        assertEquals("Italic", get("fontStyle"))
        assertEquals("All", get("fontSynthesis"))
        assertEquals("FontFamily.Monospace", get("fontFamily"))
        assertEquals("fontFeatureSettings", get("fontFeatureSettings"))
        assertEquals("BaselineShift(multiplier=-0.5)", get("baselineShift"))
        assertEquals("TextGeometricTransform(scaleX=1.0, skewX=0.0)", get("textGeometricTransform"))
        assertEquals("LocaleList(localeList=[en])", get("localeList"))
        assertEquals("TextDecoration.Underline", get("textDecoration"))
        assertEquals(
            "Shadow(color=Color(0.0, 0.0, 0.0, 1.0, sRGB IEC61966-2.1), offset=Offset(1.0, 4.0), blurRadius=0.0)",
            get("shadow"),
        )
        assertEquals("Justify", get("textAlign"))
        assertEquals("Ltr", get("textDirection"))
        assertEquals("TextIndent(firstLine=1.0.sp, restLine=0.0.sp)", get("textIndent"))
    }

    private companion object {
        private val CUSTOMISED_TEXT_STYLE = TextStyle(
            color = Color.Red,
            fontSize = 12.sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Bold,
            background = Color.Blue,
            letterSpacing = 1.sp,
            lineHeight = 14.sp,
            fontSynthesis = FontSynthesis.All,
            fontFamily = FontFamily.Monospace,
            fontFeatureSettings = "fontFeatureSettings",
            baselineShift = BaselineShift.Subscript,
            textGeometricTransform = TextGeometricTransform(scaleX = 1.0F),
            localeList = LocaleList("en"),
            textDecoration = TextDecoration.Underline,
            shadow = Shadow(Color.Black, offset = Offset(1f, 4f)),
            textAlign = TextAlign.Justify,
            textDirection = TextDirection.Ltr,
            textIndent = TextIndent(1.sp),
        )
        private val EVENT_INITIALIZE = LogEvent.Initialized(CardManagementDesignSystem(TextStyle()))
        private val EVENT_CARD_LIST = LogEvent.CardList(listOf("111", "222", "333"))
        private val EVENT_GET_PIN = LogEvent.GetPin("1234", ACTIVE)
        private val EVENT_GET_PAN = LogEvent.GetPan("1234", INACTIVE)
        private val EVENT_GET_CVV = LogEvent.GetCVV("1234", SUSPENDED)
        private val EVENT_GET_PAN_CVV = LogEvent.GetPanCVV("1234", REVOKED)
        private val EVENT_STATE_MANAGEMENT =
            LogEvent.StateManagement("1234", ACTIVE, SUSPENDED, "REASON")
        private val EVENT_PUSH_PROVISIONING = LogEvent.PushProvisioning("1234", "5678")
        private val EVENT_FAILURE =
            LogEvent.Failure("SOURCE", AuthenticationFailure)

        private fun startedAt(durationMills: Int) = Calendar.getInstance().apply {
            add(Calendar.MILLISECOND, durationMills)
        }
    }
}
