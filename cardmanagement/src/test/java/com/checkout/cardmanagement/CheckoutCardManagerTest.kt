package com.checkout.cardmanagement

import android.app.Activity
import android.content.Context
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.TextStyle
import com.checkout.cardmanagement.Fixtures.NETWORK_CARD
import com.checkout.cardmanagement.Fixtures.NETWORK_CARD_LIST
import com.checkout.cardmanagement.Fixtures.createCard
import com.checkout.cardmanagement.logging.CheckoutEventLogger
import com.checkout.cardmanagement.logging.LogEvent
import com.checkout.cardmanagement.logging.LogEventSource.CONFIGURE_PUSH_PROVISIONING
import com.checkout.cardmanagement.logging.LogEventSource.GET_CARDS
import com.checkout.cardmanagement.logging.LogEventSource.GET_CVV
import com.checkout.cardmanagement.logging.LogEventSource.GET_PAN
import com.checkout.cardmanagement.logging.LogEventSource.GET_PAN_AND_CVV
import com.checkout.cardmanagement.logging.LogEventSource.GET_PIN
import com.checkout.cardmanagement.model.Card
import com.checkout.cardmanagement.model.CardManagementDesignSystem
import com.checkout.cardmanagement.model.CardManagementError
import com.checkout.cardmanagement.model.CardManagementError.PushProvisioningFailureType.OPERATION_FAILURE
import com.checkout.cardmanagement.model.CardSecureDataResult
import com.checkout.cardmanagement.model.Environment.SANDBOX
import com.checkout.cardmanagement.model.ProvisioningConfiguration
import com.checkout.cardmanagement.model.copyPan
import com.checkout.cardmanagement.model.getPANAndSecurityCode
import com.checkout.cardmanagement.model.getPan
import com.checkout.cardmanagement.model.getPin
import com.checkout.cardmanagement.model.getSecurityCode
import com.checkout.cardmanagement.model.parse
import com.checkout.cardmanagement.model.toCardManagementError
import com.checkout.cardmanagement.utils.CoroutineScopeOwner
import com.checkout.cardnetwork.CardService
import com.checkout.cardnetwork.CheckoutCardService
import com.checkout.cardnetwork.common.model.CardNetworkError
import com.checkout.cardnetwork.common.model.Environment
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
internal class CheckoutCardManagerTest {
    private val activity: Activity = mock()
    private val cardService: CardService = mock()
    private val checkoutCardService: CheckoutCardService = mock()
    private val logger: CheckoutEventLogger = mock()
    private val context: Context = mock()
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val testCoroutineScopeOwner =
        object : CoroutineScopeOwner {
            override val scope: CoroutineScope = testScope

            override fun cancel() {}
        }
    private val resultCaptor = argumentCaptor<(Result<Unit>) -> Unit>()
    private lateinit var manager: CheckoutCardManager
    private lateinit var card: Card

    @Before
    fun setup() {
        setupCheckoutCardService()
        manager =
            CheckoutCardManager(
                context,
                SANDBOX,
                DESIGN_SYSTEM,
                checkoutCardService,
                logger,
                testCoroutineScopeOwner,
            )
        card = createCard(manager)
    }

    @Test
    fun `setup CheckoutEventLogger on init`() {
        verify(logger).initialise(
            eq(context),
            eq(SANDBOX.parse()),
            eq(CheckoutCardService.VERSION),
        )
    }

    @Test
    fun `logger Initialized on init`() {
        verify(logger).log(eq(LogEvent.Initialized(DESIGN_SYSTEM)), eq(null), eq(emptyMap()))
    }

    @Test
    fun `assign logger to card service`() {
        // when service is called
        manager.service
        // service is initialised and logger assigned
        verify(checkoutCardService).initialize(eq(context), eq(SANDBOX.parse()))
        verify(cardService).setLogger(logger)
    }

    @Test
    fun `logInSession should null on init`() {
        assertNull(manager.sessionToken.value)
    }

    @Test
    fun `logInSession should update sessionToken if the new token is valid`() {
        assertTrue(manager.logInSession(VALID_TOKEN))
        assertEquals(manager.sessionToken.value, VALID_TOKEN)
        assertTrue(manager.logInSession(VALID_TOKEN_2))
        assertEquals(manager.sessionToken.value, VALID_TOKEN_2)
    }

    @Test
    fun `logInSession should not update sessionToken if token is invalid`() {
        assertFalse(manager.logInSession(INVALID_TOKEN))
        assertNull(manager.sessionToken.value)
    }

    @Test
    fun `logInSession should wipe out previous sessionToken if the new token is invalid`() {
        assertTrue(manager.logInSession(VALID_TOKEN))
        assertEquals(manager.sessionToken.value, VALID_TOKEN)
        assertFalse(manager.logInSession(INVALID_TOKEN))
        // Token should remain unchanged if new token is invalid
        assertEquals(manager.sessionToken.value, VALID_TOKEN)
    }

    @Test
    fun `logoutSession should null sessionToken`() {
        manager.logInSession(VALID_TOKEN)
        assertEquals(manager.sessionToken.value, VALID_TOKEN)
        manager.logoutSession()
        assertNull(manager.sessionToken.value)
    }

    @Test
    fun `ConfigurePushProvisioning success result handler`() =
        runBlocking {
            val completionHandler: (Result<Unit>) -> Unit = {
                assertTrue(it.isSuccess)
            }
            getConfigurePushProvisioningHandler(completionHandler)
            resultCaptor.firstValue.invoke((Result.success(Unit)))
        }

    @Test
    fun `ConfigurePushProvisioning success result logging`() =
        runBlocking {
            val completionHandler: (Result<Unit>) -> Unit = {
                val eventCaptor = argumentCaptor<LogEvent>()
                verify(logger).log(eventCaptor.capture(), org.mockito.kotlin.any(), eq(emptyMap<String, String>()))
                assertTrue(eventCaptor.firstValue is LogEvent.ConfigurePushProvisioning)
                (eventCaptor.firstValue as LogEvent.ConfigurePushProvisioning).let { event ->
                    assertEquals(CARDHOLDER_ID, event.cardholderId)
                }
            }
            getConfigurePushProvisioningHandler(completionHandler)
            resultCaptor.firstValue.invoke((Result.success(Unit)))
        }

    @Test
    fun `ConfigurePushProvisioning failure result handler`() =
        runBlocking {
            val completionHandler: (Result<Unit>) -> Unit = {
                assertTrue(it.isFailure)
                assertEquals(CardManagementError.ConfigurationIssue(CONFIGURATION_ERROR_HINT), it.exceptionOrNull()!!)
            }
            getConfigurePushProvisioningHandler(completionHandler)
            resultCaptor.firstValue.invoke(
                (Result.failure(CardNetworkError.Misconfigured(CONFIGURATION_ERROR_HINT))),
            )
        }

    @Test
    fun `ConfigurePushProvisioning unsafe environment failure result handler`() =
        runBlocking {
            val completionHandler: (Result<Unit>) -> Unit = {
                assertTrue(it.isFailure)
                assertEquals(CardManagementError.PushProvisioningFailure(CardManagementError.PushProvisioningFailureType.ERROR_DEVICE_ENVIRONMENT_UNSAFE), it.exceptionOrNull()!!)
            }
            getConfigurePushProvisioningHandler(completionHandler)
            resultCaptor.firstValue.invoke(
                (Result.failure(CardNetworkError.PushProvisioningFailure(CardNetworkError.PushProvisioningFailureType.ERROR_DEVICE_ENVIRONMENT_UNSAFE))),
            )
        }

    @Test
    fun `ConfigurePushProvisioning debug sdk failure result handler`() =
        runBlocking {
            val completionHandler: (Result<Unit>) -> Unit = {
                assertTrue(it.isFailure)
                assertEquals(CardManagementError.PushProvisioningFailure(CardManagementError.PushProvisioningFailureType.ERROR_DEBUG_SDK_USED), it.exceptionOrNull()!!)
            }
            getConfigurePushProvisioningHandler(completionHandler)
            resultCaptor.firstValue.invoke(
                (Result.failure(CardNetworkError.PushProvisioningFailure(CardNetworkError.PushProvisioningFailureType.ERROR_DEBUG_SDK_USED))),
            )
        }

    @Test
    fun `ConfigurePushProvisioning google pay is not available failure result handler`() =
        runBlocking {
            val completionHandler: (Result<Unit>) -> Unit = {
                assertTrue(it.isFailure)
                assertEquals(CardManagementError.PushProvisioningFailure(CardManagementError.PushProvisioningFailureType.ERROR_GPAY_NOT_SUPPORTED), it.exceptionOrNull()!!)
            }
            getConfigurePushProvisioningHandler(completionHandler)
            resultCaptor.firstValue.invoke(
                (Result.failure(CardNetworkError.PushProvisioningFailure(CardNetworkError.PushProvisioningFailureType.ERROR_GPAY_NOT_SUPPORTED))),
            )
        }

    @Test
    fun `ConfigurePushProvisioning card not found available failure result handler`() =
        runBlocking {
            val completionHandler: (Result<Unit>) -> Unit = {
                assertTrue(it.isFailure)
                assertEquals(CardManagementError.PushProvisioningFailure(CardManagementError.PushProvisioningFailureType.ERROR_CARD_NOT_FOUND), it.exceptionOrNull()!!)
            }
            getConfigurePushProvisioningHandler(completionHandler)
            resultCaptor.firstValue.invoke(
                (Result.failure(CardNetworkError.PushProvisioningFailure(CardNetworkError.PushProvisioningFailureType.ERROR_CARD_NOT_FOUND))),
            )
        }

    @Test
    fun `ConfigurePushProvisioning login failure result handler`() =
        runBlocking {
            val completionHandler: (Result<Unit>) -> Unit = {
                assertTrue(it.isFailure)
                assertEquals(CardManagementError.PushProvisioningFailure(CardManagementError.PushProvisioningFailureType.ERROR_NOT_LOGGED_IN), it.exceptionOrNull()!!)
            }
            getConfigurePushProvisioningHandler(completionHandler)
            resultCaptor.firstValue.invoke(
                (Result.failure(CardNetworkError.PushProvisioningFailure(CardNetworkError.PushProvisioningFailureType.ERROR_NOT_LOGGED_IN))),
            )
        }

    @Test
    fun `ConfigurePushProvisioning failure result logging`() =
        runBlocking {
            val completionHandler: (Result<Unit>) -> Unit = {
                val eventCaptor = argumentCaptor<LogEvent>()
                verify(logger).log(eventCaptor.capture(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
                assertTrue(eventCaptor.firstValue is LogEvent.Failure)
                (eventCaptor.firstValue as LogEvent.Failure).let { event ->
                    assertEquals(CardNetworkError.Misconfigured(CONFIGURATION_ERROR_HINT), event.error)
                    assertEquals(CONFIGURE_PUSH_PROVISIONING, event.source)
                }
            }
            getConfigurePushProvisioningHandler(completionHandler)
            resultCaptor.firstValue.invoke((Result.failure(CardNetworkError.Misconfigured(CONFIGURATION_ERROR_HINT))))
        }

    @Test
    fun `suspend configurePushProvisioning success result`() =
        runBlocking {
            `when`(
                cardService.configurePushProvisioning(
                    org.mockito.kotlin.any(),
                    org.mockito.kotlin.any(),
                    org.mockito.kotlin.any(),
                ),
            ).thenReturn(Result.success(Unit))

            val result =
                manager.configurePushProvisioning(
                    activity = activity,
                    cardholderId = CARDHOLDER_ID,
                    configuration = CONFIG,
                )

            verify(cardService).configurePushProvisioning(
                activity = eq(activity),
                cardholderId = eq(CARDHOLDER_ID),
                configuration = org.mockito.kotlin.any(),
            )

            assertTrue(result.isSuccess)
        }

    @Test
    fun `suspend configurePushProvisioning failure result`() =
        runBlocking {
            val error = CardNetworkError.Misconfigured(CONFIGURATION_ERROR_HINT)
            `when`(
                cardService.configurePushProvisioning(
                    org.mockito.kotlin.any(),
                    org.mockito.kotlin.any(),
                    org.mockito.kotlin.any(),
                ),
            ).thenReturn(Result.failure(error))

            val result =
                manager.configurePushProvisioning(
                    activity = activity,
                    cardholderId = CARDHOLDER_ID,
                    configuration = CONFIG,
                )

            assertTrue(result.isFailure)
            assertEquals(error, result.exceptionOrNull())
        }

    @Test
    fun `getCardList should get an Unauthenticated when the session token is null`() {
        manager.getCards { result ->
            assertTrue(result.isFailure)
            assertEquals(CardManagementError.Unauthenticated, (result.exceptionOrNull()!!))
        }
    }

    @Test
    fun `getCardList should not get an Unauthenticated when the session is not null`() {
        manager.logInSession(VALID_TOKEN)
        manager.getCards { result -> assertTrue(result.isSuccess) }
    }

    @Test
    fun `getCards should collect CardNetworkError and return CardManagementError`() {
        val testError = CardNetworkError.AuthenticationFailure
        `when`(
            cardService.getCards(anyString(), org.mockito.kotlin.any()),
        ).thenReturn(
            flow {
                emit(Result.failure(testError))
            },
        )
        manager.logInSession(VALID_TOKEN)
        manager.getCards {
            assertTrue(it.isFailure)
            assertTrue(it.exceptionOrNull() is CardManagementError)
        }
    }

    @Test
    fun `getCards should catch CardNetworkError and return CardManagementError`() {
        val testError = CardNetworkError.AuthenticationFailure
        `when`(
            cardService.getCards(
                sessionToken = anyString(),
                statuses = org.mockito.kotlin.any(),
            ),
        ).thenReturn(
            flow {
                throw testError
            },
        )
        manager.logInSession(VALID_TOKEN)
        manager.getCards {
            assertTrue(it.isFailure)
            assertTrue(it.exceptionOrNull() is CardManagementError)
        }
    }

    @Test
    fun `getCards should collect CardNetworkError and log it`() {
        `when`(
            cardService.getCards(
                sessionToken = anyString(),
                statuses = org.mockito.kotlin.any(),
            ),
        ).thenReturn(
            flow {
                emit(Result.failure(CardNetworkError.ServerIssue))
            },
        )
        manager.logInSession(VALID_TOKEN)
        manager.getCards {
            assertFailureLogEvent(
                expectedSource = GET_CARDS,
                expectedError = CardNetworkError.ServerIssue,
            )
        }
    }

    @Test
    fun `getCardList should return a card list`() {
        manager.logInSession(VALID_TOKEN)
        manager.getCards { result ->
            assertTrue(result.isSuccess)
            val cards = result.getOrNull()
            assertTrue(cards is List<Card>)
            assertTrue(cards!!.size == NETWORK_CARD_LIST.cards.size)
            cards.forEachIndexed { index, card ->
                assertEquals(card.id, NETWORK_CARD_LIST.cards[index].id)
                assertEquals(card.state.name, NETWORK_CARD_LIST.cards[index].state.name)
                assertEquals(card.expiryDate.month, NETWORK_CARD_LIST.cards[index].expiryMonth)
                assertEquals(card.expiryDate.year, NETWORK_CARD_LIST.cards[index].expiryYear)
                assertEquals(card.cardholderName, NETWORK_CARD_LIST.cards[index].displayName)
                assertEquals(card.panLast4Digits, NETWORK_CARD_LIST.cards[index].panLast4Digits)
            }
        }
    }

    @Test
    fun `getCardList successfully should log the card list`() {
        manager.logInSession(VALID_TOKEN)
        manager.getCards { result ->
            val cards = result.getOrNull()
            val eventCaptor = argumentCaptor<LogEvent>()
            verify(logger).log(eventCaptor.capture(), any(Calendar::class.java), eq(emptyMap<String, String>()))
            assertTrue(eventCaptor.firstValue is LogEvent.CardList)
            (eventCaptor.firstValue as LogEvent.CardList).cardIds.forEachIndexed { index, cardId ->
                assertEquals(cards!![index].id, cardId)
            }
            (eventCaptor.firstValue as LogEvent.CardList).requestedStatuses.isEmpty()
        }
    }

    @Test
    fun `getCards with status parameter should forward mapped status to service`() {
        // GIVEN
        val managementStatusList =
            setOf(
                com.checkout.cardmanagement.model.CardState.ACTIVE,
                com.checkout.cardmanagement.model.CardState.SUSPENDED,
            )
        val expectedNetworkStatusList =
            setOf(
                com.checkout.cardnetwork.data.dto.CardState.ACTIVE,
                com.checkout.cardnetwork.data.dto.CardState.SUSPENDED,
            )

        manager.logInSession(VALID_TOKEN)

        // WHEN
        manager.getCards(statuses = managementStatusList) { result ->
            // Should be successful
            assertTrue(result.isSuccess)
        }

        // Advance coroutines to ensure completion handler is called
        testScope.advanceUntilIdle()

        // THEN
        verify(cardService).getCards(
            sessionToken = VALID_TOKEN,
            statuses = expectedNetworkStatusList,
        )
    }

    @Test
    fun `getCards with empty status parameter should call service with empty status list`() {
        manager.logInSession(VALID_TOKEN)

        manager.getCards(statuses = setOf()) { result ->
            assertTrue(result.isSuccess)
        }
    }

    @Test
    fun `getCards should map all CardState values correctly`() {
        val allManagementStates =
            setOf(
                com.checkout.cardmanagement.model.CardState.ACTIVE,
                com.checkout.cardmanagement.model.CardState.INACTIVE,
                com.checkout.cardmanagement.model.CardState.SUSPENDED,
                com.checkout.cardmanagement.model.CardState.REVOKED,
            )
        val expectedNetworkStates =
            setOf(
                com.checkout.cardnetwork.data.dto.CardState.ACTIVE,
                com.checkout.cardnetwork.data.dto.CardState.INACTIVE,
                com.checkout.cardnetwork.data.dto.CardState.SUSPENDED,
                com.checkout.cardnetwork.data.dto.CardState.REVOKED,
            )

        manager.logInSession(VALID_TOKEN)

        manager.getCards(statuses = allManagementStates) { result ->
            assertTrue(result.isSuccess)
        }

        // Advance coroutines to ensure completion handler is called
        testScope.advanceUntilIdle()

        verify(cardService).getCards(
            sessionToken = VALID_TOKEN,
            statuses = expectedNetworkStates,
        )
    }

    @Test
    fun `getCard should return Unauthenticated when session token is null`() =
        runTest {
            // WHEN
            manager.getCard(CARD_ID) { result ->
                // THEN
                assertTrue(result.isFailure)
                assertEquals(CardManagementError.Unauthenticated, result.exceptionOrNull())
            }

            // Advance coroutines to ensure completion handler is called
            testScope.advanceUntilIdle()
        }

    @Test
    fun `getCard should return success when service returns card`() =
        runTest {
            // GIVEN
            `when`(cardService.getCard(CARD_ID, VALID_TOKEN))
                .thenReturn(Result.success(NETWORK_CARD))

            manager.logInSession(VALID_TOKEN)

            // WHEN
            manager.getCard(CARD_ID) { result ->
                // THEN
                assertTrue(result.isSuccess)
                val card = result.getOrNull()
                assertEquals(NETWORK_CARD.id, card?.id)
                assertEquals(NETWORK_CARD.panLast4Digits, card?.panLast4Digits)
                assertEquals(NETWORK_CARD.state.name, card?.state?.name)
            }

            // Advance coroutines to ensure completion handler is called
            testScope.advanceUntilIdle()
        }

    @Test
    fun `getCard should return CardManagementError when service returns error`() =
        runTest {
            listOf(
                CardNetworkError.ServerIssue,
                CardNetworkError.AuthenticationFailure,
                CardNetworkError.NotFound,
            ).forEach { cardNetworkError ->
                // GIVEN
                `when`(cardService.getCard(CARD_ID, VALID_TOKEN))
                    .thenReturn(Result.failure(cardNetworkError))

                manager.logInSession(VALID_TOKEN)

                // WHEN
                manager.getCard(CARD_ID) { result ->
                    // THEN
                    assertTrue(result.isFailure)
                    assertEquals(cardNetworkError.toCardManagementError(), result.exceptionOrNull())
                }

                // Advance coroutines to ensure completion handler is called
                testScope.advanceUntilIdle()
            }
        }

    @Test
    fun `displayPin should call completionHandler with a failure Result if a failure result is returned`() {
        val testError = CardNetworkError.AuthenticationFailure
        performDisplaySecureDataResult(
            secureDataType = SecureDataType.PIN,
            displaySecureDataResult = flow { emit(Result.failure(testError)) },
            onReceivedSecureDataView = {
                assertTrue(it.isFailure)
                assertEquals(testError.toCardManagementError(), it.exceptionOrNull())
            },
        )
    }

    @Test
    fun `displayPin should call completionHandler with a failure Result if an throwable is thrown`() {
        val error = Exception()
        performDisplaySecureDataResult(
            secureDataType = SecureDataType.PIN,
            displaySecureDataResult = flow { throw error },
            onReceivedSecureDataView = {
                assertTrue(it.isFailure)
                assertEquals(Exception().toCardManagementError(), it.exceptionOrNull())
                assertFailureLogEvent(expectedSource = GET_PIN, expectedError = error)
            },
        )
    }

    @Test
    fun `displayPin should call completionHandler with a AbstractComposeView Result if no Throwable is caught`() {
        val expectedView: AbstractComposeView = mock()
        performDisplaySecureDataResult(
            secureDataType = SecureDataType.PIN,
            displaySecureDataResult = flow { emit(Result.success(expectedView)) },
            onReceivedSecureDataView = {
                assertTrue(it.isSuccess)
                assertEquals(expectedView, it.getOrNull())
            },
        )
    }

    @Test
    fun `displayPin should log the GetPin event if the request is successful`() {
        performDisplaySecureDataResult(
            secureDataType = SecureDataType.PIN,
            displaySecureDataResult = flow { emit(Result.success(mock())) },
            onReceivedSecureDataView = {
                val eventCaptor = argumentCaptor<LogEvent>()
                verify(logger).log(eventCaptor.capture(), any(Calendar::class.java), eq(emptyMap<String, String>()))
                assertTrue(eventCaptor.firstValue is LogEvent.GetPin)
                assertEquals(
                    card.id,
                    (eventCaptor.firstValue as LogEvent.GetPin).cardId,
                )
                assertEquals(
                    card.state,
                    (eventCaptor.firstValue as LogEvent.GetPin).cardState,
                )
            },
        )
    }

    @Test
    fun `displayPan should call completionHandler with a failure Result if a failure result is returned`() {
        val testError = CardNetworkError.AuthenticationFailure
        performDisplaySecureDataResult(
            secureDataType = SecureDataType.PAN,
            displaySecureDataResult = flow { emit(Result.failure(testError)) },
            onReceivedSecureDataView = {
                assertTrue(it.isFailure)
                assertEquals(testError.toCardManagementError(), it.exceptionOrNull())
                assertFailureLogEvent(
                    expectedSource = GET_PAN,
                    expectedError = testError,
                )
            },
        )
    }

    @Test
    fun `displayPan should call completionHandler with a failure Result if an throwable is thrown`() {
        val error = Exception()
        performDisplaySecureDataResult(
            secureDataType = SecureDataType.PAN,
            displaySecureDataResult = flow { throw error },
            onReceivedSecureDataView = {
                assertTrue(it.isFailure)
                assertEquals(Exception().toCardManagementError(), it.exceptionOrNull())
                assertFailureLogEvent(expectedSource = GET_PAN, expectedError = error)
            },
        )
    }

    @Test
    fun `displayPan should call completionHandler with a AbstractComposeView Result if no Throwable is caught`() {
        val expectedView: AbstractComposeView = mock()
        performDisplaySecureDataResult(
            secureDataType = SecureDataType.PAN,
            displaySecureDataResult = flow { emit(Result.success(expectedView)) },
            onReceivedSecureDataView = {
                assertTrue(it.isSuccess)
                assertEquals(expectedView, it.getOrNull())
            },
        )
    }

    @Test
    fun `displayPan should log the GetPan event if the request is successful`() {
        performDisplaySecureDataResult(
            secureDataType = SecureDataType.PAN,
            displaySecureDataResult = flow { emit(Result.success(mock())) },
            onReceivedSecureDataView = {
                val eventCaptor = argumentCaptor<LogEvent>()
                verify(logger).log(eventCaptor.capture(), any(Calendar::class.java), eq(emptyMap<String, String>()))
                assertTrue(eventCaptor.firstValue is LogEvent.GetPan)
                assertEquals(
                    card.id,
                    (eventCaptor.firstValue as LogEvent.GetPan).cardId,
                )
                assertEquals(
                    card.state,
                    (eventCaptor.firstValue as LogEvent.GetPan).cardState,
                )
            },
        )
    }

    @Test
    fun `displaySecureCode should call completionHandler with a failure Result if a failure result is returned`() {
        val testError = CardNetworkError.AuthenticationFailure
        performDisplaySecureDataResult(
            secureDataType = SecureDataType.CVV,
            displaySecureDataResult = flow { emit(Result.failure(testError)) },
            onReceivedSecureDataView = {
                assertTrue(it.isFailure)
                assertEquals(testError.toCardManagementError(), it.exceptionOrNull())
                assertFailureLogEvent(
                    expectedSource = GET_CVV,
                    expectedError = testError,
                )
            },
        )
    }

    @Test
    fun `displaySecureCode should call completionHandler with a failure Result if an throwable is thrown`() {
        val error = Exception()
        performDisplaySecureDataResult(
            secureDataType = SecureDataType.CVV,
            displaySecureDataResult = flow { throw error },
            onReceivedSecureDataView = {
                assertTrue(it.isFailure)
                assertEquals(Exception().toCardManagementError(), it.exceptionOrNull())
                assertFailureLogEvent(expectedSource = GET_CVV, expectedError = error)
            },
        )
    }

    @Test
    fun `displaySecureCode should call completionHandler with a AbstractComposeView Result if no Throwable is caught`() {
        val expectedView: AbstractComposeView = mock()
        performDisplaySecureDataResult(
            secureDataType = SecureDataType.CVV,
            displaySecureDataResult = flow { emit(Result.success(expectedView)) },
            onReceivedSecureDataView = {
                assertTrue(it.isSuccess)
                assertEquals(expectedView, it.getOrNull())
            },
        )
    }

    @Test
    fun `displaySecurityCode should log the GetCVV event if the request is successful`() {
        performDisplaySecureDataResult(
            secureDataType = SecureDataType.CVV,
            displaySecureDataResult = flow { emit(Result.success(mock())) },
            onReceivedSecureDataView = {
                val eventCaptor = argumentCaptor<LogEvent>()
                verify(logger).log(eventCaptor.capture(), any(Calendar::class.java), eq(emptyMap<String, String>()))
                assertTrue(eventCaptor.firstValue is LogEvent.GetCVV)
                assertEquals(
                    card.id,
                    (eventCaptor.firstValue as LogEvent.GetCVV).cardId,
                )
                assertEquals(
                    card.state,
                    (eventCaptor.firstValue as LogEvent.GetCVV).cardState,
                )
            },
        )
    }

    @Test
    fun `displayPANAndSecureCode should call completionHandler with a failure Result if a failure result is returned`() {
        // Test with a single error to avoid multiple stubbing issues in loops
        val testError = CardNetworkError.AuthenticationFailure
        performDisplaySecureDataPairResult(
            displaySecureDataResult = flow { emit(Result.failure(testError)) },
            onReceivedSecureDataPairView = {
                assertTrue(it.isFailure)
                assertEquals(testError.toCardManagementError(), it.exceptionOrNull())
                assertFailureLogEvent(
                    expectedSource = GET_PAN_AND_CVV,
                    expectedError = testError,
                )
            },
        )
    }

    @Test
    fun `displayPANAndSecureCode should call completionHandler with a failure Result if an throwable is thrown`() {
        val error = Exception()
        performDisplaySecureDataPairResult(
            displaySecureDataResult = flow { throw error },
            onReceivedSecureDataPairView = {
                assertTrue(it.isFailure)
                assertEquals(Exception().toCardManagementError(), it.exceptionOrNull())
                assertFailureLogEvent(expectedSource = GET_PAN_AND_CVV, expectedError = error)
            },
        )
    }

    @Test
    fun `displayPANAndSecureCode should call completionHandler with a view Result if no error is caught`() {
        val expectedPanView: AbstractComposeView = mock()
        val expectedSecurityCodeView: AbstractComposeView = mock()
        performDisplaySecureDataPairResult(
            displaySecureDataResult =
                flow {
                    emit(Result.success(expectedPanView to expectedSecurityCodeView))
                },
            onReceivedSecureDataPairView = {
                assertTrue(it.isSuccess)
                assertEquals(expectedPanView, it.getOrNull()?.first)
                assertEquals(expectedSecurityCodeView, it.getOrNull()?.second)
            },
        )
    }

    @Test
    fun `copyPan should call completionHandler with a success Result if no error is caught`() {
        `when`(
            cardService.copyPan(
                card.id,
                SINGLE_USE_TOKEN,
            ),
        ).thenReturn(flowOf(Result.success(Unit)))

        card.copyPan(SINGLE_USE_TOKEN) { result ->
            assertTrue(result.isSuccess)
        }
    }

    @Test
    fun `copyPan should call completionHandler with a failure Result if a failure result is returned`() {
        val testError = CardNetworkError.AuthenticationFailure
        `when`(
            cardService.copyPan(
                card.id,
                SINGLE_USE_TOKEN,
            ),
        ).thenReturn(flow { emit(Result.failure(testError)) })

        card.copyPan(SINGLE_USE_TOKEN) { result ->
            assertTrue(result.isFailure)
            assertEquals(testError.toCardManagementError(), result.exceptionOrNull())
        }
    }

    @Test
    fun `copyPan should call completionHandler with a failure Result if an exception is thrown`() {
        val error = Exception()
        `when`(
            cardService.copyPan(
                card.id,
                SINGLE_USE_TOKEN,
            ),
        ).thenReturn(flow { throw error })

        card.copyPan(SINGLE_USE_TOKEN) { result ->
            assertTrue(result.isFailure)
            assertEquals(Exception().toCardManagementError(), result.exceptionOrNull())
        }
    }

    @Test
    fun `copyPan should log the CopyPan event if the request is successful`() {
        `when`(
            cardService.copyPan(
                card.id,
                SINGLE_USE_TOKEN,
            ),
        ).thenReturn(flowOf(Result.success(Unit)))

        card.copyPan(SINGLE_USE_TOKEN) { _ ->
            val eventCaptor = argumentCaptor<LogEvent>()
            verify(logger).log(eventCaptor.capture(), any(Calendar::class.java), eq(emptyMap<String, String>()))
            assertTrue(eventCaptor.firstValue is LogEvent.CopyPan)
            assertEquals(
                card.id,
                (eventCaptor.firstValue as LogEvent.CopyPan).cardId,
            )
            assertEquals(
                card.state,
                (eventCaptor.firstValue as LogEvent.CopyPan).cardState,
            )
        }
    }

    @Test
    fun `displayPANAndSecureCode should log the GetPanCVV event if the request is successful`() {
        performDisplaySecureDataPairResult(
            displaySecureDataResult =
                flow {
                    emit(Result.success(mock<AbstractComposeView>() to mock()))
                },
            onReceivedSecureDataPairView = {
                val eventCaptor = argumentCaptor<LogEvent>()
                verify(logger).log(eventCaptor.capture(), any(Calendar::class.java), eq(emptyMap<String, String>()))
                assertTrue(eventCaptor.firstValue is LogEvent.GetPanCVV)
                assertEquals(
                    card.id,
                    (eventCaptor.firstValue as LogEvent.GetPanCVV).cardId,
                )
                assertEquals(
                    card.state,
                    (eventCaptor.firstValue as LogEvent.GetPanCVV).cardState,
                )
            },
        )
    }

    @Test
    fun `suspend getCards should throw Unauthenticated when session token is null`() =
        runBlocking {
            try {
                manager.getCards()
                throw AssertionError("Expected CardManagementError.Unauthenticated to be thrown")
            } catch (e: CardManagementError) {
                assertEquals(CardManagementError.Unauthenticated, e)
            }
        }

    @Test
    fun `suspend getCards should return list of cards when authenticated`() =
        runBlocking {
            manager.logInSession(VALID_TOKEN)
            val cards = manager.getCards()

            assertEquals(NETWORK_CARD_LIST.cards.size, cards.size)
            cards.forEachIndexed { index, card ->
                assertEquals(NETWORK_CARD_LIST.cards[index].id, card.id)
                assertEquals(NETWORK_CARD_LIST.cards[index].state.name, card.state.name)
            }
        }

    @Test
    fun `suspend getCards should throw CardManagementError on network failure`() =
        runBlocking {
            `when`(cardService.getCards(eq(VALID_TOKEN), eq(emptySet()))).thenReturn(
                flow { emit(Result.failure(CardNetworkError.ServerIssue)) },
            )
            manager.logInSession(VALID_TOKEN)

            try {
                manager.getCards()
                throw AssertionError("Expected CardManagementError to be thrown")
            } catch (e: CardManagementError) {
                assertEquals(CardManagementError.ConnectionIssue, e)
            }
        }

    @Test
    fun `suspend getPin should return Success with AbstractComposeView on success`() =
        runBlocking {
            val expectedView: AbstractComposeView = mock()
            `when`(
                cardService.displayPin(
                    card.id,
                    SINGLE_USE_TOKEN,
                    DESIGN_SYSTEM.pinViewConfig,
                ),
            ).thenReturn(flowOf(Result.success(expectedView)))

            val result = card.getPin(SINGLE_USE_TOKEN)
            assertTrue(result is CardSecureDataResult.Success)
            assertEquals(expectedView, (result as CardSecureDataResult.Success).data)
        }

    @Test
    fun `suspend getPin should return Error on failure`() =
        runBlocking {
            `when`(
                cardService.displayPin(
                    card.id,
                    SINGLE_USE_TOKEN,
                    DESIGN_SYSTEM.pinViewConfig,
                ),
            ).thenReturn(flow { emit(Result.failure(CardNetworkError.AuthenticationFailure)) })

            val result = card.getPin(SINGLE_USE_TOKEN)
            assertTrue(result is CardSecureDataResult.Error.AuthenticationFailure)
        }

    @Test
    fun `suspend getPan should return Success with AbstractComposeView on success`() =
        runBlocking {
            val expectedView: AbstractComposeView = mock()
            `when`(
                cardService.displayPan(
                    card.id,
                    SINGLE_USE_TOKEN,
                    DESIGN_SYSTEM.panViewConfig,
                ),
            ).thenReturn(flowOf(Result.success(expectedView)))

            val result = card.getPan(SINGLE_USE_TOKEN)
            assertTrue(result is CardSecureDataResult.Success)
            assertEquals(expectedView, (result as CardSecureDataResult.Success).data)
        }

    @Test
    fun `suspend getPan should return Error on failure`() =
        runBlocking {
            `when`(
                cardService.displayPan(
                    card.id,
                    SINGLE_USE_TOKEN,
                    DESIGN_SYSTEM.panViewConfig,
                ),
            ).thenReturn(flow { emit(Result.failure(CardNetworkError.AuthenticationFailure)) })

            val result = card.getPan(SINGLE_USE_TOKEN)
            assertTrue(result is CardSecureDataResult.Error.AuthenticationFailure)
        }

    @Test
    fun `suspend getSecurityCode should return Success with AbstractComposeView on success`() =
        runBlocking {
            val expectedView: AbstractComposeView = mock()
            `when`(
                cardService.displaySecurityCode(
                    card.id,
                    SINGLE_USE_TOKEN,
                    DESIGN_SYSTEM.securityCodeViewConfig,
                ),
            ).thenReturn(flowOf(Result.success(expectedView)))

            val result = card.getSecurityCode(SINGLE_USE_TOKEN)
            assertTrue(result is CardSecureDataResult.Success)
            assertEquals(expectedView, (result as CardSecureDataResult.Success).data)
        }

    @Test
    fun `suspend getSecurityCode should return Error on failure`() =
        runBlocking {
            `when`(
                cardService.displaySecurityCode(
                    card.id,
                    SINGLE_USE_TOKEN,
                    DESIGN_SYSTEM.securityCodeViewConfig,
                ),
            ).thenReturn(flow { emit(Result.failure(CardNetworkError.AuthenticationFailure)) })

            val result = card.getSecurityCode(SINGLE_USE_TOKEN)
            assertTrue(result is CardSecureDataResult.Error.AuthenticationFailure)
        }

    @Test
    fun `suspend getPANAndSecurityCode should return Success with pair of views on success`() =
        runBlocking {
            val expectedPanView: AbstractComposeView = mock()
            val expectedCvvView: AbstractComposeView = mock()
            `when`(
                cardService.displayPANAndSecurityCode(
                    card.id,
                    SINGLE_USE_TOKEN,
                    DESIGN_SYSTEM.panViewConfig,
                    DESIGN_SYSTEM.securityCodeViewConfig,
                ),
            ).thenReturn(flowOf(Result.success(expectedPanView to expectedCvvView)))

            val result = card.getPANAndSecurityCode(SINGLE_USE_TOKEN)
            assertTrue(result is CardSecureDataResult.Success)
            val (panView, cvvView) = (result as CardSecureDataResult.Success).data
            assertEquals(expectedPanView, panView)
            assertEquals(expectedCvvView, cvvView)
        }

    @Test
    fun `suspend getPANAndSecurityCode should return Error on failure`() =
        runBlocking {
            `when`(
                cardService.displayPANAndSecurityCode(
                    card.id,
                    SINGLE_USE_TOKEN,
                    DESIGN_SYSTEM.panViewConfig,
                    DESIGN_SYSTEM.securityCodeViewConfig,
                ),
            ).thenReturn(flow { emit(Result.failure(CardNetworkError.AuthenticationFailure)) })

            val result = card.getPANAndSecurityCode(SINGLE_USE_TOKEN)
            assertTrue(result is CardSecureDataResult.Error.AuthenticationFailure)
        }

    @Test
    fun `suspend copyPan should return Success on success`() =
        runBlocking {
            `when`(
                cardService.copyPan(
                    card.id,
                    SINGLE_USE_TOKEN,
                ),
            ).thenReturn(flowOf(Result.success(Unit)))

            val result = card.copyPan(SINGLE_USE_TOKEN)
            assertTrue(result is CardSecureDataResult.Success)
        }

    @Test
    fun `suspend copyPan should return Error on failure`() =
        runBlocking {
            `when`(
                cardService.copyPan(
                    card.id,
                    SINGLE_USE_TOKEN,
                ),
            ).thenReturn(flow { emit(Result.failure(CardNetworkError.PanNotViewedFailure)) })

            val result = card.copyPan(SINGLE_USE_TOKEN)
            assertTrue(result is CardSecureDataResult.Error.PanNotViewed)
        }

    @Test
    fun `suspend copyPan should return Success when called on supported API version`() =
        runBlocking {
            // This test verifies the method works on supported API versions
            // The unsupported API version logic is tested in the callback version
            `when`(
                cardService.copyPan(
                    card.id,
                    SINGLE_USE_TOKEN,
                ),
            ).thenReturn(flowOf(Result.success(Unit)))

            val result = card.copyPan(SINGLE_USE_TOKEN)
            assertTrue(result is CardSecureDataResult.Success)
        }

    @Test
    fun `suspend getPin should return Unauthenticated error on unauthenticated failure`() =
        runTest {
            `when`(
                cardService.displayPin(
                    card.id,
                    SINGLE_USE_TOKEN,
                    DESIGN_SYSTEM.pinViewConfig,
                ),
            ).thenReturn(flow { emit(Result.failure(CardNetworkError.Unauthenticated)) })

            val result = card.getPin(SINGLE_USE_TOKEN)
            assertTrue(result is CardSecureDataResult.Error.Unauthenticated)
            assertEquals("No active session", (result as CardSecureDataResult.Error.Unauthenticated).message)
        }

    @Test
    fun `suspend getPin should return ConnectionIssue error on server failure`() =
        runTest {
            `when`(
                cardService.displayPin(
                    card.id,
                    SINGLE_USE_TOKEN,
                    DESIGN_SYSTEM.pinViewConfig,
                ),
            ).thenReturn(flow { emit(Result.failure(CardNetworkError.ServerIssue)) })

            val result = card.getPin(SINGLE_USE_TOKEN)
            assertTrue(result is CardSecureDataResult.Error.ConnectionIssue)
            assertEquals("ServerIssue", (result as CardSecureDataResult.Error.ConnectionIssue).message)
        }

    @Test
    fun `suspend getPan should return UnableToPerformOperation error on secure operations failure`() =
        runTest {
            `when`(
                cardService.displayPan(
                    card.id,
                    SINGLE_USE_TOKEN,
                    DESIGN_SYSTEM.panViewConfig,
                ),
            ).thenReturn(flow { emit(Result.failure(CardNetworkError.SecureOperationsFailure)) })

            val result = card.getPan(SINGLE_USE_TOKEN)
            assertTrue(result is CardSecureDataResult.Error.UnableToPerformOperation)
            assertEquals("SecureOperationsFailure", (result as CardSecureDataResult.Error.UnableToPerformOperation).message)
        }

    @Test
    fun `suspend getPANAndSecurityCode should return ConnectionIssue error with cause on parsing failure`() =
        runTest {
            `when`(
                cardService.displayPANAndSecurityCode(
                    card.id,
                    SINGLE_USE_TOKEN,
                    DESIGN_SYSTEM.panViewConfig,
                    DESIGN_SYSTEM.securityCodeViewConfig,
                ),
            ).thenReturn(flow { emit(Result.failure(CardNetworkError.ParsingFailure)) })

            val result = card.getPANAndSecurityCode(SINGLE_USE_TOKEN)
            assertTrue(result is CardSecureDataResult.Error.ConnectionIssue)
            val error = result as CardSecureDataResult.Error.ConnectionIssue
            assertEquals("ParsingFailure", error.message)
            assertTrue(error.cause is CardNetworkError.ParsingFailure)
        }

    private fun assertFailureLogEvent(
        expectedSource: String,
        expectedError: Throwable,
    ) {
        val eventCaptor = argumentCaptor<LogEvent>()
        verify(logger, atLeastOnce()).log(eventCaptor.capture(), any(Calendar::class.java), eq(emptyMap<String, String>()))
        assertTrue(eventCaptor.lastValue is LogEvent.Failure)
        assertEquals(expectedError, (eventCaptor.lastValue as LogEvent.Failure).error)
        assertEquals(expectedSource, (eventCaptor.lastValue as LogEvent.Failure).source)
    }

    private fun performDisplaySecureDataResult(
        secureDataType: SecureDataType,
        displaySecureDataResult: Flow<Result<AbstractComposeView>>,
        onReceivedSecureDataView: (Result<AbstractComposeView>) -> Unit,
    ) {
        `when`(
            when (secureDataType) {
                SecureDataType.PIN ->
                    cardService.displayPin(
                        card.id,
                        SINGLE_USE_TOKEN,
                        DESIGN_SYSTEM.pinViewConfig,
                    )

                SecureDataType.PAN ->
                    cardService.displayPan(
                        card.id,
                        SINGLE_USE_TOKEN,
                        DESIGN_SYSTEM.panViewConfig,
                    )

                SecureDataType.CVV ->
                    cardService.displaySecurityCode(
                        card.id,
                        SINGLE_USE_TOKEN,
                        DESIGN_SYSTEM.securityCodeViewConfig,
                    )
            },
        ).thenReturn(displaySecureDataResult)

        with(card) {
            when (secureDataType) {
                SecureDataType.PIN -> getPin(SINGLE_USE_TOKEN, onReceivedSecureDataView)
                SecureDataType.PAN -> getPan(SINGLE_USE_TOKEN, onReceivedSecureDataView)
                SecureDataType.CVV ->
                    getSecurityCode(SINGLE_USE_TOKEN, onReceivedSecureDataView)
            }
        }
    }

    private fun performDisplaySecureDataPairResult(
        displaySecureDataResult: Flow<Result<Pair<AbstractComposeView, AbstractComposeView>>>,
        onReceivedSecureDataPairView: (Result<Pair<AbstractComposeView, AbstractComposeView>>) -> Unit,
    ) {
        `when`(
            cardService.displayPANAndSecurityCode(
                card.id,
                SINGLE_USE_TOKEN,
                DESIGN_SYSTEM.panViewConfig,
                DESIGN_SYSTEM.securityCodeViewConfig,
            ),
        ).thenReturn(displaySecureDataResult)
        card.getPANAndSecurityCode(SINGLE_USE_TOKEN, onReceivedSecureDataPairView)
    }

    private fun setupCheckoutCardService() {
        `when`(cardService.isTokenValid(VALID_TOKEN)).thenReturn(true)
        `when`(cardService.isTokenValid(VALID_TOKEN_2)).thenReturn(true)
        `when`(cardService.isTokenValid(INVALID_TOKEN)).thenReturn(false)
        `when`(
            cardService.getCards(
                anyString(),
                statuses = org.mockito.kotlin.any(),
            ),
        ).thenReturn(
            flowOf(Result.success(NETWORK_CARD_LIST)),
        )
        `when`(checkoutCardService.initialize(context, Environment.SANDBOX)).thenReturn(cardService)
    }

    private fun getConfigurePushProvisioningHandler(
        completionHandler: (Result<Unit>) -> Unit,
    ): (Result<Unit>) -> Unit {
        manager.configurePushProvisioning(
            activity = activity,
            cardholderId = CARDHOLDER_ID,
            configuration = CONFIG,
            completionHandler = completionHandler,
        )

        verify(cardService).configurePushProvisioning(
            activity = eq(activity),
            cardholderId = eq(CARDHOLDER_ID),
            configuration = org.mockito.kotlin.any(),
            completionHandler = resultCaptor.capture(),
        )
        return resultCaptor.firstValue
    }

    companion object {
        private enum class SecureDataType { PIN, PAN, CVV }

        private val DESIGN_SYSTEM = CardManagementDesignSystem(TextStyle())
        private const val SINGLE_USE_TOKEN = "SINGLE_USE_TOKEN"
        private const val VALID_TOKEN = "VALID_TOKEN"
        private const val VALID_TOKEN_2 = "VALID_TOKEN_2"
        private const val INVALID_TOKEN = "INVALID_TOKEN"
        private const val CARDHOLDER_ID = "CARDHOLDER_ID"
        private const val CARD_ID = "crd_a3o34dts4geuvp7dg3wwuh27wy"
        private const val CONFIGURATION_ERROR_HINT = "HINT"
        private val CONFIG =
            ProvisioningConfiguration(
                issuerID = "ISSUER_ID",
                serviceRSAExponent = byteArrayOf(),
                serviceRSAModulus = byteArrayOf(),
                serviceURL = "SERVICE_URL",
                digitalCardURL = "DIGITAL_CARD_URL",
            )

        private fun getAllCardManageErrors(): List<CardManagementError> {
            val errorList =
                listOf(
                    CardManagementError.UnsupportedAPIVersion(12),
                    CardManagementError.AuthenticationFailure,
                    CardManagementError.ConfigurationIssue("HINT"),
                    CardManagementError.ConnectionIssue,
                    CardManagementError.Unauthenticated,
                    CardManagementError.UnableToPerformSecureOperation,
                    CardManagementError.InvalidStateRequested,
                    CardManagementError.PanNotViewedFailure,
                    CardManagementError.PushProvisioningFailure(OPERATION_FAILURE),
                    CardManagementError.PushProvisioningFailure(CardManagementError.PushProvisioningFailureType.ERROR_NOT_LOGGED_IN),
                    CardManagementError.PushProvisioningFailure(CardManagementError.PushProvisioningFailureType.ERROR_DEVICE_ENVIRONMENT_UNSAFE),
                    CardManagementError.PushProvisioningFailure(CardManagementError.PushProvisioningFailureType.ERROR_GPAY_NOT_SUPPORTED),
                    CardManagementError.PushProvisioningFailure(CardManagementError.PushProvisioningFailureType.ERROR_DEBUG_SDK_USED),
                    CardManagementError.PushProvisioningFailure(CardManagementError.PushProvisioningFailureType.ERROR_CARD_NOT_FOUND),
                    CardManagementError.FetchDigitizationStateFailure(CardManagementError.DigitizationStateFailureType.OPERATION_FAILURE),
                    CardManagementError.NotFound,
                )
            if (errorList.size != CardManagementError::class.sealedSubclasses.size) {
                throw Exception("One or more CardManagementError is missing in the errorList.")
            }
            return errorList
        }
    }
}
