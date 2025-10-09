package com.checkout.cardmanagement

import android.app.Activity
import android.content.Context
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.TextStyle
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
import com.checkout.cardmanagement.model.Environment.SANDBOX
import com.checkout.cardmanagement.model.ProvisioningConfiguration
import com.checkout.cardmanagement.model.copyPan
import com.checkout.cardmanagement.model.getPANAndSecurityCode
import com.checkout.cardmanagement.model.getPan
import com.checkout.cardmanagement.model.getPin
import com.checkout.cardmanagement.model.getSecurityCode
import com.checkout.cardmanagement.model.parse
import com.checkout.cardmanagement.model.toCardManagementError
import com.checkout.cardnetwork.CardService
import com.checkout.cardnetwork.CheckoutCardService
import com.checkout.cardnetwork.common.model.CardNetworkError
import com.checkout.cardnetwork.common.model.Environment
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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

internal class CheckoutCardManagerTest {
    private val activity: Activity = mock()
    private val cardService: CardService = mock()
    private val checkoutCardService: CheckoutCardService = mock()
    private val logger: CheckoutEventLogger = mock()
    private val context: Context = mock()
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
        assertNull(manager.sessionToken)
    }

    @Test
    fun `logInSession should update sessionToken if the new token is valid`() {
        assertTrue(manager.logInSession(VALID_TOKEN))
        assertEquals(manager.sessionToken, VALID_TOKEN)
        assertTrue(manager.logInSession(VALID_TOKEN_2))
        assertEquals(manager.sessionToken, VALID_TOKEN_2)
    }

    @Test
    fun `logInSession should not update sessionToken if token is invalid`() {
        assertFalse(manager.logInSession(INVALID_TOKEN))
        assertNull(manager.sessionToken)
    }

    @Test
    fun `logInSession should wipe out previous sessionToken if the new token is invalid`() {
        assertTrue(manager.logInSession(VALID_TOKEN))
        assertEquals(manager.sessionToken, VALID_TOKEN)
        assertFalse(manager.logInSession(INVALID_TOKEN))
        assertNull(manager.sessionToken)
    }

    @Test
    fun `logoutSession should null sessionToken`() {
        manager.logInSession(VALID_TOKEN)
        assertEquals(manager.sessionToken, VALID_TOKEN)
        manager.logoutSession()
        assertNull(manager.sessionToken)
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
        getAllCardManageErrors().forEach { cardNetworkError ->
            `when`(cardService.getCards(anyString())).thenReturn(
                flow {
                    emit(Result.failure(cardNetworkError))
                },
            )
            manager.logInSession(VALID_TOKEN)
            manager.getCards {
                assertTrue(it.isFailure)
                assertTrue(it.exceptionOrNull() is CardManagementError)
            }
        }
    }

    @Test
    fun `getCards should catch CardNetworkError and return CardManagementError`() {
        getAllCardManageErrors().forEach { cardNetworkError ->
            `when`(cardService.getCards(anyString())).thenReturn(
                flow {
                    throw cardNetworkError
                },
            )
            manager.logInSession(VALID_TOKEN)
            manager.getCards {
                assertTrue(it.isFailure)
                assertTrue(it.exceptionOrNull() is CardManagementError)
            }
        }
    }

    @Test
    fun `getCards should collect CardNetworkError and log it`() {
        `when`(cardService.getCards(anyString())).thenReturn(
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
        }
    }

    @Test
    fun `displayPin should call completionHandler with a failure Result if a failure result is returned`() {
        getAllCardManageErrors().forEach { cardNetworkError ->
            performDisplaySecureDataResult(
                secureDataType = SecureDataType.PIN,
                displaySecureDataResult = flow { emit(Result.failure(cardNetworkError)) },
                onReceivedSecureDataView = {
                    assertTrue(it.isFailure)
                    assertEquals(cardNetworkError.toCardManagementError(), it.exceptionOrNull())
                },
            )
        }
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
        getAllCardManageErrors().forEach { cardNetworkError ->
            performDisplaySecureDataResult(
                secureDataType = SecureDataType.PAN,
                displaySecureDataResult = flow { emit(Result.failure(cardNetworkError)) },
                onReceivedSecureDataView = {
                    assertTrue(it.isFailure)
                    assertEquals(cardNetworkError.toCardManagementError(), it.exceptionOrNull())
                    assertFailureLogEvent(
                        expectedSource = GET_PAN,
                        expectedError = cardNetworkError,
                    )
                },
            )
        }
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
        getAllCardManageErrors().forEach { cardNetworkError ->
            performDisplaySecureDataResult(
                secureDataType = SecureDataType.CVV,
                displaySecureDataResult = flow { emit(Result.failure(cardNetworkError)) },
                onReceivedSecureDataView = {
                    assertTrue(it.isFailure)
                    assertEquals(cardNetworkError.toCardManagementError(), it.exceptionOrNull())
                    assertFailureLogEvent(
                        expectedSource = GET_CVV,
                        expectedError = cardNetworkError,
                    )
                },
            )
        }
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
        getAllCardManageErrors().forEach { cardNetworkError ->
            performDisplaySecureDataPairResult(
                displaySecureDataResult = flow { emit(Result.failure(cardNetworkError)) },
                onReceivedSecureDataPairView = {
                    assertTrue(it.isFailure)
                    assertEquals(cardNetworkError.toCardManagementError(), it.exceptionOrNull())
                    assertFailureLogEvent(
                        expectedSource = GET_PAN_AND_CVV,
                        expectedError = cardNetworkError,
                    )
                },
            )
        }
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
        getAllCardManageErrors().forEach { cardNetworkError ->
            `when`(
                cardService.copyPan(
                    card.id,
                    SINGLE_USE_TOKEN,
                ),
            ).thenReturn(flow { emit(Result.failure(cardNetworkError)) })

            card.copyPan(SINGLE_USE_TOKEN) { result ->
                assertTrue(result.isFailure)
                assertEquals(cardNetworkError.toCardManagementError(), result.exceptionOrNull())
            }
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

        card.copyPan(SINGLE_USE_TOKEN) { result ->
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
        `when`(cardService.getCards(anyString()))
            .thenReturn(
                flow {
                    try {
                        emit(Result.success(NETWORK_CARD_LIST))
                    } catch (e: Exception) {
                        emit(Result.failure(e))
                    }
                },
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
                    CardManagementError.FetchDigitizationStateFailure(CardManagementError.DigitizationStateFailureType.OPERATION_FAILURE),
                )
            if (errorList.size != CardManagementError::class.sealedSubclasses.size) {
                throw Exception("One or more CardManagementError is missing in the errorList.")
            }
            return errorList
        }
    }
}
