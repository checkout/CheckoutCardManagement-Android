package com.checkout.cardmanagement.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class CardSecureDataResultTest {
    @Test
    fun `Success should expose data`() {
        val data = "secret"

        val success = CardSecureDataResult.Success(data)

        assertEquals(data, success.data)
        assertTrue(success is CardSecureDataResult<String>)
    }

    @Test
    fun `Success equals should rely on data`() {
        val first = CardSecureDataResult.Success("payload")
        val second = CardSecureDataResult.Success("payload")
        val different = CardSecureDataResult.Success("other")

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, different)
    }

    @Test
    fun `Success copy should change data while remaining Success`() {
        val original = CardSecureDataResult.Success("payload")

        val copy = original.copy(data = "new")

        assertEquals("new", copy.data)
    }

    @Test
    fun `AuthenticationFailure should expose message and tokenType`() {
        val error =
            CardSecureDataResult.Error.AuthenticationFailure(
                message = MESSAGE,
                tokenType = "session",
            )

        assertEquals(MESSAGE, error.message)
        assertEquals("session", error.tokenType)
        assertTrue(error is CardSecureDataResult.Error)
    }

    @Test
    fun `AuthenticationFailure copy should preserve other fields`() {
        val error =
            CardSecureDataResult.Error
                .AuthenticationFailure(MESSAGE, "session")
                .copy(message = "new")

        assertEquals("new", error.message)
        assertEquals("session", error.tokenType)
    }

    @Test
    fun `Unauthenticated should expose message`() {
        val error = CardSecureDataResult.Error.Unauthenticated(MESSAGE)

        assertEquals(MESSAGE, error.message)
        assertTrue(error is CardSecureDataResult.Error)
    }

    @Test
    fun `ConnectionIssue should expose message and cause`() {
        val cause = RuntimeException("boom")
        val error =
            CardSecureDataResult.Error.ConnectionIssue(
                message = MESSAGE,
                cause = cause,
            )

        assertEquals(MESSAGE, error.message)
        assertEquals(cause, error.cause)
    }

    @Test
    fun `ConnectionIssue should accept null cause`() {
        val error = CardSecureDataResult.Error.ConnectionIssue(MESSAGE, null)

        assertNull(error.cause)
    }

    @Test
    fun `PanNotViewed should expose message`() {
        val error = CardSecureDataResult.Error.PanNotViewed(MESSAGE)

        assertEquals(MESSAGE, error.message)
        assertTrue(error is CardSecureDataResult.Error)
    }

    @Test
    fun `UnableToPerformOperation should expose message and default null cause`() {
        val error = CardSecureDataResult.Error.UnableToPerformOperation(MESSAGE)

        assertEquals(MESSAGE, error.message)
        assertNull(error.cause)
    }

    @Test
    fun `UnableToPerformOperation should expose provided cause`() {
        val cause = IllegalStateException()
        val error = CardSecureDataResult.Error.UnableToPerformOperation(MESSAGE, cause)

        assertEquals(cause, error.cause)
    }

    @Test
    fun `UnsupportedApiVersion should expose version and message`() {
        val error =
            CardSecureDataResult.Error.UnsupportedApiVersion(
                version = 21,
                message = MESSAGE,
            )

        assertEquals(21, error.version)
        assertEquals(MESSAGE, error.message)
    }

    @Test
    fun `Error data classes should support equality by content`() {
        val error1 = CardSecureDataResult.Error.Unauthenticated(MESSAGE)
        val error2 = CardSecureDataResult.Error.Unauthenticated(MESSAGE)
        val different = CardSecureDataResult.Error.Unauthenticated("other")

        assertEquals(error1, error2)
        assertEquals(error1.hashCode(), error2.hashCode())
        assertNotEquals(error1, different)
    }

    private companion object {
        private const val MESSAGE = "MESSAGE"
    }
}
