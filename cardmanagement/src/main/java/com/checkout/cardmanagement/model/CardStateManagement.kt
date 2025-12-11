package com.checkout.cardmanagement.model

import com.checkout.cardmanagement.logging.LogEvent
import com.checkout.cardmanagement.logging.LogEventSource
import com.checkout.cardmanagement.utils.toCardOperationError
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Activates the card.
 *
 * Transitions the card from its current state to ACTIVE. This operation requires
 * an active session and may fail if the current state doesn't allow activation.
 *
 * Example usage:
 * ```kotlin
 * when (val result = card.activate()) {
 *     is CardOperationResult.Success -> {
 *         showSuccess("Card activated successfully")
 *     }
 *     is CardOperationResult.Error.InvalidStateTransition -> {
 *         showError("Cannot activate from ${result.currentState}")
 *     }
 *     is CardOperationResult.Error.Unauthenticated -> {
 *         showError(result.message)
 *         redirectToLogin()
 *     }
 *     is CardOperationResult.Error -> showError(result.message)
 * }
 * ```
 *
 * @return Success (Unit), or specific error
 * @since 3.0.0
 */
public suspend fun Card.activate(): CardOperationResult<Unit> =
    performStateChange(
        targetState = CardState.ACTIVE,
        reason = null,
    ) { token ->
        manager.service.activateCard(token, id)
    }

/**
 * Suspends the card with optional reason.
 *
 * Transitions the card to SUSPENDED state. This operation requires an active session
 * and may fail if the current state doesn't allow suspension.
 *
 * Example usage:
 * ```kotlin
 * when (val result = card.suspend(CardSuspendReason.LOST)) {
 *     is CardOperationResult.Success -> {
 *         showSuccess("Card suspended")
 *     }
 *     is CardOperationResult.Error.InvalidStateTransition -> {
 *         showError("Cannot suspend from ${result.currentState}")
 *     }
 *     is CardOperationResult.Error -> showError(result.message)
 * }
 * ```
 *
 * @param reason Optional reason for suspension
 * @return Success (Unit), or specific error
 * @since 3.0.0
 */
public suspend fun Card.suspend(reason: CardSuspendReason? = null): CardOperationResult<Unit> =
    performStateChange(
        targetState = CardState.SUSPENDED,
        reason = reason?.value,
    ) { token ->
        manager.service.suspendCard(
            token,
            reason?.toCardNetworkSuspendReason(),
            id,
        )
    }

/**
 * Revokes the card with optional reason.
 *
 * Transitions the card to REVOKED state. This is typically a permanent action.
 * This operation requires an active session and may fail if the current state
 * doesn't allow revocation.
 *
 * Example usage:
 * ```kotlin
 * when (val result = card.revoke(CardRevokeReason.STOLEN)) {
 *     is CardOperationResult.Success -> {
 *         showSuccess("Card revoked")
 *     }
 *     is CardOperationResult.Error.InvalidStateTransition -> {
 *         showError("Cannot revoke from ${result.currentState}")
 *     }
 *     is CardOperationResult.Error -> showError(result.message)
 * }
 * ```
 *
 * @param reason Optional reason for revocation
 * @return Success (Unit), or specific error
 * @since 3.0.0
 */
public suspend fun Card.revoke(reason: CardRevokeReason? = null): CardOperationResult<Unit> =
    performStateChange(
        targetState = CardState.REVOKED,
        reason = reason?.value,
    ) { token ->
        manager.service.revokeCard(
            token,
            reason?.toCardNetworkRevokeReason(),
            id,
        )
    }

private suspend fun Card.performStateChange(
    targetState: CardState,
    reason: String?,
    operation: (String) -> kotlinx.coroutines.flow.Flow<Result<Unit>>,
): CardOperationResult<Unit> {
    if (!state.getPossibleStateChanges().contains(targetState)) {
        return CardOperationResult.Error.InvalidStateTransition(
            message = "Cannot transition from $state to $targetState",
            currentState = state,
            requestedState = targetState,
        )
    }

    val token = manager.sessionToken.value
    if (token == null) {
        return CardOperationResult.Error.Unauthenticated(
            message = "Session expired or not authenticated",
        )
    }

    val startTime = Calendar.getInstance()

    return try {
        operation(token)
            .catch { error ->
                manager.logger.log(
                    LogEvent.Failure(targetState.toLogEventSource(), error),
                    startTime,
                )
                throw error
            }.first()
            .fold(
                onSuccess = {
                    manager.logger.log(
                        LogEvent.StateManagement(
                            cardId = id,
                            originalState = state,
                            requestedState = targetState,
                            reason = reason,
                        ),
                        startTime,
                    )
                    CardOperationResult.Success(Unit)
                },
                onFailure = { error ->
                    manager.logger.log(
                        LogEvent.Failure(targetState.toLogEventSource(), error),
                        startTime,
                    )
                    error.toCardOperationError(
                        currentState = state,
                        requestedState = targetState,
                    )
                },
            )
    } catch (error: Throwable) {
        manager.logger.log(
            LogEvent.Failure(targetState.toLogEventSource(), error),
            startTime,
        )
        error.toCardOperationError(
            currentState = state,
            requestedState = targetState,
        )
    }
}

/**
 * Maps a CardState to its corresponding log event source identifier.
 */
private fun CardState.toLogEventSource(): String =
    when (this) {
        CardState.ACTIVE -> LogEventSource.ACTIVATE_CARD
        CardState.SUSPENDED -> LogEventSource.SUSPEND_CARD
        CardState.REVOKED -> LogEventSource.REVOKE_CARD
        else -> ""
    }
