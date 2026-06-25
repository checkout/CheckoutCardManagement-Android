package com.checkout.cardmanagement.model

import com.checkout.cardmanagement.logging.CheckoutEventLogger
import com.checkout.cardmanagement.logging.LogEvent
import com.checkout.cardmanagement.logging.LogEventSource.ACTIVATE_CARD
import com.checkout.cardmanagement.logging.LogEventSource.REVOKE_CARD
import com.checkout.cardmanagement.logging.LogEventSource.SUSPEND_CARD
import com.checkout.cardmanagement.model.CardRevokeReason.STOLEN
import com.checkout.cardmanagement.model.CardState.ACTIVE
import com.checkout.cardmanagement.model.CardState.INACTIVE
import com.checkout.cardmanagement.model.CardState.REVOKED
import com.checkout.cardmanagement.model.CardState.SUSPENDED
import com.checkout.cardmanagement.model.CardSuspendReason.LOST
import com.checkout.cardmanagement.utils.CoroutineScopeOwner
import com.checkout.cardnetwork.CardService
import com.checkout.cardnetwork.common.model.CardNetworkError
import com.checkout.cardnetwork.common.model.CardNetworkError.ServerIssue
import com.checkout.cardnetwork.common.model.CardNetworkError.Unauthenticated
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor

@OptIn(ExperimentalCoroutinesApi::class)
internal class CardStateManagementSuspendTest {
    private val service: CardService = mock()
    private val manager: com.checkout.cardmanagement.CheckoutCardManager = mock()
    private val logger: CheckoutEventLogger = mock()
    private val sessionTokenFlow = MutableStateFlow<String?>("SESSION_TOKEN")

    private val testScope = TestScope(UnconfinedTestDispatcher())
    private val testCoroutineScopeOwner =
        object : CoroutineScopeOwner {
            override val scope: CoroutineScope = testScope

            override fun cancel() {}
        }

    private lateinit var card: Card

    @Before
    fun setup() {
        `when`(manager.logger).thenReturn(logger)
        `when`(manager.sessionToken).thenReturn(sessionTokenFlow)
        `when`(manager.service).thenReturn(service)
        `when`(manager.coroutineScope).thenReturn(testCoroutineScopeOwner)
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    // -- activate --

    @Test
    fun `suspend activate returns Success on flow success`() =
        runBlocking {
            card = createCard(INACTIVE)
            `when`(service.activateCard(any(), any()))
                .thenReturn(flow { emit(Result.success(Unit)) })

            val result = card.activate()

            assertTrue(result is CardOperationResult.Success)
        }

    @Test
    fun `suspend activate returns InvalidStateTransition when current state is not allowed`() =
        runBlocking {
            card = createCard(ACTIVE)

            val result = card.activate()

            assertTrue(result is CardOperationResult.Error.InvalidStateTransition)
            val error = result as CardOperationResult.Error.InvalidStateTransition
            assertEquals(ACTIVE, error.currentState)
            assertEquals(ACTIVE, error.requestedState)
        }

    @Test
    fun `suspend activate returns Unauthenticated when session token is null`() =
        runBlocking {
            card = createCard(INACTIVE)
            sessionTokenFlow.value = null

            val result = card.activate()

            assertTrue(result is CardOperationResult.Error.Unauthenticated)
        }

    @Test
    fun `suspend activate returns mapped Error when flow emits failure`() =
        runBlocking {
            card = createCard(INACTIVE)
            `when`(service.activateCard(any(), any()))
                .thenReturn(flow { emit(Result.failure(ServerIssue)) })

            val result = card.activate()

            assertTrue(result is CardOperationResult.Error)
        }

    @Test
    fun `suspend activate logs failure when flow emits failure`() =
        runBlocking {
            card = createCard(INACTIVE)
            `when`(service.activateCard(any(), any()))
                .thenReturn(flow { emit(Result.failure(ServerIssue)) })

            card.activate()

            assertFailureLogged(ACTIVATE_CARD, ServerIssue)
        }

    @Test
    fun `suspend activate returns Error when flow throws`() =
        runBlocking {
            card = createCard(INACTIVE)
            `when`(service.activateCard(any(), any()))
                .thenReturn(flow { throw ServerIssue })

            val result = card.activate()

            assertTrue(result is CardOperationResult.Error)
        }

    @Test
    fun `suspend activate logs StateManagement on success`() =
        runBlocking {
            card = createCard(INACTIVE)
            `when`(service.activateCard(any(), any()))
                .thenReturn(flow { emit(Result.success(Unit)) })

            card.activate()

            assertStateManagementLogged(
                expectedReason = null,
                expectedRequestedState = ACTIVE,
            )
        }

    // -- suspend --

    @Test
    fun `suspend suspend returns Success on flow success`() =
        runBlocking {
            card = createCard(ACTIVE)
            `when`(service.suspendCard(any(), any(), any()))
                .thenReturn(flow { emit(Result.success(Unit)) })

            val result = card.suspend(LOST)

            assertTrue(result is CardOperationResult.Success)
        }

    @Test
    fun `suspend suspend returns Success with null reason`() =
        runBlocking {
            card = createCard(ACTIVE)
            `when`(service.suspendCard(any(), anyOrNull(), any()))
                .thenReturn(flow { emit(Result.success(Unit)) })

            val result = card.suspend(null)

            assertTrue(result is CardOperationResult.Success)
        }

    @Test
    fun `suspend suspend returns InvalidStateTransition when current state is REVOKED`() =
        runBlocking {
            card = createCard(REVOKED)

            val result = card.suspend(LOST)

            assertTrue(result is CardOperationResult.Error.InvalidStateTransition)
        }

    @Test
    fun `suspend suspend returns Unauthenticated when session token is null`() =
        runBlocking {
            card = createCard(ACTIVE)
            sessionTokenFlow.value = null

            val result = card.suspend(LOST)

            assertTrue(result is CardOperationResult.Error.Unauthenticated)
        }

    @Test
    fun `suspend suspend returns Error when flow throws`() =
        runBlocking {
            card = createCard(ACTIVE)
            `when`(service.suspendCard(any(), any(), any()))
                .thenReturn(flow { throw Unauthenticated })

            val result = card.suspend(LOST)

            assertTrue(result is CardOperationResult.Error)
        }

    @Test
    fun `suspend suspend logs failure when flow emits failure`() =
        runBlocking {
            card = createCard(ACTIVE)
            `when`(service.suspendCard(any(), any(), any()))
                .thenReturn(flow { emit(Result.failure(ServerIssue)) })

            card.suspend(LOST)

            assertFailureLogged(SUSPEND_CARD, ServerIssue)
        }

    // -- revoke --

    @Test
    fun `suspend revoke returns Success on flow success`() =
        runBlocking {
            card = createCard(INACTIVE)
            `when`(service.revokeCard(any(), any(), any()))
                .thenReturn(flow { emit(Result.success(Unit)) })

            val result = card.revoke(STOLEN)

            assertTrue(result is CardOperationResult.Success)
        }

    @Test
    fun `suspend revoke returns Success with null reason`() =
        runBlocking {
            card = createCard(INACTIVE)
            `when`(service.revokeCard(any(), anyOrNull(), any()))
                .thenReturn(flow { emit(Result.success(Unit)) })

            val result = card.revoke(null)

            assertTrue(result is CardOperationResult.Success)
        }

    @Test
    fun `suspend revoke returns InvalidStateTransition when already REVOKED`() =
        runBlocking {
            card = createCard(REVOKED)

            val result = card.revoke(STOLEN)

            assertTrue(result is CardOperationResult.Error.InvalidStateTransition)
        }

    @Test
    fun `suspend revoke returns Unauthenticated when session token is null`() =
        runBlocking {
            card = createCard(INACTIVE)
            sessionTokenFlow.value = null

            val result = card.revoke(STOLEN)

            assertTrue(result is CardOperationResult.Error.Unauthenticated)
        }

    @Test
    fun `suspend revoke logs failure when flow emits failure`() =
        runBlocking {
            card = createCard(INACTIVE)
            `when`(service.revokeCard(any(), any(), any()))
                .thenReturn(flow { emit(Result.failure(ServerIssue)) })

            card.revoke(STOLEN)

            assertFailureLogged(REVOKE_CARD, ServerIssue)
        }

    @Test
    fun `suspend revoke logs StateManagement on success`() =
        runBlocking {
            card = createCard(ACTIVE)
            `when`(service.revokeCard(any(), any(), any()))
                .thenReturn(flow { emit(Result.success(Unit)) })

            card.revoke(STOLEN)

            assertStateManagementLogged(
                expectedReason = "reported_stolen",
                expectedRequestedState = REVOKED,
            )
        }

    @Test
    fun `suspend revoke returns Error when flow throws`() =
        runBlocking {
            card = createCard(INACTIVE)
            `when`(service.revokeCard(any(), any(), any()))
                .thenReturn(flow { throw ServerIssue })

            val result = card.revoke(STOLEN)

            assertTrue(result is CardOperationResult.Error)
        }

    @Test
    fun `suspend suspend logs StateManagement on success with reason`() =
        runBlocking {
            card = createCard(ACTIVE)
            `when`(service.suspendCard(any(), any(), any()))
                .thenReturn(flow { emit(Result.success(Unit)) })

            card.suspend(LOST)

            assertStateManagementLogged(
                expectedReason = "suspected_lost",
                expectedRequestedState = SUSPENDED,
            )
        }

    private fun assertFailureLogged(
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

    private fun assertStateManagementLogged(
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

    private fun createCard(state: CardState): Card =
        Card(
            state = state,
            id = "CARD_ID",
            panLast4Digits = "1234",
            expiryDate = CardExpiryDate("11", "25"),
            cardholderName = "John Smith",
            manager = manager,
        )
}
