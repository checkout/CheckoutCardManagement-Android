package com.checkout.cardmanagement

import android.app.Activity
import android.content.Context
import com.checkout.cardmanagement.logging.CheckoutEventLogger
import com.checkout.cardmanagement.logging.LogEvent
import com.checkout.cardmanagement.logging.LogEventSource
import com.checkout.cardmanagement.logging.LogEventSource.GET_CARDS
import com.checkout.cardmanagement.logging.LogEventUtils.Companion.KEY_CARDHOLDER_ID
import com.checkout.cardmanagement.model.Card
import com.checkout.cardmanagement.model.CardManagementDesignSystem
import com.checkout.cardmanagement.model.CardManagementError
import com.checkout.cardmanagement.model.CardState
import com.checkout.cardmanagement.model.Environment
import com.checkout.cardmanagement.model.ProvisioningConfiguration
import com.checkout.cardmanagement.model.parse
import com.checkout.cardmanagement.model.toCardManagementError
import com.checkout.cardmanagement.model.toNetworkCardState
import com.checkout.cardmanagement.utils.CoroutineScopeOwner
import com.checkout.cardmanagement.utils.DefaultCoroutineScopeHandler
import com.checkout.cardnetwork.CardService
import com.checkout.cardnetwork.CheckoutCardService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Main access gateway to the Checkout Card Management SDK.
 *
 * CheckoutCardManager provides a comprehensive interface for managing payment cards, including
 * retrieving card information, configuring push provisioning for digital wallets (e.g., Google Pay),
 * and managing authentication sessions. This class serves as the primary entry point for all
 * card management operations within your application.
 *
 * @see CardManagementDesignSystem
 * @see Card
 * @see Environment
 */
public class CheckoutCardManager internal constructor(
    context: Context,
    environment: Environment,
    internal val designSystem: CardManagementDesignSystem,
    checkoutCardService: CheckoutCardService,
    internal val logger: CheckoutEventLogger,
    internal val coroutineScope: CoroutineScopeOwner,
) {
    init {
        logger.initialise(
            context = context,
            environment = environment.parse(),
            serviceVersion = CheckoutCardService.VERSION,
        )
        logger.log(LogEvent.Initialized(designSystem))
    }

    /**
     * Creates a new instance of CheckoutCardManager.
     *
     * @param context The Android application or activity context
     * @param designSystem The design system configuration for customizing UI elements and theming
     * @param environment The target environment - use [Environment.SANDBOX] for testing or
     *        [Environment.PRODUCTION] for live operations
     */
    public constructor(
        context: Context,
        designSystem: CardManagementDesignSystem,
        environment: Environment,
    ) : this(
        context,
        environment,
        designSystem,
        CheckoutCardService(),
        CheckoutEventLogger(),
        coroutineScope = DefaultCoroutineScopeHandler(),
    )

    // Service enabling interactions with outside services
    internal val service: CardService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        checkoutCardService
            .initialize(context, environment.parse())
            .also { cardService ->
                cardService.setLogger(logger)
            }
    }

    @Suppress("ktlint:standard:backing-property-naming")
    private val _sessionToken = MutableStateFlow<String?>(null)
    internal val sessionToken: StateFlow<String?> = _sessionToken

    /**
     * Authenticates and establishes a session for card management operations.
     *
     * This method validates and stores the provided session token, enabling access to session-dependent
     * operations such as [getCards] and other card management functions. The token is validated
     * before being stored to ensure it meets the required format and security criteria.
     *
     * **Important:** This method must be called successfully (returning true) before invoking any
     * session-dependent operations. Without a valid session, methods like [getCards] will fail with
     * [CardManagementError.Unauthenticated].
     *
     * @param token The session token obtained from your authentication system. This token is validated
     *        for format and structure before being accepted.
     * @return `true` if the token is valid and the session has been successfully established.
     *         `false` if the token is invalid - in this case, the session remains unauthenticated
     *         and the internal session token is not set
     *
     * @see logoutSession
     */
    public fun logInSession(token: String): Boolean {
        val isTokenValid = service.isTokenValid(token)
        if (isTokenValid) {
            _sessionToken.value = token
        }
        return isTokenValid
    }

    /**
     * Terminates the current session and cleans up associated resources.
     *
     * This method clears the stored session token and cancels all ongoing operations
     * managed by the CheckoutCardManager's internal scope. After calling this method, the session
     * is considered unauthenticated and all session-dependent operations will fail until a new
     * session is established.
     *
     * **Effects of calling this method:**
     * - The internal session token is set to null
     * - All ongoing coroutines in the manager's scope are cancelled
     * - Subsequent calls to session-dependent methods (like [getCards]) will throw
     *   [CardManagementError.Unauthenticated] until [logInSession] is called again successfully
     *
     * **When to call this method:**
     * - When the user explicitly logs out of your application
     * - When the session expires or is invalidated by your backend
     * - When switching between different user accounts
     * - As part of security best practices when the app goes to background (if required by your security policy)
     *
     * **Important:** Any ongoing card management operations will be cancelled. Ensure that critical
     * operations are completed or properly handled before calling this method.
     *
     * **Reusability:** After calling this method, you can call [logInSession] again with a new token
     * to establish a new session. The CheckoutCardManager instance remains fully functional and
     * can be reused across multiple login/logout cycles.
     *
     * @see logInSession
     */
    public fun logoutSession() {
        _sessionToken.value = null
        coroutineScope.cancel()
    }

    /**
     * Configures the Push Provisioning Manager to enable adding cards to digital wallets (e.g., Google Pay).
     *
     * Push provisioning allows cardholders to add their physical cards to digital wallet applications
     * on their device. This method initializes the provisioning configuration required for the wallet
     * integration process.
     *
     * This method executes asynchronously and invokes the completion handler with the result when finished.
     *
     * @param activity The Android Activity context required for wallet provider interactions
     * @param cardholderId The unique identifier for the cardholder whose card will be provisioned
     * @param configuration The [ProvisioningConfiguration] containing issuer credentials and endpoints
     *        obtained during merchant onboarding with Checkout.com
     * @param completionHandler Callback invoked with Result<Unit> upon completion.
     *        - Success: Result.success(Unit) indicates successful configuration
     *        - Failure: Result.failure(CardManagementError) with specific error type:
     *          - [CardManagementError.PushProvisioningFailure] if configuration or operation fails
     *          - [CardManagementError.ConfigurationIssue] if the provided configuration is invalid
     */
    @Deprecated(
        message = "Use suspend configurePushProvisioning() instead",
        replaceWith = ReplaceWith("configurePushProvisioning()"),
    )
    public fun configurePushProvisioning(
        activity: Activity,
        cardholderId: String,
        configuration: ProvisioningConfiguration,
        completionHandler: (Result<Unit>) -> Unit,
    ) {
        runBlocking {
            launch {
                val startTime = Calendar.getInstance()
                service.configurePushProvisioning(
                    activity,
                    cardholderId,
                    configuration.toNetworkConfig(),
                ) { result ->
                    result
                        .onSuccess {
                            logger.log(
                                startedAt = startTime,
                                event = LogEvent.ConfigurePushProvisioning(cardholderId),
                            )
                            completionHandler(result)
                        }.onFailure {
                            logger.log(
                                LogEvent.Failure(LogEventSource.CONFIGURE_PUSH_PROVISIONING, it),
                                startTime,
                                mapOf(KEY_CARDHOLDER_ID to cardholderId),
                            )
                            completionHandler(Result.failure(it.toCardManagementError()))
                        }
                }
            }
        }
    }

    /**
     * Configures the CheckoutCardManager to enable adding cards to Google Pay
     *
     * This method initializes the provisioning configuration required for the wallet integration process and we expect
     * it to be called before any push provisioning is attempted. Calling this once per session on app startup is ideal.
     *
     *
     * @param activity The Android Activity context required for wallet provider interactions
     * @param cardholderId The unique identifier for the cardholder whose card will be provisioned
     * @param configuration The [ProvisioningConfiguration] containing issuer credentials and endpoints
     *        obtained during merchant onboarding with Checkout.com
     * @return Result<Unit> indicating success or failure.
     *         - Success: Result.success(Unit) indicates successful configuration
     *         - Failure: Result.failure(CardManagementError) with specific error type:
     *           - [CardManagementError.PushProvisioningFailure] if configuration or operation fails
     *           - [CardManagementError.ConfigurationIssue] if the provided configuration is invalid
     *
     * Example:
     * ```kotlin
     * lifecycleScope.launch {
     *     val config = ProvisioningConfiguration(...)
     *     val result = cardManager.configurePushProvisioning(
     *         activity = this@MainActivity,
     *         cardholderId = "crh_123",
     *         configuration = config
     *     )
     *     result.onSuccess { /* Configuration complete */ }
     *           .onFailure { error -> /* Handle error */ }
     * }
     * ```
     *
     * @since 3.0.0
     */
    public suspend fun configurePushProvisioning(
        activity: Activity,
        cardholderId: String,
        configuration: ProvisioningConfiguration,
    ): Result<Unit> {
        val startTime = Calendar.getInstance()
        return service
            .configurePushProvisioning(
                activity = activity,
                cardholderId = cardholderId,
                configuration = configuration.toNetworkConfig(),
            ).onSuccess {
                logger.log(
                    startedAt = startTime,
                    event = LogEvent.ConfigurePushProvisioning(cardholderId),
                )
            }.onFailure {
                logger.log(
                    LogEvent.Failure(LogEventSource.CONFIGURE_PUSH_PROVISIONING, it),
                    startTime,
                    mapOf(KEY_CARDHOLDER_ID to cardholderId),
                )
            }
    }

    /**
     * Retrieves a list of all cards associated with the authenticated cardholder account.
     *
     * This method returns card information including card state, last 4 digits of PAN,
     * expiry date, cardholder name, and card ID. Each [Card] object provides methods for further
     * card management operations.
     *
     * **Important:** This method requires an active session. You must call [logInSession] with a
     * valid session token before invoking this method.
     *
     * @param completionHandler Callback invoked with Result<List<[Card]>> upon completion.
     *        - Success: Result.success(List<[Card]>) containing all cards associated with the account.
     *          Returns an empty list if no cards are found.
     *        - Failure: Result.failure([CardManagementError]) with specific error type:
     *          - [CardManagementError.Unauthenticated] if no session is active (logInSession not called
     *            or session token was rejected)
     *          - [CardManagementError.AuthenticationFailure] if the session token has expired or is
     *            no longer valid
     *          - [CardManagementError.ConnectionIssue] if there are network connectivity problems or
     *            server communication errors
     *          - [CardManagementError.ConfigurationIssue] if the SDK is misconfigured or the request
     *            parameters are invalid (check the hint property for guidance)
     *
     * @deprecated This callback-based API is deprecated. Migrate to the suspend function version:
     *             `suspend fun getCards(): List<Card>` for better coroutine support and error handling.
     *             This method will be removed in a future major version of the SDK.
     *
     * @see getCards Suspend function version (recommended)
     * @see Card
     * @see logInSession
     */
    @Deprecated(
        message = "Use suspend getCards() instead",
        replaceWith = ReplaceWith("getCards()"),
    )
    public fun getCards(
        statuses: Set<CardState> = emptySet(),
        completionHandler: CardListResultCompletion,
    ) {
        val token = _sessionToken.value
        if (token == null) {
            completionHandler(Result.failure(CardManagementError.Unauthenticated))
            return
        }

        coroutineScope.scope.launch {
            val startTime = Calendar.getInstance()
            service
                .getCards(
                    sessionToken = token,
                    statuses = statuses.mapNotNull { it.toNetworkCardState() }.toSet(),
                ).catch {
                    completionHandler(Result.failure(it.toCardManagementError()))
                }.collect { result ->
                    result
                        .onSuccess { cardList ->
                            val cards =
                                cardList.cards.map { networkCard ->
                                    Card.fromNetworkCard(networkCard, this@CheckoutCardManager)
                                }
                            logger.log(
                                event =
                                    LogEvent.CardList(
                                        cardIds = cards.map { it.id },
                                        requestedStatuses = statuses,
                                    ),
                                startTime,
                            )
                            completionHandler(Result.success(cards))
                        }.onFailure {
                            logger.log(LogEvent.Failure(source = GET_CARDS, it), startTime)
                            completionHandler(Result.failure(it.toCardManagementError()))
                        }
                }
        }
    }

    /**
     * Retrieves a specific card by its card ID.
     *
     * This method returns detailed card information. The returned [Card] object provides methods for
     * further card management operations.
     *
     * **Important:** This method requires an active session. You must call [logInSession] with a
     * valid session token before invoking this method.
     *
     * @param cardId The unique identifier of the card to retrieve
     * @param completionHandler Callback invoked with Result<[Card]> upon completion.
     *        - Success: Result.success([Card]) containing the requested card information.
     *        - Failure: Result.failure([CardManagementError]) with specific error type:
     *          - [CardManagementError.Unauthenticated] if no session is active (logInSession not called
     *            or session token was rejected)
     *          - [CardManagementError.AuthenticationFailure] if the session token has expired or is
     *            no longer valid
     *          - [CardManagementError.ConnectionIssue] if there are network connectivity problems or
     *            server communication errors
     *
     * @deprecated This callback-based API is deprecated. Migrate to the suspend function version:
     *             `suspend fun getCard(cardId: String): Card` for better coroutine support and error handling.
     *             This method will be removed in a future major version of the SDK.
     *
     * @see getCard Suspend function version (recommended)
     * @see Card
     * @see logInSession
     */
    @Deprecated(
        message = "Use suspend getCard(cardId: String) instead",
        replaceWith = ReplaceWith("getCard(cardId)"),
    )
    public fun getCard(
        cardId: String,
        completionHandler: CardResultCompletion,
    ) {
        val token = _sessionToken.value
        if (token == null) {
            completionHandler(Result.failure(CardManagementError.Unauthenticated))
            return
        }

        coroutineScope.scope.launch {
            val cardResult = service.getCard(cardId = cardId, sessionToken = token)

            cardResult.fold(
                onSuccess = { domainCard ->
                    val card = Card.fromNetworkCard(networkCard = domainCard, manager = this@CheckoutCardManager)
                    completionHandler(Result.success(card))
                },
                onFailure = { error ->
                    completionHandler(Result.failure(error.toCardManagementError()))
                },
            )
        }
    }

    /**
     * Retrieves a list of all cards associated with the authenticated cardholder account.
     *
     * This suspend function returns card information including card state, last 4 digits of PAN,
     * expiry date, cardholder name, and card ID. Each [Card] object provides methods for further
     * card management operations.
     *
     * **Important:** This method requires an active session. You must call [logInSession] with a
     * valid session token before invoking this method.
     *
     * @return List<[Card]> containing all cards associated with the account. Returns an empty list
     *         if no cards are found.
     * @throws CardManagementError.Unauthenticated if no session is active (logInSession not called
     *         or session token was rejected)
     * @throws CardManagementError.AuthenticationFailure if the session token has expired or is
     *         no longer valid
     * @throws CardManagementError.ConnectionIssue if there are network connectivity problems or
     *         server communication errors
     * @throws CardManagementError.ConfigurationIssue if the SDK is misconfigured or the request
     *         parameters are invalid (check the hint property for guidance)
     *
     * Example:
     * ```kotlin
     * viewModelScope.launch {
     *     runCatching { cardManager.getCards() }
     *         .onSuccess { cards ->
     *             cards.forEach { card -> println("Card: ${card.panLast4Digits}") }
     *         }
     *         .onFailure { error ->
     *             when (error) {
     *                 is CardManagementError.Unauthenticated -> // Prompt login
     *                 is CardManagementError.ConnectionIssue -> // Show network error
     *                 else -> // Handle other errors
     *             }
     *         }
     * }
     * ```
     *
     * @see Card
     * @see logInSession
     *
     *@since 3.0.0
     */
    @Throws(CardManagementError::class)
    public suspend fun getCards(statuses: Set<CardState> = emptySet()): List<Card> {
        val token = _sessionToken.value ?: throw CardManagementError.Unauthenticated
        return getCards(token = token, statuses = statuses)
    }

    /**
     * Retrieves a specific card by its card ID.
     *
     * This suspend function returns detailed card information including card state, last 4 digits of PAN,
     * expiry date, cardholder name, and card ID. The returned [Card] object provides methods for further
     * card management operations.
     *
     * **Important:** This method requires an active session. You must call [logInSession] with a
     * valid session token before invoking this method.
     *
     * @param cardId The unique identifier of the card to retrieve
     * @return [Card] containing the requested card information.
     * @throws CardManagementError.Unauthenticated if no session is active (logInSession not called
     *         or session token was rejected)
     * @throws CardManagementError.AuthenticationFailure if the session token has expired or is
     *         no longer valid
     * @throws CardManagementError.ConnectionIssue if there are network connectivity problems or
     *         server communication errors
     *
     * @throws CardManagementError.NotFound if the card in question is not found
     *
     * Example:
     * ```kotlin
     * viewModelScope.launch {
     *     runCatching { cardManager.getCard("crd_123") }
     *         .onSuccess { card ->
     *             println("Card: ${card.panLast4Digits}")
     *         }
     *         .onFailure { error ->
     *             when (error) {
     *                 is CardManagementError.Unauthenticated -> // Prompt login
     *                 is CardManagementError.ConnectionIssue -> // Show network error
     *                 else -> // Handle other errors
     *             }
     *         }
     * }
     * ```
     *
     * @see Card
     * @see logInSession
     *
     * @since 3.0.0
     */
    @Throws(CardManagementError::class)
    public suspend fun getCard(cardId: String): Card {
        val token = _sessionToken.value ?: throw CardManagementError.Unauthenticated
        return getCard(token = token, cardId = cardId)
    }

    private suspend fun getCards(
        token: String,
        statuses: Set<CardState>,
    ): List<Card> =
        withContext(Dispatchers.IO) {
            val startTime = Calendar.getInstance()
            service
                .getCards(token, statuses = statuses.mapNotNull { it.toNetworkCardState() }.toSet())
                .catch { throw it.toCardManagementError() }
                .first()
                .fold(
                    onSuccess = { cardList ->
                        val cards =
                            cardList.cards.map { networkCard ->
                                Card.fromNetworkCard(networkCard, this@CheckoutCardManager)
                            }
                        logger.log(
                            LogEvent.CardList(
                                cards.map { it.id },
                                requestedStatuses = statuses,
                            ),
                            startTime,
                        )
                        cards
                    },
                    onFailure = { error ->
                        logger.log(LogEvent.Failure(source = GET_CARDS, error), startTime)
                        throw error.toCardManagementError()
                    },
                )
        }

    private suspend fun getCard(
        token: String,
        cardId: String,
    ): Card =
        withContext(Dispatchers.IO) {
            val startTime = Calendar.getInstance()
            service
                .getCard(cardId = cardId, sessionToken = token)
                .fold(
                    onSuccess = { domainCard ->
                        val card = Card.fromNetworkCard(networkCard = domainCard, manager = this@CheckoutCardManager)
                        logger.log(
                            LogEvent.CardList(
                                listOf(card.id),
                                requestedStatuses = emptySet(),
                            ),
                            startTime,
                        )
                        card
                    },
                    onFailure = { error ->
                        logger.log(LogEvent.Failure(source = GET_CARDS, error), startTime)
                        throw error.toCardManagementError()
                    },
                )
        }
}

/**
 * Completion handler returning a Result of a List of Card
 */
public typealias CardListResultCompletion = (Result<List<Card>>) -> Unit
public typealias CardResultCompletion = (Result<Card>) -> Unit
