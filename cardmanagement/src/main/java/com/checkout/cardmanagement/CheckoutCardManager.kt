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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * Access gateway into the Card Management functionality
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
     * Public facing constructor to instantiate the manager
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
    internal val service: CardService by lazy {
        val parsedEnvironment = environment.parse()
        checkoutCardService.initialize(context, parsedEnvironment).also { cardService ->
            cardService.setLogger(logger)
        }
    }

    // Generic token used for non sensitive calls
    internal var sessionToken: String? = null

    /**
     * Store provided token to use on network calls.
     *
     * Whenever a logInSession is called, the previous session token will be wiped.
     * If the new token is valid, it will be cached locally in the sessionToken.
     * If the new token in invalid, the local sessionToken will be assigned null.
     */
    public fun logInSession(token: String): Boolean {
        val isTokenValid = service.isTokenValid(token)
        sessionToken = if (isTokenValid) token else null
        return isTokenValid
    }

    /**
     * Remove current token from future calls
     */
    public fun logoutSession() {
        sessionToken = null
    }

    /**
     * Configure the Push Provisioning Manager
     */
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
     * Request a list of cards
     *
     * @param statuses Optional list of [CardState] values to filter cards by their state.
     * When provided, only cards matching the specified states will be returned.
     * Pass an empty list (default) to retrieve all cards regardless of their state.
     * Supported states: [CardState.ACTIVE], [CardState.INACTIVE], [CardState.SUSPENDED], [CardState.REVOKED]
     * @param completionHandler Callback that receives a [Result] containing either:
     * - Success: List of [Card] objects matching the filter criteria
     * - Failure: [CardManagementError] describing what went wrong
     *
     * Example usage:
     * ```
     * // Get all cards
     * manager.getCards { result ->
     *     result.onSuccess { cards -> /* handle cards */ }
     * }
     *
     * // Get only active and suspended cards
     * manager.getCards(statuses = listOf(CardState.ACTIVE, CardState.SUSPENDED)) { result ->
     *     result.onSuccess { cards -> /* handle filtered cards */ }
     * }
     * ```
     */
    public fun getCards(
        statuses: Set<CardState> = emptySet(),
        completionHandler: CardListResultCompletion,
    ) {
        if (sessionToken == null) {
            completionHandler(Result.failure(CardManagementError.Unauthenticated))
            return
        }

        runBlocking {
            launch {
                val startTime = Calendar.getInstance()
                service
                    .getCards(
                        sessionToken = sessionToken ?: "",
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
    }

    public fun getCard(
        cardId: String,
        completionHandler: CardResultCompletion,
    ) {
        coroutineScope.scope.launch {
            sessionToken?.let {
                val cardResult = service.getCard(cardId = cardId, sessionToken = it)

                cardResult.fold(
                    onSuccess = { domainCard ->
                        val card = Card.fromNetworkCard(networkCard = domainCard, manager = this@CheckoutCardManager)
                        completionHandler(Result.success(card))
                    },
                    onFailure = { error ->
                        completionHandler(Result.failure(error.toCardManagementError()))
                    },
                )
            } ?: completionHandler(Result.failure(CardManagementError.Unauthenticated))
        }
    }
}

/**
 * Completion handler returning a Result of a List of Card
 */
public typealias CardListResultCompletion = (Result<List<Card>>) -> Unit
public typealias CardResultCompletion = (Result<Card>) -> Unit
