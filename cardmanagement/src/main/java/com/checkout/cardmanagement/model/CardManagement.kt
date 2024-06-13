package com.checkout.cardmanagement.model

import android.app.Activity
import android.content.Intent
import com.checkout.cardmanagement.logging.LogEvent
import com.checkout.cardmanagement.logging.LogEventSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * Request to add the provided card ID to the Google Wallet present on device
 *
 * @param activity the Activity for receiving callback
 * @param cardholderId Cardholder Identifier
 * @param configuration Provisioning Configuration shared during Onboarding
 * @param token Push Provisioning token formatted for the request
 * @param completionHandler Completion Handler returning the outcome of the provisioning operation
 */
public fun Card.provision(
    activity: Activity,
    cardholderId: String,
    configuration: ProvisioningConfiguration,
    token: String,
    completionHandler: (Result<Unit>) -> Unit,
) {
    runBlocking {
        launch {
            val startTime = Calendar.getInstance()
            manager.service.addCardToGoogleWallet(
                activity = activity,
                cardId = id,
                cardholderId = cardholderId,
                configuration = configuration.toNetworkConfig(),
                token = token,
            ) { result ->
                result.onSuccess {
                    manager.logger.log(
                        startedAt = startTime,
                        event = LogEvent.PushProvisioning(
                            partIdentifier,
                            cardholderId.takeLast(Card.PARTIAL_ID_DIGITS),
                        ),
                    )
                    completionHandler(result)
                }.onFailure {
                    manager.logger.log(
                        LogEvent.Failure(LogEventSource.PUSH_PROVISIONING, it),
                        startTime,
                    )
                    completionHandler(Result.failure(it.toCardManagementError()))
                }
            }
        }
    }
}

/**
 *  Request to activate the card
 *
 * @param completionHandler Completion Handler returning the outcome of the activate operation
 */
public fun Card.activate(completionHandler: (Result<Unit>) -> Unit) {
    performCardManagementOperation(
        CardState.ACTIVE,
        completionHandler,
    ) {
        manager.service.activateCard(manager.sessionToken!!, id)
    }
}

/**
 * Request to revoke the card, with option to provide a reason for change
 *
 * @param reason Optional reason for the suspend operation
 * @param completionHandler Completion Handler returning the outcome of the suspend operation
 */
public fun Card.suspend(reason: CardSuspendReason?, completionHandler: (Result<Unit>) -> Unit) {
    performCardManagementOperation(
        CardState.SUSPENDED,
        completionHandler,
        reason?.value,
    ) {
        manager.service.suspendCard(
            manager.sessionToken!!,
            reason?.toCardNetworkSuspendReason(),
            id,
        )
    }
}

/**
 * Request to revoke the card, with option to provide a reason for change
 *
 * @param reason Optional reason for the revoke operation
 * @param completionHandler Completion Handler returning the outcome of the revoke operation
 */
public fun Card.revoke(reason: CardRevokeReason?, completionHandler: (Result<Unit>) -> Unit) {
    performCardManagementOperation(
        CardState.REVOKED,
        completionHandler,
        reason?.value,
    ) {
        manager.service.revokeCard(manager.sessionToken!!, reason?.toCardNetworkRevokeReason(), id)
    }
}

private fun Card.performCardManagementOperation(
    targetCardState: CardState,
    completionHandler: (Result<Unit>) -> Unit,
    reason: String? = null,
    operation: () -> Flow<Result<Unit>>,
) {
    when {
        !state.getPossibleStateChanges().contains(targetCardState) ->
            completionHandler(Result.failure(CardManagementError.InvalidStateRequested))
        manager.sessionToken == null ->
            completionHandler(Result.failure(CardManagementError.Unauthenticated))
        else -> {
            runBlocking {
                launch {
                    val startTime = Calendar.getInstance()
                    operation().collect { result ->
                        result.onSuccess {
                            manager.logger.log(
                                LogEvent.StateManagement(
                                    idLast4 = partIdentifier,
                                    originalState = state,
                                    requestedState = targetCardState,
                                    reason = reason,
                                ),
                                startTime,
                            )
                            completionHandler(Result.success(Unit))
                        }.onFailure { error ->
                            manager.logger.log(
                                LogEvent.Failure(targetCardState.toLogEventSource(), error),
                                startTime,
                            )
                            completionHandler(Result.failure(error.toCardManagementError()))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Handle the [Card.provision] operation result in the passed [Activity], override
 * [Activity.onActivityResult] to pass the content back to SDK for further processing.
 */
public fun Card.handleCardResult(requestCode: Int, resultCode: Int, data: Intent?) {
    manager.service.handleCardResult(requestCode, resultCode, data)
}

private fun CardState.toLogEventSource(): String = when (this) {
    CardState.ACTIVE -> LogEventSource.ACTIVATE_CARD
    CardState.SUSPENDED -> LogEventSource.SUSPEND_CARD
    CardState.REVOKED -> LogEventSource.REVOKE_CARD
    else -> ""
}
