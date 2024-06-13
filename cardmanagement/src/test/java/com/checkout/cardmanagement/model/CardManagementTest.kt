package com.checkout.cardmanagement.model

import android.app.Activity
import android.content.Intent
import com.checkout.cardmanagement.logging.CheckoutEventLogger
import com.checkout.cardmanagement.logging.LogEvent
import com.checkout.cardmanagement.logging.LogEventSource.ACTIVATE_CARD
import com.checkout.cardmanagement.logging.LogEventSource.PUSH_PROVISIONING
import com.checkout.cardmanagement.logging.LogEventSource.REVOKE_CARD
import com.checkout.cardmanagement.logging.LogEventSource.SUSPEND_CARD
import com.checkout.cardmanagement.model.CardManagementError.PushProvisioningFailureType.CANCELLED
import com.checkout.cardmanagement.model.CardRevokeReason.STOLEN
import com.checkout.cardmanagement.model.CardState.ACTIVE
import com.checkout.cardmanagement.model.CardState.INACTIVE
import com.checkout.cardmanagement.model.CardState.REVOKED
import com.checkout.cardmanagement.model.CardState.SUSPENDED
import com.checkout.cardmanagement.model.CardSuspendReason.LOST
import com.checkout.cardnetwork.CardService
import com.checkout.cardnetwork.common.model.CardNetworkError
import com.checkout.cardnetwork.common.model.CardNetworkError.PushProvisioningFailureType
import com.checkout.cardnetwork.common.model.CardNetworkError.PushProvisioningFailureType.OPERATION_FAILURE
import com.checkout.cardnetwork.common.model.CardNetworkError.ServerIssue
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq

internal class CardManagementTest {
    private val activity: Activity = mock()
    private val service: CardService = mock()
    private val manager: com.checkout.cardmanagement.CheckoutCardManager = mock()
    private val logger: CheckoutEventLogger = mock()
    private val resultCaptor = argumentCaptor<(Result<Unit>) -> Unit>()
    private lateinit var card: Card

    @Before
    fun setup() {
        `when`(manager.logger).thenReturn(logger)
        `when`(manager.sessionToken).thenReturn("SESSION_TOKEN")
        `when`(manager.service).thenReturn(service)
        card = createCard(manager = manager)
    }

    @Test
    fun `Provision success result handler`() = runBlocking {
        val completionHandler: (Result<Unit>) -> Unit = {
            assertTrue(it.isSuccess)
        }
        getProvisionHandler(completionHandler)
        resultCaptor.firstValue.invoke((Result.success(Unit)))
    }

    @Test
    fun `Provision success result logging`() = runBlocking {
        val completionHandler: (Result<Unit>) -> Unit = {
            val eventCaptor = argumentCaptor<LogEvent>()
            verify(logger).log(eventCaptor.capture(), any())
            assertTrue(eventCaptor.firstValue is LogEvent.PushProvisioning)
            (eventCaptor.firstValue as LogEvent.PushProvisioning).let { event ->
                assertEquals(card.partIdentifier, event.last4CardID)
                assertEquals(CARDHOLDER_ID.takeLast(4), event.last4CardholderID)
            }
        }
        getProvisionHandler(completionHandler)
        resultCaptor.firstValue.invoke((Result.success(Unit)))
    }

    @Test
    fun `Provision failure result handler`() = runBlocking {
        val completionHandler: (Result<Unit>) -> Unit = {
            assertTrue(it.isFailure)
            assertEquals(CardManagementError.PushProvisioningFailure(CANCELLED), it.exceptionOrNull()!!)
        }
        getProvisionHandler(completionHandler)
        resultCaptor.firstValue.invoke(
            (Result.failure(CardNetworkError.PushProvisioningFailure(PushProvisioningFailureType.CANCELLED))),
        )
    }

    @Test
    fun `Provision failure result logging`() = runBlocking {
        val completionHandler: (Result<Unit>) -> Unit = {
            val eventCaptor = argumentCaptor<LogEvent>()
            verify(logger).log(eventCaptor.capture(), any())
            assertTrue(eventCaptor.firstValue is LogEvent.Failure)
            (eventCaptor.firstValue as LogEvent.Failure).let { event ->
                assertEquals(CardNetworkError.PushProvisioningFailure(OPERATION_FAILURE), event.error)
                assertEquals(PUSH_PROVISIONING, event.source)
            }
        }
        getProvisionHandler(completionHandler)
        resultCaptor.firstValue.invoke((Result.failure(CardNetworkError.PushProvisioningFailure(OPERATION_FAILURE))))
    }

    @Test
    fun `card#handleCardResult should call CardService#handleCardResult`() {
        val intent = Intent()
        card.handleCardResult(0, 1, intent)
        verify(service).handleCardResult(eq(0), eq(1), eq(intent))
    }

    @Test
    fun `card#activate handles success result`() {
        card = createCard(INACTIVE, manager)
        `when`(service.activateCard(any(), any())).thenReturn(flow { emit(Result.success(Unit)) })
        val completionHandler: (Result<Unit>) -> Unit = {
            assertTrue(it.isSuccess)
            assertEquals(Unit, it.getOrNull())
        }
        card.activate { completionHandler(it) }
    }

    @Test
    fun `card#activate handles success logging`() {
        card = createCard(INACTIVE, manager)
        `when`(service.activateCard(any(), any())).thenReturn(flow { emit(Result.success(Unit)) })
        card.activate {
            assertSuccessfulLog(expectedReason = null, expectedRequestedState = ACTIVE)
        }
    }

    @Test
    fun `card#activate on an unsupported CardState should return an InvalidStateRequested error`() {
        listOf(ACTIVE, REVOKED).forEach { unsupportedCardState ->
            card = createCard(unsupportedCardState, manager)
            val completionHandler: (Result<Unit>) -> Unit = {
                assertTrue(it.isFailure)
                assertEquals(CardManagementError.InvalidStateRequested, it.exceptionOrNull())
            }
            card.activate { completionHandler(it) }
        }
    }

    @Test
    fun `card#activate without session token should return an Unauthenticated error`() {
        card = createCard(INACTIVE, manager)
        `when`(manager.sessionToken).thenReturn(null)
        val completionHandler: (Result<Unit>) -> Unit = {
            assertTrue(it.isFailure)
            assertEquals(CardManagementError.Unauthenticated, it.exceptionOrNull())
        }
        card.activate { completionHandler(it) }
    }

    @Test
    fun `card#activate handles failure result`() {
        card = createCard(INACTIVE, manager)
        `when`(service.activateCard(any(), any()))
            .thenReturn(flow { emit(Result.failure(ServerIssue)) })
        val completionHandler: (Result<Unit>) -> Unit = {
            assertTrue(it.isFailure)
            assertEquals(ServerIssue.toCardManagementError(), it.exceptionOrNull())
        }
        card.activate { completionHandler(it) }
    }

    @Test
    fun `card#activate handles failure logging`() {
        card = createCard(INACTIVE, manager)
        `when`(service.activateCard(any(), any()))
            .thenReturn(flow { emit(Result.failure(ServerIssue)) })
        card.activate {
            assertFailureLog(expectedSource = ACTIVATE_CARD, expectedError = ServerIssue)
        }
    }

    @Test
    fun `card#suspend handles success result`() {
        card = createCard(ACTIVE, manager)
        `when`(service.suspendCard(any(), any(), any()))
            .thenReturn(flow { emit(Result.success(Unit)) })
        val completionHandler: (Result<Unit>) -> Unit = {
            assertTrue(it.isSuccess)
            assertEquals(Unit, it.getOrNull())
        }
        card.suspend(LOST) { completionHandler(it) }
    }

    @Test
    fun `card#suspend handles success logging`() {
        card = createCard(ACTIVE, manager)
        `when`(service.suspendCard(any(), any(), any()))
            .thenReturn(flow { emit(Result.success(Unit)) })
        card.suspend(LOST) {
            assertSuccessfulLog(
                expectedReason = "suspected_lost",
                expectedRequestedState = SUSPENDED,
            )
        }
    }

    @Test
    fun `card#suspend on an unsupported CardState should return an InvalidStateRequested error`() {
        listOf(SUSPENDED, REVOKED, INACTIVE).forEach { unsupportedCardState ->
            card = createCard(unsupportedCardState, manager)
            val completionHandler: (Result<Unit>) -> Unit = {
                assertTrue(it.isFailure)
                assertEquals(CardManagementError.InvalidStateRequested, it.exceptionOrNull())
            }
            card.suspend(LOST) { completionHandler(it) }
        }
    }

    @Test
    fun `card#suspend without session token should return an Unauthenticated error`() {
        card = createCard(ACTIVE, manager)
        `when`(manager.sessionToken).thenReturn(null)
        val completionHandler: (Result<Unit>) -> Unit = {
            assertTrue(it.isFailure)
            assertEquals(CardManagementError.Unauthenticated, it.exceptionOrNull())
        }
        card.suspend(LOST) { completionHandler(it) }
    }

    @Test
    fun `card#suspend handles failure result`() {
        card = createCard(ACTIVE, manager)
        `when`(service.suspendCard(any(), any(), any()))
            .thenReturn(flow { emit(Result.failure(ServerIssue)) })
        val completionHandler: (Result<Unit>) -> Unit = {
            assertTrue(it.isFailure)
            assertEquals(ServerIssue.toCardManagementError(), it.exceptionOrNull())
        }
        card.suspend(LOST) { completionHandler(it) }
    }

    @Test
    fun `card#suspend handles failure logging`() {
        card = createCard(ACTIVE, manager)
        `when`(service.suspendCard(any(), any(), any()))
            .thenReturn(flow { emit(Result.failure(ServerIssue)) })
        card.suspend(LOST) {
            assertFailureLog(expectedSource = SUSPEND_CARD, expectedError = ServerIssue)
        }
    }

    @Test
    fun `card#revoke handles success result`() {
        card = createCard(INACTIVE, manager)
        `when`(service.revokeCard(any(), any(), any()))
            .thenReturn(flow { emit(Result.success(Unit)) })
        val completionHandler: (Result<Unit>) -> Unit = {
            assertTrue(it.isSuccess)
            assertEquals(Unit, it.getOrNull())
        }
        card.revoke(STOLEN) { completionHandler(it) }
    }

    @Test
    fun `card#revoke handles success logging`() {
        card = createCard(ACTIVE, manager)
        `when`(service.revokeCard(any(), any(), any()))
            .thenReturn(flow { emit(Result.success(Unit)) })
        card.revoke(STOLEN) {
            assertSuccessfulLog(
                expectedReason = "reported_stolen",
                expectedRequestedState = REVOKED,
            )
        }
    }

    @Test
    fun `card#revoke on an unsupported CardState should return an InvalidStateRequested error`() {
        card = createCard(REVOKED, manager)
        val completionHandler: (Result<Unit>) -> Unit = {
            assertTrue(it.isFailure)
            assertEquals(CardManagementError.InvalidStateRequested, it.exceptionOrNull())
        }
        card.revoke(STOLEN) { completionHandler(it) }
    }

    @Test
    fun `card#revoke without session token should return an Unauthenticated error`() {
        card = createCard(INACTIVE, manager)
        `when`(manager.sessionToken).thenReturn(null)
        val completionHandler: (Result<Unit>) -> Unit = {
            assertTrue(it.isFailure)
            assertEquals(CardManagementError.Unauthenticated, it.exceptionOrNull())
        }
        card.revoke(STOLEN) { completionHandler(it) }
    }

    @Test
    fun `card#revoke handles failure result`() {
        card = createCard(INACTIVE, manager)
        `when`(service.revokeCard(any(), any(), any()))
            .thenReturn(flow { emit(Result.failure(ServerIssue)) })
        val completionHandler: (Result<Unit>) -> Unit = {
            assertTrue(it.isFailure)
            assertEquals(ServerIssue.toCardManagementError(), it.exceptionOrNull())
        }
        card.revoke(STOLEN) { completionHandler(it) }
    }

    @Test
    fun `card#revoke handles failure logging`() {
        card = createCard(INACTIVE, manager)
        `when`(service.revokeCard(any(), any(), any()))
            .thenReturn(flow { emit(Result.failure(ServerIssue)) })
        card.revoke(STOLEN) {
            assertFailureLog(expectedSource = REVOKE_CARD, expectedError = ServerIssue)
        }
    }

    private fun assertSuccessfulLog(expectedReason: String?, expectedRequestedState: CardState) {
        val eventCaptor = argumentCaptor<LogEvent>()
        verify(logger).log(eventCaptor.capture(), any())
        assertTrue(eventCaptor.firstValue is LogEvent.StateManagement)
        (eventCaptor.firstValue as LogEvent.StateManagement).let { event ->
            assertEquals(card.partIdentifier, event.idLast4)
            assertEquals(card.state, event.originalState)
            assertEquals(expectedRequestedState, event.requestedState)
            assertEquals(expectedReason, event.reason)
        }
    }

    private fun assertFailureLog(expectedSource: String, expectedError: CardNetworkError) {
        val eventCaptor = argumentCaptor<LogEvent>()
        verify(logger).log(eventCaptor.capture(), any())
        assertTrue(eventCaptor.firstValue is LogEvent.Failure)
        (eventCaptor.firstValue as LogEvent.Failure).let { event ->
            assertEquals(expectedSource, event.source)
            assertEquals(expectedError, event.error)
        }
    }

    private fun getProvisionHandler(
        completionHandler: (Result<Unit>) -> Unit,
    ): (Result<Unit>) -> Unit {
        card.provision(
            activity = activity,
            cardholderId = CARDHOLDER_ID,
            configuration = CONFIG,
            token = TOKEN,
            completionHandler = completionHandler,
        )

        verify(service).addCardToGoogleWallet(
            activity = eq(activity),
            cardId = eq(card.id),
            cardholderId = eq(CARDHOLDER_ID),
            configuration = any(),
            token = eq(TOKEN),
            completionHandler = resultCaptor.capture(),
        )
        return resultCaptor.firstValue
    }

    private companion object {
        private const val CARDHOLDER_ID = "CARDHOLDER_ID"
        private const val TOKEN = "TOKEN"
        private val CONFIG = ProvisioningConfiguration(
            issuerID = "ISSUER_ID",
            serviceRSAExponent = byteArrayOf(),
            serviceRSAModulus = byteArrayOf(),
            serviceURL = "SERVICE_URL",
            digitalCardURL = "DIGITAL_CARD_URL",
        )

        private fun createCard(
            state: CardState = ACTIVE,
            manager: com.checkout.cardmanagement.CheckoutCardManager,
        ) = Card(
            state = state,
            id = "CARD_ID",
            panLast4Digits = "1234",
            expiryDate = CardExpiryDate("11", "25"),
            cardholderName = "John Smith",
            manager = manager,
        )
    }
}
