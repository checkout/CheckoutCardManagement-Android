package com.checkout.cardmanagement.logging

import androidx.compose.ui.text.TextStyle
import com.checkout.cardmanagement.model.CardManagementDesignSystem
import com.checkout.cardnetwork.common.model.CardNetworkError
import com.checkout.eventlogger.domain.model.Event
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Calendar
import com.checkout.eventlogger.CheckoutEventLogger as EventLogger

internal class CheckoutEventLoggerTest {
    private val logEventUtils: LogEventUtils = mock()
    private val eventLogger: EventLogger = mock()
    private val event: Event = mock()
    private lateinit var logger: CheckoutEventLogger

    @Before
    fun setup() {
        logger = CheckoutEventLogger(logEventUtils, eventLogger)
    }

    @Test
    fun `log event`() {
        val startedAt = Calendar.getInstance()
        val logEvent = LogEvent.Initialized(CardManagementDesignSystem(TextStyle()))
        whenever(logEventUtils.buildEvent(logEvent, startedAt)).thenReturn(event)

        logger.log(logEvent, startedAt)
        verify(eventLogger).logEvent(event)
    }

    @Test
    fun `log error with source`() {
        val eventCaptor = argumentCaptor<LogEvent.Failure>()
        val additionalInfo = mapOf("KEY" to "VALUE", "source" to "GET PIN")
        whenever(logEventUtils.buildEvent(any(), eq(null), eq(additionalInfo)))
            .thenReturn(event)

        logger.log(CardNetworkError.Unauthenticated, additionalInfo)
        verify(logEventUtils).buildEvent(eventCaptor.capture(), eq(null), eq(additionalInfo))

        assertEquals(CardNetworkError.Unauthenticated, eventCaptor.firstValue.error)
        assertEquals("GET PIN", eventCaptor.firstValue.source)
        verify(eventLogger).logEvent(event)
    }
}
