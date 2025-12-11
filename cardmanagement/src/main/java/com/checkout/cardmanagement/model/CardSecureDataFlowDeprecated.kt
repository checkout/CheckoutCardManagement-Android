package com.checkout.cardmanagement.model

import androidx.compose.ui.platform.AbstractComposeView
import com.checkout.cardmanagement.utils.getOrThrow
import kotlinx.coroutines.launch

/**
 * Requests an AbstractComposeView containing pin number for the card.
 *
 * @param singleUseToken Single-use authentication token for this operation
 * @param completionHandler Callback that receives [SecureResult] with the PIN view or error
 * @deprecated This callback-based API will be deprecated in a future version.
 * Prefer the coroutine-based suspend getPin() which returns CardSecureDataResult.
 */
@Deprecated(
    message = "This callback-based API will be deprecated in a future version. Use suspend getPin() instead",
    replaceWith = ReplaceWith("getPin(singleUseToken)"),
    level = DeprecationLevel.WARNING,
)
public fun Card.getPin(
    singleUseToken: String,
    completionHandler: SecureResultCompletion,
): Unit =
    getSecureDataBridge(
        completionHandler = completionHandler,
        card = this,
    ) { isLegacy ->
        getPinImpl(singleUseToken, isLegacy)
    }

/**
 * Requests an AbstractComposeView containing long card number for the card.
 *
 * @param singleUseToken Single-use authentication token for this operation
 * @param completionHandler Callback that receives [SecureResult] with the PAN view or error
 * @deprecated This callback-based API will be deprecated in a future version.
 * Prefer the coroutine-based suspend getPan() which returns CardSecureDataResult.
 */
@Deprecated(
    message = "This callback-based API will be deprecated in a future version. Use suspend getPan() instead",
    replaceWith = ReplaceWith("getPan(singleUseToken)"),
    level = DeprecationLevel.WARNING,
)
public fun Card.getPan(
    singleUseToken: String,
    completionHandler: SecureResultCompletion,
): Unit =
    getSecureDataBridge(
        completionHandler = completionHandler,
        card = this,
    ) { isLegacy ->
        getPanImpl(singleUseToken, isLegacy)
    }

/**
 * Requests an AbstractComposeView containing security code for the card.
 *
 * @param singleUseToken Single-use authentication token for this operation
 * @param completionHandler Callback that receives [SecureResult] with the security code view or error
 * @deprecated This callback-based API will be deprecated in a future version.
 * Prefer the coroutine-based suspend getSecurityCode() which returns CardSecureDataResult.
 */
@Deprecated(
    message = "This callback-based API will be deprecated in a future version. Use suspend getSecurityCode() instead",
    replaceWith = ReplaceWith("getSecurityCode(singleUseToken)"),
    level = DeprecationLevel.WARNING,
)
public fun Card.getSecurityCode(
    singleUseToken: String,
    completionHandler: SecureResultCompletion,
): Unit =
    getSecureDataBridge(
        completionHandler = completionHandler,
        card = this,
    ) { isLegacy ->
        getSecurityCodeImpl(singleUseToken, isLegacy)
    }

/**
 * Requests a pair of AbstractComposeView containing PAN and security code for the card.
 *
 * @param singleUseToken Single-use authentication token for this operation
 * @param completionHandler Callback that receives [SecurePropertiesResult] with both views or error
 * @deprecated This callback-based API will be deprecated in a future version.
 * Prefer the coroutine-based suspend getPANAndSecurityCode() which returns CardSecureDataResult.
 */
@Deprecated(
    message =
        "This callback-based API will be deprecated in a future version." +
            "Use suspend getPANAndSecurityCode() instead",
    replaceWith = ReplaceWith("getPANAndSecurityCode(singleUseToken)"),
    level = DeprecationLevel.WARNING,
)
public fun Card.getPANAndSecurityCode(
    singleUseToken: String,
    completionHandler: SecurePropertiesResultCompletion,
): Unit =
    getSecureDataBridge(
        completionHandler = completionHandler,
        card = this,
    ) { isLegacy ->
        getPANAndSecurityCodeImpl(singleUseToken, isLegacy)
    }

/**
 * Copies PAN to clipboard with security features. The PAN will only be copyable if it has first been
 * viewed in the current session.
 *
 * @param singleUseToken Single-use authentication token for this operation
 * @param completionHandler Callback that receives Result<Unit> indicating success or error
 * @deprecated This callback-based API will be deprecated in a future version.
 * Prefer the coroutine-based suspend copyPan() which returns CardSecureDataResult.
 */
@Deprecated(
    message = "This callback-based API will be deprecated in a future version. Use suspend copyPan() instead",
    replaceWith = ReplaceWith("copyPan(singleUseToken)"),
    level = DeprecationLevel.WARNING,
)
public fun Card.copyPan(
    singleUseToken: String,
    completionHandler: (Result<Unit>) -> Unit,
): Unit =
    getSecureDataBridge(
        completionHandler = completionHandler,
        card = this,
    ) { isLegacy ->
        copyPanImpl(singleUseToken, isLegacy)
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
 * @param operation The internal suspend function that returns [CardSecureDataResult]
 */
private fun <T> getSecureDataBridge(
    completionHandler: (Result<T>) -> Unit,
    card: Card,
    operation: suspend Card.(Boolean) -> CardSecureDataResult<T>,
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

/**
 * Result type that on success delivers a [AbstractComposeView] to be presented to the user,
 * and on failure delivers an error to identify the problem.
 *
 * Used by deprecated callback-based APIs for returning single secure views (PIN, PAN, or CVV).
 */
public typealias SecureResult = Result<AbstractComposeView>

/**
 * Completion handler callback type for deprecated APIs that return a single secure view.
 *
 * Receives a [SecureResult] containing either:
 * - Success: An [AbstractComposeView] ready to be displayed
 * - Failure: A [CardManagementError] describing what went wrong
 */
public typealias SecureResultCompletion = (SecureResult) -> Unit

/**
 * Result type that on success delivers a pair of [AbstractComposeView] for PAN and SecurityCode,
 * and on failure delivers an error to identify the problem.
 *
 * Used by deprecated callback-based APIs for returning multiple secure views simultaneously.
 */
public typealias SecurePropertiesResult = Result<Pair<AbstractComposeView, AbstractComposeView>>

/**
 * Completion handler callback type for deprecated APIs that return multiple secure views.
 *
 * Receives a [SecurePropertiesResult] containing either:
 * - Success: A Pair of [AbstractComposeView] (first is PAN, second is security code)
 * - Failure: A [CardManagementError] describing what went wrong
 */
public typealias SecurePropertiesResultCompletion = (SecurePropertiesResult) -> Unit
