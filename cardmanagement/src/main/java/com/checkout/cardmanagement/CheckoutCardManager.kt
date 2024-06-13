package com.checkout.cardmanagement

import android.content.Context
import com.checkout.cardmanagement.logging.CheckoutEventLogger
import com.checkout.cardmanagement.logging.LogEvent
import com.checkout.cardmanagement.logging.LogEventSource.GET_CARDS
import com.checkout.cardmanagement.model.Card
import com.checkout.cardmanagement.model.CardManagementDesignSystem
import com.checkout.cardmanagement.model.CardManagementError
import com.checkout.cardmanagement.model.Environment
import com.checkout.cardmanagement.model.parse
import com.checkout.cardmanagement.model.toCardManagementError
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
     * Request a list of cards
     */
    public fun getCards(completionHandler: CardListResultCompletion) {
        if (sessionToken == null) {
            completionHandler(Result.failure(CardManagementError.Unauthenticated))
            return
        }

        runBlocking {
            launch {
                val startTime = Calendar.getInstance()
                service.getCards(sessionToken ?: "")
                    .catch {
                        completionHandler(Result.failure(it.toCardManagementError()))
                    }
                    .collect { result ->
                        result.onSuccess { cardList ->
                            val cards = cardList.cards.map { networkCard ->
                                Card.fromNetworkCard(networkCard, this@CheckoutCardManager)
                            }
                            logger.log(
                                LogEvent.CardList(cards.map { it.partIdentifier }),
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
}

/**
 * Completion handler returning a Result of a List of Card
 */
public typealias CardListResultCompletion = (Result<List<Card>>) -> Unit
