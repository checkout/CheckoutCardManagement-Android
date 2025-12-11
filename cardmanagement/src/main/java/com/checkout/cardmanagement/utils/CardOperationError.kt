package com.checkout.cardmanagement.utils

import com.checkout.cardmanagement.model.CardManagementError
import com.checkout.cardmanagement.model.CardOperationResult
import com.checkout.cardmanagement.model.CardState
import com.checkout.cardmanagement.model.toCardManagementError

/**
 * Converts a Throwable to a CardOperationResult.Error with preserved downstream messages.
 */
internal fun Throwable.toCardOperationError(
    currentState: CardState? = null,
    requestedState: CardState? = null,
): CardOperationResult.Error =
    when (val error = this.toCardManagementError()) {
        CardManagementError.AuthenticationFailure ->
            CardOperationResult.Error.AuthenticationFailure(
                message = error.message ?: "Authentication failed",
                tokenType = "push provisioning token",
            )

        CardManagementError.Unauthenticated ->
            CardOperationResult.Error.Unauthenticated(
                message = error.message ?: "Session expired or not authenticated",
            )

        CardManagementError.ConnectionIssue ->
            CardOperationResult.Error.ConnectionIssue(
                message = this.message ?: error.message ?: "Network error occurred",
                cause = this,
            )

        is CardManagementError.ConfigurationIssue ->
            CardOperationResult.Error.ConfigurationFailure(
                message = error.message ?: "Configuration error",
                hint = error.hint,
            )

        CardManagementError.InvalidStateRequested ->
            CardOperationResult.Error.InvalidStateTransition(
                message = error.message ?: "Invalid state transition requested",
                currentState = currentState ?: CardState.INACTIVE,
                requestedState = requestedState ?: CardState.INACTIVE,
            )

        is CardManagementError.PushProvisioningFailure ->
            when (error.type) {
                CardManagementError.PushProvisioningFailureType.CANCELLED ->
                    CardOperationResult.Error.OperationCancelled(
                        message = error.message,
                    )
                CardManagementError.PushProvisioningFailureType.CONFIGURATION_FAILURE ->
                    CardOperationResult.Error.ConfigurationFailure(
                        message = error.message,
                        hint = "Verify provisioning configuration",
                    )
                CardManagementError.PushProvisioningFailureType.ERROR_DEVICE_ENVIRONMENT_UNSAFE -> {
                    CardOperationResult.Error.DeviceEnvironmentUnsafe(
                        message = error.message,
                    )
                }
                CardManagementError.PushProvisioningFailureType.ERROR_GPAY_NOT_SUPPORTED -> {
                    CardOperationResult.Error.GooglePayUnsupported(
                        message = error.message,
                    )
                }
                CardManagementError.PushProvisioningFailureType.ERROR_DEBUG_SDK_USED -> {
                    CardOperationResult.Error.GooglePayUnsupported(
                        message = error.message,
                    )
                }
                CardManagementError.PushProvisioningFailureType.ERROR_CARD_NOT_FOUND -> {
                    CardOperationResult.Error.CardNotFound(
                        message = error.message,
                    )
                }
                CardManagementError.PushProvisioningFailureType.ERROR_NOT_LOGGED_IN -> {
                    CardOperationResult.Error.AuthenticationFailure(
                        message = error.message,
                        tokenType = "Provisioning Token",
                    )
                }
                else ->
                    CardOperationResult.Error.OperationFailure(
                        message = error.message,
                        cause = this,
                    )
            }

        is CardManagementError.FetchDigitizationStateFailure ->
            CardOperationResult.Error.DigitizationStateFailure(
                message = error.message,
                cause = this,
            )

        else ->
            CardOperationResult.Error.OperationFailure(
                message = this.message ?: error.message ?: "Operation failed",
                cause = this,
            )
    }

/**
 * Converts CardOperationResult to throwing Result (for deprecated bridge).
 */
internal fun <T> CardOperationResult<T>.getOrThrow(): T =
    when (this) {
        is CardOperationResult.Success -> data
        is CardOperationResult.Error.AuthenticationFailure -> throw CardManagementError.AuthenticationFailure
        is CardOperationResult.Error.Unauthenticated -> throw CardManagementError.Unauthenticated
        is CardOperationResult.Error.ConnectionIssue -> throw cause ?: CardManagementError.ConnectionIssue
        is CardOperationResult.Error.ConfigurationFailure -> throw CardManagementError.ConfigurationIssue(hint)
        is CardOperationResult.Error.OperationCancelled ->
            throw CardManagementError.PushProvisioningFailure(
                CardManagementError.PushProvisioningFailureType.CANCELLED,
            )
        is CardOperationResult.Error.DigitizationStateFailure ->
            throw CardManagementError.FetchDigitizationStateFailure(
                CardManagementError.DigitizationStateFailureType.OPERATION_FAILURE,
            )
        is CardOperationResult.Error.InvalidStateTransition -> throw CardManagementError.InvalidStateRequested
        is CardOperationResult.Error.OperationFailure -> throw cause ?: Exception(message)
        is CardOperationResult.Error.CardNotFound -> throw CardManagementError.PushProvisioningFailure(
            CardManagementError.PushProvisioningFailureType.ERROR_CARD_NOT_FOUND,
        )
        is CardOperationResult.Error.DebugSDKUsed -> throw CardManagementError.PushProvisioningFailure(
            CardManagementError.PushProvisioningFailureType.ERROR_DEBUG_SDK_USED,
        )
        is CardOperationResult.Error.DeviceEnvironmentUnsafe -> throw CardManagementError.PushProvisioningFailure(
            CardManagementError.PushProvisioningFailureType.ERROR_DEVICE_ENVIRONMENT_UNSAFE,
        )
        is CardOperationResult.Error.GooglePayUnsupported -> throw CardManagementError.PushProvisioningFailure(
            CardManagementError.PushProvisioningFailureType.ERROR_GPAY_NOT_SUPPORTED,
        )
    }
