package com.checkout.cardmanagement.model

import android.app.Activity
import com.checkout.cardmanagement.utils.getOrThrow
import kotlinx.coroutines.launch

/**
 * Requests the card digitization state.
 *
 * @param token Push provisioning authentication token
 * @param completionHandler Callback that receives Result with digitization state or error
 * @deprecated This callback-based API will be deprecated in a future version.
 * Prefer the coroutine-based suspend getDigitizationState() which returns CardOperationResult.
 */
@Deprecated(
    message =
        "This callback-based API will be deprecated in a future version. " +
            "Use suspend getDigitizationState() instead",
    replaceWith = ReplaceWith("getDigitizationState(token)"),
    level = DeprecationLevel.WARNING,
)
public fun Card.getDigitizationState(
    token: String,
    completionHandler: (Result<DigitizationState>) -> Unit,
): Unit =
    provisioningBridge(
        completionHandler = completionHandler,
        card = this,
    ) { isLegacy ->
        getDigitizationStateImpl(token, isLegacy)
    }

/**
 * Requests to add the card to Google Wallet on the device.
 *
 * @param activity Activity for receiving provisioning callbacks
 * @param token Push provisioning authentication token
 * @param completionHandler Callback that receives Result<Unit> indicating success or error
 * @deprecated This callback-based API will be deprecated in a future version.
 * Prefer the coroutine-based suspend provision() which returns CardOperationResult.
 */
@Deprecated(
    message = "This callback-based API will be deprecated in a future version. Use suspend provision() instead",
    replaceWith = ReplaceWith("provision(activity, token)"),
    level = DeprecationLevel.WARNING,
)
public fun Card.provision(
    activity: Activity,
    token: String,
    completionHandler: (Result<Unit>) -> Unit,
): Unit =
    provisioningBridge(
        completionHandler = completionHandler,
        card = this,
    ) { isLegacy ->
        provisionImpl(activity, token, isLegacy)
    }

/**
 * Internal helper function that bridges between the new sealed result pattern and the old callback pattern.
 *
 * This function:
 * 1. Calls the internal suspend function with isLegacyRequest = true
 * 2. Converts the sealed result to an exception using [getOrThrow]
 * 3. Wraps in a [Result] for the deprecated callback API
 *
 * @param T The type of data being retrieved
 * @param completionHandler Callback to receive the result
 * @param card The card instance on which the operation is performed
 * @param operation The internal suspend function that returns [CardOperationResult]
 */
private fun <T> provisioningBridge(
    completionHandler: (Result<T>) -> Unit,
    card: Card,
    operation: suspend Card.(Boolean) -> CardOperationResult<T>,
) {
    card.manager.coroutineScope.scope.launch {
        try {
            val result = card.operation(true)
            val data = result.getOrThrow()
            completionHandler(Result.success(data))
        } catch (e: Exception) {
            completionHandler(Result.failure(e))
        }
    }
}
