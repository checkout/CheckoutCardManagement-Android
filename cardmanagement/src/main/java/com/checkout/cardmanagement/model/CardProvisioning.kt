package com.checkout.cardmanagement.model

import android.app.Activity
import android.content.Intent
import com.checkout.cardmanagement.logging.LogEvent
import com.checkout.cardmanagement.logging.LogEventSource
import com.checkout.cardmanagement.logging.LogEventUtils
import com.checkout.cardmanagement.utils.toCardOperationError
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Retrieves the digitization state of the card.
 *
 * Returns the current state indicating whether the card has been digitized
 * (added to Google Wallet) or not.
 *
 * Example usage:
 * ```kotlin
 * when (val result = card.getDigitizationState(token)) {
 *     is CardOperationResult.Success -> {
 *         when (result.data) {
 *             DigitizationState.DIGITIZED -> showDigitized()
 *             DigitizationState.NOT_DIGITIZED -> showAddToWallet()
 *         }
 *     }
 *     is CardOperationResult.Error -> showError(result.message)
 * }
 * ```
 *
 * @param token Push provisioning authentication token
 * @return Success with digitization state, or specific error
 * @since 3.0.0
 */
public suspend fun Card.getDigitizationState(token: String): CardOperationResult<DigitizationState> =
    getDigitizationStateImpl(token, isLegacyRequest = false)

/**
 * Internal implementation of [getDigitizationState] that accepts a legacy request flag for analytics.
 *
 * @param token Push provisioning authentication token
 * @param isLegacyRequest True if called from deprecated callback API, false if from suspend API
 * @return Success with digitization state, or specific error
 */
internal suspend fun Card.getDigitizationStateImpl(
    token: String,
    isLegacyRequest: Boolean,
): CardOperationResult<DigitizationState> {
    val startTime = Calendar.getInstance()
    val result = manager.service.getCardDigitizationState(cardId = id, token = token)

    return result.fold(
        onSuccess = { response ->
            val cardDigitizationState = DigitizationState.Companion.from(response)
            manager.logger.log(
                startedAt = startTime,
                event =
                    LogEvent.GetCardDigitizationState(
                        cardId = id,
                        cardDigitizationState,
                    ),
                additionalInfo =
                    mapOf(
                        LogEventUtils.KEY_CARD_ID to id,
                        LogEventUtils.KEY_LEGACY_REQUEST to isLegacyRequest.toString(),
                    ),
            )
            CardOperationResult.Success(cardDigitizationState)
        },
        onFailure = { error ->
            manager.logger.log(
                LogEvent.Failure(LogEventSource.GET_CARD_DIGITIZATION_STATE, error),
                startTime,
                mapOf(
                    LogEventUtils.KEY_CARD_ID to id,
                    LogEventUtils.KEY_LEGACY_REQUEST to isLegacyRequest.toString(),
                ),
            )
            error.toCardOperationError()
        },
    )
}

/**
 * Provisions the card to Google Wallet on the device.
 *
 * Initiates the flow to add the card to Google Pay, launching the necessary
 * UI for user consent and completion.
 *
 * Example usage:
 * ```kotlin
 * when (val result = card.provision(activity, token)) {
 *     is CardOperationResult.Success -> {
 *         showSuccess("Card added to wallet")
 *     }
 *     is CardOperationResult.Error -> showError(result.message)
 * }
 * ```
 *
 * @param activity Activity for receiving provisioning callbacks
 * @param token Push provisioning authentication token
 * @return Success (Unit), or specific error
 * @since 3.0.0
 */
public suspend fun Card.provision(
    activity: Activity,
    token: String,
): CardOperationResult<Unit> = provisionImpl(activity, token, isLegacyRequest = false)

/**
 * Internal implementation of [provision] that accepts a legacy request flag for analytics.
 *
 * @param activity Activity for receiving provisioning callbacks
 * @param token Push provisioning authentication token
 * @param isLegacyRequest True if called from deprecated callback API, false if from suspend API
 * @return Success (Unit), or specific error
 */
internal suspend fun Card.provisionImpl(
    activity: Activity,
    token: String,
    isLegacyRequest: Boolean,
): CardOperationResult<Unit> {
    val startTime = Calendar.getInstance()
    val result =
        manager.service.addCardToGoogleWallet(
            activity = activity,
            cardId = id,
            token = token,
        )

    return result.fold(
        onSuccess = {
            manager.logger.log(
                startedAt = startTime,
                event =
                    LogEvent.PushProvisioning(
                        cardId = id,
                    ),
                additionalInfo =
                    mapOf(
                        LogEventUtils.KEY_CARD_ID to id,
                        LogEventUtils.KEY_LEGACY_REQUEST to isLegacyRequest.toString(),
                    ),
            )
            CardOperationResult.Success(Unit)
        },
        onFailure = { error ->
            manager.logger.log(
                LogEvent.Failure(LogEventSource.PUSH_PROVISIONING, error),
                startTime,
                mapOf(LogEventUtils.KEY_CARD_ID to id, LogEventUtils.KEY_LEGACY_REQUEST to isLegacyRequest.toString()),
            )
            error.toCardOperationError()
        },
    )
}

/**
 * Handles the result from the card provisioning activity.
 *
 * This method should be called from your Activity's onActivityResult() to process
 * the result of the Google Wallet card provisioning flow.
 *
 * @param requestCode The request code originally supplied to startActivityForResult()
 * @param resultCode The result code returned by the child activity
 * @param data An Intent which can return result data to the caller
 */
public fun Card.handleCardResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?,
) {
    with(manager) {
        coroutineScope.scope.launch {
            service.handleActivityResult(requestCode, resultCode, data)
        }
    }
}
