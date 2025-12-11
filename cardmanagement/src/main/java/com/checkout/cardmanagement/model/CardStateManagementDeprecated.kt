package com.checkout.cardmanagement.model

import com.checkout.cardmanagement.utils.getOrThrow
import kotlinx.coroutines.launch

/**
 * Requests to activate the card.
 *
 * @param completionHandler Callback that receives Result<Unit> indicating success or error
 * @deprecated This callback-based API will be deprecated in a future version.
 * Prefer the coroutine-based suspend activate() which returns CardOperationResult.
 */
@Deprecated(
    message = "This callback-based API will be deprecated in a future version. Use suspend activate() instead",
    replaceWith = ReplaceWith("activate()"),
    level = DeprecationLevel.WARNING,
)
public fun Card.activate(completionHandler: (Result<Unit>) -> Unit): Unit =
    stateManagementBridge(
        completionHandler = completionHandler,
        card = this,
    ) {
        activate()
    }

/**
 * Requests to suspend the card with optional reason.
 *
 * @param reason Optional reason for suspension
 * @param completionHandler Callback that receives Result<Unit> indicating success or error
 * @deprecated This callback-based API will be deprecated in a future version.
 * Prefer the coroutine-based suspend suspend() which returns CardOperationResult.
 */
@Deprecated(
    message = "This callback-based API will be deprecated in a future version. Use suspend suspend() instead",
    replaceWith = ReplaceWith("suspend(reason)"),
    level = DeprecationLevel.WARNING,
)
public fun Card.suspend(
    reason: CardSuspendReason?,
    completionHandler: (Result<Unit>) -> Unit,
): Unit =
    stateManagementBridge(
        completionHandler = completionHandler,
        card = this,
    ) {
        suspend(reason)
    }

/**
 * Requests to revoke the card with optional reason.
 *
 * @param reason Optional reason for revocation
 * @param completionHandler Callback that receives Result<Unit> indicating success or error
 * @deprecated This callback-based API will be deprecated in a future version.
 * Prefer the coroutine-based suspend revoke() which returns CardOperationResult.
 */
@Deprecated(
    message = "This callback-based API will be deprecated in a future version. Use suspend revoke() instead",
    replaceWith = ReplaceWith("revoke(reason)"),
    level = DeprecationLevel.WARNING,
)
public fun Card.revoke(
    reason: CardRevokeReason?,
    completionHandler: (Result<Unit>) -> Unit,
): Unit =
    stateManagementBridge(
        completionHandler = completionHandler,
        card = this,
    ) {
        revoke(reason)
    }

/**
 * Internal helper function that bridges between the new sealed result pattern and the old callback pattern.
 *
 * This function:
 * 1. Calls the modern suspend function that returns [CardOperationResult]
 * 2. Converts the sealed result to an exception using [getOrThrow]
 * 3. Wraps in a [Result] for the deprecated callback API
 *
 * @param T The type of data being retrieved
 * @param completionHandler Callback to receive the result
 * @param card The card instance on which the operation is performed
 * @param operation The suspend function that returns [CardOperationResult]
 */
private fun <T> stateManagementBridge(
    completionHandler: (Result<T>) -> Unit,
    card: Card,
    operation: suspend Card.() -> CardOperationResult<T>,
) {
    card.manager.coroutineScope.scope.launch {
        try {
            val result = card.operation()
            val data = result.getOrThrow()
            completionHandler(Result.success(data))
        } catch (e: Exception) {
            completionHandler(Result.failure(e))
        }
    }
}
