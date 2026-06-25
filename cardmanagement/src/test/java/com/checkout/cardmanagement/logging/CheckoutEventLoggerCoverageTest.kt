package com.checkout.cardmanagement.logging

import com.checkout.eventlogger.domain.model.Event
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import com.checkout.eventlogger.CheckoutEventLogger as EventLogger

internal class CheckoutEventLoggerCoverageTest {
    private val logEventUtils: LogEventUtils = mock()
    private val eventLogger: EventLogger = mock()
    private val event: Event = mock()
    private lateinit var logger: CheckoutEventLogger

    @Before
    fun setup() {
        logger = CheckoutEventLogger(logEventUtils, eventLogger)
    }

    @Test
    fun `sessionID should be a non null UUID-shaped string`() {
        val sessionId = logger.sessionID

        assertNotNull(sessionId)
        // UUID format: 8-4-4-4-12 = 36 chars
        assertNotNull(java.util.UUID.fromString(sessionId))
    }

    @Test
    fun `sessionID should be stable across reads`() {
        val first = logger.sessionID
        val second = logger.sessionID

        assert(first == second)
    }

    @Test
    fun `log with additionalInfo should pass map into buildEvent and log to inner logger`() {
        val additionalInfo = mapOf("k1" to "v1", "k2" to "v2")
        val logEvent = LogEvent.CardList(listOf("c1"), emptySet())
        whenever(logEventUtils.buildEvent(eq(logEvent), eq(null), eq(additionalInfo)))
            .thenReturn(event)

        logger.log(event = logEvent, startedAt = null, additionalInfo = additionalInfo)

        verify(eventLogger).logEvent(event)
    }

    @Test
    fun `default constructor should produce non null logger`() {
        val direct = CheckoutEventLogger()

        assertNotNull(direct)
        assertNotNull(direct.sessionID)
    }
}
