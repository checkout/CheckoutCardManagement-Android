package com.checkout.cardmanagement.model

import android.app.Activity
import android.content.Intent
import com.checkout.cardmanagement.logging.CheckoutEventLogger
import com.checkout.cardmanagement.logging.LogEvent
import com.checkout.cardmanagement.logging.LogEventSource.ACTIVATE_CARD
import com.checkout.cardmanagement.logging.LogEventSource.GET_CARD_DIGITIZATION_STATE
import com.checkout.cardmanagement.logging.LogEventSource.PUSH_PROVISIONING
import com.checkout.cardmanagement.logging.LogEventSource.REVOKE_CARD
import com.checkout.cardmanagement.logging.LogEventSource.SUSPEND_CARD
import com.checkout.cardmanagement.logging.LogEventUtils
import com.checkout.cardmanagement.model.CardManagementError.PushProvisioningFailureType.CANCELLED
import com.checkout.cardmanagement.model.CardRevokeReason.STOLEN
import com.checkout.cardmanagement.model.CardState.ACTIVE
import com.checkout.cardmanagement.model.CardState.INACTIVE
import com.checkout.cardmanagement.model.CardState.REVOKED
import com.checkout.cardmanagement.model.CardState.SUSPENDED
import com.checkout.cardmanagement.model.CardSuspendReason.LOST
import com.checkout.cardmanagement.utils.CoroutineScopeOwner
import com.checkout.cardnetwork.CardService
import com.checkout.cardnetwork.common.model.CardNetworkError
import com.checkout.cardnetwork.common.model.CardNetworkError.PushProvisioningFailureType
import com.checkout.cardnetwork.common.model.CardNetworkError.PushProvisioningFailureType.OPERATION_FAILURE
import com.checkout.cardnetwork.common.model.CardNetworkError.ServerIssue
import com.checkout.cardnetwork.data.core.CardDigitizationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
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
    private val sessionTokenFlow = MutableStateFlow<String?>("SESSION_TOKEN")

    private lateinit var card: Card

    @OptIn(ExperimentalCoroutinesApi::class)
    private val testScope = TestScope(UnconfinedTestDispatcher())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val testCoroutineScopeOwner =
        object : CoroutineScopeOwner {
            override val scope: CoroutineScope = testScope

            override fun cancel() {}
        }

    @Before
    fun setup() {
        `when`(manager.logger).thenReturn(logger)
        `when`(manager.sessionToken).thenReturn(sessionTokenFlow)
        `when`(manager.service).thenReturn(service)
        `when`(manager.coroutineScope).thenReturn(testCoroutineScopeOwner)
        card = createCard(manager = manager)
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun `GetDigitizationState success result handler`() =
        runBlocking {
            `when`(service.getCardDigitizationState(any(), any())).thenReturn(
                Result.success(CardDigitizationState.DIGITIZED),
            )

            val completionHandler: (Result<DigitizationState>) -> Unit = {
                assertTrue(it.isSuccess)
                assertEquals(DigitizationState.DIGITIZED, it.getOrNull())
            }
            getDigitizationStateHandler(completionHandler)
        }

    @Test
    fun `suspend getDigitizationState success result`() =
        runBlocking {
            `when`(service.getCardDigitizationState(any(), any())).thenReturn(
                Result.success(CardDigitizationState.DIGITIZED),
            )

            val result = card.getDigitizationState(token = TOKEN)

            verify(service).getCardDigitizationState(
                cardId = eq(card.id),
                token = eq(TOKEN),
            )

            assertTrue(result is CardOperationResult.Success)
            assertEquals(DigitizationState.DIGITIZED, (result as CardOperationResult.Success).data)
        }

    @Test
    fun `suspend getDigitizationState success logging`() =
        runBlocking {
            `when`(service.getCardDigitizationState(any(), any())).thenReturn(
                Result.success(CardDigitizationState.DIGITIZED),
            )

            card.getDigitizationState(token = TOKEN)

            val eventCaptor = argumentCaptor<LogEvent>()
            verify(logger).log(eventCaptor.capture(), any(), any())
            assertTrue(eventCaptor.firstValue is LogEvent.GetCardDigitizationState)
            (eventCaptor.firstValue as LogEvent.GetCardDigitizationState).let { event ->
                assertEquals(card.id, event.cardId)
                assertEquals(DigitizationState.DIGITIZED, event.digitizationState)
            }
        }

    @Test
    fun `suspend getDigitizationState failure result`() =
        runBlocking {
            val error =
                CardNetworkError.FetchDigitizationStateFailure(
                    CardNetworkError.DigitizationStateFailureType.CONFIGURATION_FAILURE,
                )
            `when`(service.getCardDigitizationState(any(), any())).thenReturn(
                Result.failure(error),
            )

            val result = card.getDigitizationState(token = TOKEN)

            assertTrue(result is CardOperationResult.Error.DigitizationStateFailure)
        }

    @Test
    fun `suspend getDigitizationState failure logging`() =
        runBlocking {
            val error =
                CardNetworkError.FetchDigitizationStateFailure(
                    CardNetworkError.DigitizationStateFailureType.OPERATION_FAILURE,
                )
            `when`(service.getCardDigitizationState(any(), any())).thenReturn(
                Result.failure(error),
            )

            card.getDigitizationState(token = TOKEN)

            val eventCaptor = argumentCaptor<LogEvent>()
            verify(logger).log(eventCaptor.capture(), any(), any())
            assertTrue(eventCaptor.firstValue is LogEvent.Failure)
            (eventCaptor.firstValue as LogEvent.Failure).let { event ->
                assertEquals(error, event.error)
                assertEquals(GET_CARD_DIGITIZATION_STATE, event.source)
            }
        }

    @Test
    fun `GetDigitizationState success result logging`() =
        runBlocking {
            `when`(service.getCardDigitizationState(any(), any())).thenReturn(
                Result.success(CardDigitizationState.DIGITIZED),
            )

            val completionHandler: (Result<DigitizationState>) -> Unit = {
                val eventCaptor = argumentCaptor<LogEvent>()
                verify(logger).log(eventCaptor.capture(), any(), any())
                assertTrue(eventCaptor.firstValue is LogEvent.GetCardDigitizationState)
                (eventCaptor.firstValue as LogEvent.GetCardDigitizationState).let { event ->
                    assertEquals(card.id, event.cardId)
                }
            }
            getDigitizationStateHandler(completionHandler)
        }

    @Test
    fun `GetDigitizationState failure result handler`() =
        runBlocking {
            val error =
                CardNetworkError.FetchDigitizationStateFailure(
                    CardNetworkError.DigitizationStateFailureType.CONFIGURATION_FAILURE,
                )
            `when`(service.getCardDigitizationState(any(), any())).thenReturn(
                Result.failure(error),
            )

            val completionHandler: (Result<DigitizationState>) -> Unit = {
                assertTrue(it.isFailure)
                assertEquals(CardManagementError.FetchDigitizationStateFailure(CardManagementError.DigitizationStateFailureType.CONFIGURATION_FAILURE), it.exceptionOrNull()!!)
            }
            getDigitizationStateHandler(completionHandler)
        }

    @Test
    fun `GetDigitizationState failure result logging`() =
        runBlocking {
            val error =
                CardNetworkError.FetchDigitizationStateFailure(
                    CardNetworkError.DigitizationStateFailureType.OPERATION_FAILURE,
                )
            `when`(service.getCardDigitizationState(any(), any())).thenReturn(
                Result.failure(error),
            )

            val completionHandler: (Result<DigitizationState>) -> Unit = {
                val eventCaptor = argumentCaptor<LogEvent>()
                verify(logger).log(eventCaptor.capture(), any(), any())
                assertTrue(eventCaptor.firstValue is LogEvent.Failure)
                (eventCaptor.firstValue as LogEvent.Failure).let { event ->
                    assertEquals(error, event.error)
                    assertEquals(GET_CARD_DIGITIZATION_STATE, event.source)
                }
            }
            getDigitizationStateHandler(completionHandler)
        }

    @Test
    fun `Provision success result handler`() =
        runBlocking {
            `when`(service.addCardToGoogleWallet(any(), any(), any())).thenReturn(
                Result.success(Unit),
            )

            val completionHandler: (Result<Unit>) -> Unit = {
                assertTrue(it.isSuccess)
            }
            getProvisionHandler(completionHandler)
        }

    @Test
    fun `Provision success result logging`() =
        runBlocking {
            `when`(service.addCardToGoogleWallet(any(), any(), any())).thenReturn(
                Result.success(Unit),
            )

            val completionHandler: (Result<Unit>) -> Unit = {
                val eventCaptor = argumentCaptor<LogEvent>()
                verify(logger).log(eventCaptor.capture(), any(), any())
                assertTrue(eventCaptor.firstValue is LogEvent.PushProvisioning)
                (eventCaptor.firstValue as LogEvent.PushProvisioning).let { event ->
                    assertEquals(card.id, event.cardId)
                }
            }
            getProvisionHandler(completionHandler)
        }

    @Test
    fun `Provision failure result handler`() =
        runBlocking {
            val error = CardNetworkError.PushProvisioningFailure(PushProvisioningFailureType.CANCELLED)
            `when`(service.addCardToGoogleWallet(any(), any(), any())).thenReturn(
                Result.failure(error),
            )

            val completionHandler: (Result<Unit>) -> Unit = {
                assertTrue(it.isFailure)
                assertEquals(CardManagementError.PushProvisioningFailure(CANCELLED), it.exceptionOrNull()!!)
            }
            getProvisionHandler(completionHandler)
        }

    @Test
    fun `Provision failure result logging`() =
        runBlocking {
            val error = CardNetworkError.PushProvisioningFailure(OPERATION_FAILURE)
            `when`(service.addCardToGoogleWallet(any(), any(), any())).thenReturn(
                Result.failure(error),
            )

            val completionHandler: (Result<Unit>) -> Unit = {
                val eventCaptor = argumentCaptor<LogEvent>()
                verify(logger).log(eventCaptor.capture(), any(), any())
                assertTrue(eventCaptor.firstValue is LogEvent.Failure)
                (eventCaptor.firstValue as LogEvent.Failure).let { event ->
                    assertEquals(error, event.error)
                    assertEquals(PUSH_PROVISIONING, event.source)
                }
            }
            getProvisionHandler(completionHandler)
        }

    @Test
    fun `suspend provision success result`() =
        runBlocking {
            `when`(service.addCardToGoogleWallet(any(), any(), any())).thenReturn(
                Result.success(Unit),
            )

            val result = card.provision(activity = activity, token = TOKEN)

            verify(service).addCardToGoogleWallet(
                activity = eq(activity),
                cardId = eq(card.id),
                token = eq(TOKEN),
            )

            assertTrue(result is CardOperationResult.Success)
        }

    @Test
    fun `suspend provision success logging`() =
        runBlocking {
            `when`(service.addCardToGoogleWallet(any(), any(), any())).thenReturn(
                Result.success(Unit),
            )

            card.provision(activity = activity, token = TOKEN)

            val eventCaptor = argumentCaptor<LogEvent>()
            verify(logger).log(eventCaptor.capture(), any(), any())
            assertTrue(eventCaptor.firstValue is LogEvent.PushProvisioning)
            (eventCaptor.firstValue as LogEvent.PushProvisioning).let { event ->
                assertEquals(card.id, event.cardId)
            }
        }

    @Test
    fun `suspend provision failure result`() =
        runBlocking {
            val error = CardNetworkError.PushProvisioningFailure(PushProvisioningFailureType.CANCELLED)
            `when`(service.addCardToGoogleWallet(any(), any(), any())).thenReturn(
                Result.failure(error),
            )

            val result = card.provision(activity = activity, token = TOKEN)

            assertTrue(result is CardOperationResult.Error.OperationCancelled)
        }

    @Test
    fun `suspend provision failure logging`() =
        runBlocking {
            val error = CardNetworkError.PushProvisioningFailure(OPERATION_FAILURE)
            `when`(service.addCardToGoogleWallet(any(), any(), any())).thenReturn(
                Result.failure(error),
            )

            card.provision(activity = activity, token = TOKEN)

            val eventCaptor = argumentCaptor<LogEvent>()
            verify(logger).log(eventCaptor.capture(), any(), any())
            assertTrue(eventCaptor.firstValue is LogEvent.Failure)
            (eventCaptor.firstValue as LogEvent.Failure).let { event ->
                assertEquals(error, event.error)
                assertEquals(PUSH_PROVISIONING, event.source)
            }
        }

    @Test
    fun `suspend getDigitizationState logs with isLegacyRequest false`() =
        runBlocking {
            `when`(service.getCardDigitizationState(any(), any())).thenReturn(
                Result.success(CardDigitizationState.DIGITIZED),
            )

            card.getDigitizationState(token = TOKEN)

            val additionalInfoCaptor = argumentCaptor<Map<String, String>>()
            verify(logger).log(any(), any(), additionalInfoCaptor.capture())
            assertEquals("false", additionalInfoCaptor.firstValue[LogEventUtils.KEY_LEGACY_REQUEST])
        }

    @Test
    fun `deprecated getDigitizationState logs with isLegacyRequest true`() =
        runBlocking {
            `when`(service.getCardDigitizationState(any(), any())).thenReturn(
                Result.success(CardDigitizationState.DIGITIZED),
            )

            val completionHandler: (Result<DigitizationState>) -> Unit = {
                val additionalInfoCaptor = argumentCaptor<Map<String, String>>()
                verify(logger).log(any(), any(), additionalInfoCaptor.capture())
                assertEquals("true", additionalInfoCaptor.firstValue[LogEventUtils.KEY_LEGACY_REQUEST])
            }
            getDigitizationStateHandler(completionHandler)
        }

    @Test
    fun `suspend provision logs with isLegacyRequest false`() =
        runBlocking {
            `when`(service.addCardToGoogleWallet(any(), any(), any())).thenReturn(
                Result.success(Unit),
            )

            card.provision(activity = activity, token = TOKEN)

            val additionalInfoCaptor = argumentCaptor<Map<String, String>>()
            verify(logger).log(any(), any(), additionalInfoCaptor.capture())
            assertEquals("false", additionalInfoCaptor.firstValue[LogEventUtils.KEY_LEGACY_REQUEST])
        }

    @Test
    fun `deprecated provision logs with isLegacyRequest true`() =
        runBlocking {
            `when`(service.addCardToGoogleWallet(any(), any(), any())).thenReturn(
                Result.success(Unit),
            )

            val completionHandler: (Result<Unit>) -> Unit = {
                val additionalInfoCaptor = argumentCaptor<Map<String, String>>()
                verify(logger).log(any(), any(), additionalInfoCaptor.capture())
                assertEquals("true", additionalInfoCaptor.firstValue[LogEventUtils.KEY_LEGACY_REQUEST])
            }
            getProvisionHandler(completionHandler)
        }

    @Test
    fun `handleActivityResult delegates to service`() =
        runTest {
            val intent = Intent()
            card.handleCardResult(0, 1, intent)
            verify(service).handleActivityResult(eq(0), eq(1), eq(intent))
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
        sessionTokenFlow.value = null
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
        sessionTokenFlow.value = null
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
        sessionTokenFlow.value = null
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

    private fun assertSuccessfulLog(
        expectedReason: String?,
        expectedRequestedState: CardState,
    ) {
        val eventCaptor = argumentCaptor<LogEvent>()
        verify(logger).log(eventCaptor.capture(), any(), any())
        assertTrue(eventCaptor.firstValue is LogEvent.StateManagement)
        (eventCaptor.firstValue as LogEvent.StateManagement).let { event ->
            assertEquals(card.id, event.cardId)
            assertEquals(card.state, event.originalState)
            assertEquals(expectedRequestedState, event.requestedState)
            assertEquals(expectedReason, event.reason)
        }
    }

    private fun assertFailureLog(
        expectedSource: String,
        expectedError: CardNetworkError,
    ) {
        val eventCaptor = argumentCaptor<LogEvent>()
        verify(logger).log(eventCaptor.capture(), any(), any())
        assertTrue(eventCaptor.firstValue is LogEvent.Failure)
        (eventCaptor.firstValue as LogEvent.Failure).let { event ->
            assertEquals(expectedSource, event.source)
            assertEquals(expectedError, event.error)
        }
    }

    private fun getDigitizationStateHandler(
        completionHandler: (Result<DigitizationState>) -> Unit,
    ) {
        card.getDigitizationState(
            token = TOKEN,
            completionHandler = completionHandler,
        )

        testScope.testScheduler.advanceUntilIdle()
    }

    private fun getProvisionHandler(
        completionHandler: (Result<Unit>) -> Unit,
    ) {
        card.provision(
            activity = activity,
            token = TOKEN,
            completionHandler = completionHandler,
        )

        testScope.testScheduler.advanceUntilIdle()
    }

    private companion object {
        private const val TOKEN = "TOKEN"

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
