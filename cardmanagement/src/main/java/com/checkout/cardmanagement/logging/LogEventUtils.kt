package com.checkout.cardmanagement.logging

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
import androidx.compose.ui.unit.TextUnit
import com.checkout.cardmanagement.model.CardState
import com.checkout.eventlogger.domain.model.Event
import com.checkout.eventlogger.domain.model.MonitoringLevel
import java.text.DecimalFormat
import java.util.Calendar
import kotlin.math.abs
import kotlin.reflect.KProperty1

/**
 * Formatter for internal Analytic Events
 */
internal class LogEventUtils {

    /**
     * Create dispatchable analytics event from the given LogEvent
     */
    internal fun buildEvent(
        event: LogEvent,
        startedAt: Calendar? = null,
        extraProperties: Map<String, String> = emptyMap(),
    ): Event = object : Event {
        val currentTime = Calendar.getInstance()
        override val monitoringLevel = event.monitoringLevel()
        override val properties = event.properties(startedAt, extraProperties, currentTime)
        override val time = currentTime.time
        override val typeIdentifier = event.identifier()
    }

    internal companion object {
        internal const val KEY_SOURCE = "source"
        internal const val KEY_VERSION = "version"
        internal const val KEY_DESIGN = "design"
        internal const val KEY_PAN_TEXT_SEPARATOR = "panTextSeparator"
        internal const val KEY_PAN_TEXT_STYLE = "panTextStyle"
        internal const val KEY_PIN_TEXT_STYLE = "pinTextStyle"
        internal const val KEY_CVV_TEXT_STYLE = "cvvTextStyle"
        internal const val KEY_SUFFIX_IDS = "suffix_ids"
        internal const val KEY_SUFFIX_ID = "suffix_id"
        internal const val KEY_CARD_STATE = "card_state"
        internal const val KEY_FROM = "from"
        internal const val KEY_TO = "to"
        internal const val KEY_REASON = "reason"
        internal const val KEY_CARD = "card"
        internal const val KEY_CARDHOLDER = "cardholder"
        internal const val KEY_CARD_DIGITIZATION_STATE = "digitization_state"
        internal const val KEY_ERROR = "error"
        internal const val KEY_DURATION = "duration"
        internal const val LOGGER_PRODUCTION_ID = "com.checkout.issuing-mobile-sdk"

        // Define unique identifier for event
        private fun LogEvent.identifier(): String = when (this) {
            is LogEvent.Initialized -> "card_management_initialised"
            is LogEvent.CardList -> "card_list"
            is LogEvent.GetPin -> "card_pin"
            is LogEvent.GetPan -> "card_pan"
            is LogEvent.GetCVV -> "card_cvv"
            is LogEvent.GetPanCVV -> "card_pan_cvv"
            is LogEvent.StateManagement -> "card_state_change"
            is LogEvent.ConfigurePushProvisioning -> "configure_push_provisioning"
            is LogEvent.GetCardDigitizationState -> "get_card_digitization_state"
            is LogEvent.PushProvisioning -> "push_provisioning"
            is LogEvent.Failure -> "failure"
        }

        // Define monitoring level for event
        private fun LogEvent.monitoringLevel(): MonitoringLevel = when (this) {
            is LogEvent.Initialized,
            is LogEvent.CardList,
            is LogEvent.GetPin,
            is LogEvent.GetPan,
            is LogEvent.GetCVV,
            is LogEvent.GetPanCVV,
            is LogEvent.StateManagement,
            is LogEvent.ConfigurePushProvisioning,
            is LogEvent.GetCardDigitizationState,
            is LogEvent.PushProvisioning,
            -> MonitoringLevel.INFO
            is LogEvent.Failure -> MonitoringLevel.WARN
        }

        // Build a properties map for event
        private fun LogEvent.properties(
            startAt: Calendar? = null,
            extraProperties: Map<String, String>,
            currentTime: Calendar,
        ): Map<String, Any> {
            val propertyMap = mutableMapOf<String, Any>()

            when (this) {
                is LogEvent.Initialized -> {
                    propertyMap[KEY_VERSION] = com.checkout.cardmanagement.BuildConfig.APP_VERSION
                    propertyMap[KEY_DESIGN] = mutableMapOf<String, Any>().apply {
                        with(designSystem) {
                            put(KEY_PAN_TEXT_SEPARATOR, panTextSeparator)
                            put(KEY_PAN_TEXT_STYLE, buildTextStyleMap(panViewConfig.textStyle))
                            put(KEY_PIN_TEXT_STYLE, buildTextStyleMap(pinViewConfig.textStyle))
                            put(KEY_CVV_TEXT_STYLE, buildTextStyleMap(securityCodeViewConfig.textStyle))
                        }
                    }
                }

                is LogEvent.CardList -> propertyMap[KEY_SUFFIX_IDS] = idSuffixes
                is LogEvent.GetPin,
                is LogEvent.GetPan,
                is LogEvent.GetCVV,
                is LogEvent.GetPanCVV,
                -> {
                    propertyMap[KEY_SUFFIX_ID] = this.readProperty<String>("idLast4")
                    propertyMap[KEY_CARD_STATE] = this.readProperty<CardState>("cardState").name.lowercase()
                }

                is LogEvent.StateManagement -> {
                    propertyMap[KEY_SUFFIX_ID] = idLast4
                    propertyMap[KEY_FROM] = originalState.name.lowercase()
                    propertyMap[KEY_TO] = requestedState.name.lowercase()
                    reason?.let { propertyMap[KEY_REASON] = it }
                }

                is LogEvent.ConfigurePushProvisioning -> {
                    propertyMap[KEY_CARDHOLDER] = last4CardholderID
                }

                is LogEvent.GetCardDigitizationState -> {
                    propertyMap[KEY_CARD] = last4CardID
                    propertyMap[KEY_CARD_DIGITIZATION_STATE] = digitizationState
                }

                is LogEvent.PushProvisioning -> {
                    propertyMap[KEY_CARD] = last4CardID
                }

                is LogEvent.Failure -> {
                    propertyMap[KEY_SOURCE] = source
                    propertyMap[KEY_ERROR] = error.message ?: ""
                }
            }

            startAt?.let { startCalendar ->
                // Round to 2 decimal places
                propertyMap[KEY_DURATION] = DecimalFormat("#.##").format(
                    abs(currentTime.timeInMillis - startCalendar.timeInMillis) / 1000F,
                )
            }

            propertyMap.putAll(extraProperties)

            return propertyMap.toMap()
        }
    }
}

// Build a map of text style properties if they are not the default values.
private fun buildTextStyleMap(textStyle: TextStyle) = mutableMapOf<String, String>().apply {
    putIfNotNullOrNotUnspecified<Color>(textStyle, "color", Color.Unspecified)
    putIfNotNullOrNotUnspecified<Color>(textStyle, "background", Color.Unspecified)
    putIfNotNullOrNotUnspecified<TextUnit>(textStyle, "fontSize", TextUnit.Unspecified)
    putIfNotNullOrNotUnspecified<TextUnit>(textStyle, "letterSpacing", TextUnit.Unspecified)
    putIfNotNullOrNotUnspecified<TextUnit>(textStyle, "lineHeight", TextUnit.Unspecified)
    putIfNotNullOrNotUnspecified<FontWeight>(textStyle, "fontWeight", null)
    putIfNotNullOrNotUnspecified<FontStyle>(textStyle, "fontStyle", null)
    putIfNotNullOrNotUnspecified<FontSynthesis>(textStyle, "fontSynthesis", null)
    putIfNotNullOrNotUnspecified<FontFamily>(textStyle, "fontFamily", null)
    putIfNotNullOrNotUnspecified<String>(textStyle, "fontFeatureSettings", null)
    putIfNotNullOrNotUnspecified<BaselineShift>(textStyle, "baselineShift", null)
    putIfNotNullOrNotUnspecified<TextGeometricTransform>(textStyle, "textGeometricTransform", null)
    putIfNotNullOrNotUnspecified<LocaleList>(textStyle, "localeList", null)
    putIfNotNullOrNotUnspecified<TextDecoration>(textStyle, "textDecoration", null)
    putIfNotNullOrNotUnspecified<Shadow>(textStyle, "shadow", null)
    putIfNotNullOrNotUnspecified<TextAlign>(textStyle, "textAlign", null)
    putIfNotNullOrNotUnspecified<TextDirection>(textStyle, "textDirection", null)
    putIfNotNullOrNotUnspecified<TextIndent>(textStyle, "textIndent", null)
}

/** Put the [TextStyle] property in the map if the value is not [kotlin.null] or [unspecifiedValue]. */
private fun <T> MutableMap<String, String>.putIfNotNullOrNotUnspecified(
    textStyle: TextStyle,
    propertyName: String,
    unspecifiedValue: Any? = null,
) {
    val property = textStyle.readProperty<T>(propertyName)
    if (property != null && property != unspecifiedValue) put(propertyName, property.toString())
}

@Suppress("UNCHECKED_CAST")
private fun <R> Any.readProperty(propertyName: String): R {
    // don't cast here to <Any, R>, it would succeed silently
    val property = this::class.members.first { it.name == propertyName } as KProperty1<Any, *>
    // force a invalid cast exception if incorrect type here
    return property.get(this) as R
}
