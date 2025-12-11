package com.checkout.cardmanagement.model

import android.os.Build
import androidx.compose.ui.platform.AbstractComposeView
import com.checkout.cardmanagement.logging.CheckoutEventLogger
import com.checkout.cardmanagement.logging.LogEvent
import com.checkout.cardmanagement.logging.LogEventSource.COPY_PAN
import com.checkout.cardmanagement.logging.LogEventSource.GET_CVV
import com.checkout.cardmanagement.logging.LogEventSource.GET_PAN
import com.checkout.cardmanagement.logging.LogEventSource.GET_PAN_AND_CVV
import com.checkout.cardmanagement.logging.LogEventSource.GET_PIN
import com.checkout.cardmanagement.logging.LogEventUtils.Companion.KEY_LEGACY_REQUEST
import com.checkout.cardmanagement.utils.toCardSecureDataError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Validates that the current Android API version supports the PAN copy operation.
 *
 * Copy to clipboard is not supported on Android API versions 29-32 (Android 10-12L)
 * due to platform security restrictions.
 *
 * @return [CardSecureDataResult.Error.UnsupportedApiVersion] if unsupported, null if supported
 */
private fun validateApiVersionForCopyPan(): CardSecureDataResult.Error.UnsupportedApiVersion? {
    val currentAPIVersion = Build.VERSION.SDK_INT
    return if (currentAPIVersion >= Build.VERSION_CODES.Q && currentAPIVersion <= Build.VERSION_CODES.S_V2) {
        CardSecureDataResult.Error.UnsupportedApiVersion(
            version = currentAPIVersion,
            message =
                "Copying PAN is not supported on Android API $currentAPIVersion (Android 10-12L). " +
                    "This feature is available on API 28 and below, or API 33 and above due to platform security restrictions.",
        )
    } else {
        null
    }
}

/**
 * Retrieves the card PIN as a secure view for display.
 *
 * Returns a Compose view that securely displays the PIN without exposing it to application code.
 *
 * Example usage:
 * ```kotlin
 * when (val result = card.getPin(singleUseToken)) {
 *     is CardSecureDataResult.Success -> {
 *         displaySecureView(result.data)
 *     }
 *     is CardSecureDataResult.Error -> {
 *         showError(result.message)
 *         handleError(result)
 *     }
 * }
 * ```
 *
 * @param singleUseToken Single-use authentication token for this operation
 * @return Success with secure PIN view, or specific error
 * @since 3.0.0
 */
public suspend fun Card.getPin(singleUseToken: String): CardSecureDataResult<AbstractComposeView> =
    getPinImpl(singleUseToken, isLegacyRequest = false)

/**
 * Internal implementation of [getPin] that accepts a legacy request flag for analytics.
 *
 * @param singleUseToken Single-use authentication token for this operation
 * @param isLegacyRequest True if called from deprecated callback API, false if from suspend API
 * @return Success with secure PIN view, or specific error
 */
internal suspend fun Card.getPinImpl(
    singleUseToken: String,
    isLegacyRequest: Boolean,
): CardSecureDataResult<AbstractComposeView> =
    getSecureDataSuspend(
        logger = manager.logger,
        successLogEvent = LogEvent.GetPin(cardId = id, state),
        logEventSource = GET_PIN,
        isLegacyRequest = isLegacyRequest,
        displaySecureData = {
            manager.service.displayPin(
                cardId = id,
                singleUseToken = singleUseToken,
                pinViewConfiguration = manager.designSystem.pinViewConfig,
            )
        },
    )

/**
 * Retrieves the card PAN (Primary Account Number) as a secure view for display.
 *
 * Returns a Compose view that securely displays the full card number without exposing it
 * to application code.
 *
 * Example usage:
 * ```kotlin
 * when (val result = card.getPan(singleUseToken)) {
 *     is CardSecureDataResult.Success -> {
 *         displaySecureView(result.data)
 *     }
 *     is CardSecureDataResult.Error -> {
 *         showError(result.message)
 *         handleError(result)
 *     }
 * }
 * ```
 *
 * @param singleUseToken Single-use authentication token for this operation
 * @return Success with secure PAN view, or specific error
 * @since 3.0.0
 */
public suspend fun Card.getPan(singleUseToken: String): CardSecureDataResult<AbstractComposeView> =
    getPanImpl(singleUseToken, isLegacyRequest = false)

/**
 * Internal implementation of [getPan] that accepts a legacy request flag for analytics.
 *
 * @param singleUseToken Single-use authentication token for this operation
 * @param isLegacyRequest True if called from deprecated callback API, false if from suspend API
 * @return Success with secure PAN view, or specific error
 */
internal suspend fun Card.getPanImpl(
    singleUseToken: String,
    isLegacyRequest: Boolean,
): CardSecureDataResult<AbstractComposeView> =
    getSecureDataSuspend(
        logger = manager.logger,
        successLogEvent = LogEvent.GetPan(cardId = id, state),
        logEventSource = GET_PAN,
        isLegacyRequest = isLegacyRequest,
        displaySecureData = {
            manager.service.displayPan(
                cardId = id,
                singleUseToken = singleUseToken,
                panTextViewConfiguration = manager.designSystem.panViewConfig,
            )
        },
    )

/**
 * Retrieves the card security code (CVV/CVC) as a secure view for display.
 *
 * Returns a Compose view that securely displays the security code without exposing it
 * to application code.
 *
 * Example usage:
 * ```kotlin
 * when (val result = card.getSecurityCode(singleUseToken)) {
 *     is CardSecureDataResult.Success -> {
 *         displaySecureView(result.data)
 *     }
 *     is CardSecureDataResult.Error -> {
 *         showError(result.message)
 *         handleError(result)
 *     }
 * }
 * ```
 *
 * @param singleUseToken Single-use authentication token for this operation
 * @return Success with secure CVV view, or specific error
 * @since 3.0.0
 */
public suspend fun Card.getSecurityCode(singleUseToken: String): CardSecureDataResult<AbstractComposeView> =
    getSecurityCodeImpl(singleUseToken, isLegacyRequest = false)

/**
 * Internal implementation of [getSecurityCode] that accepts a legacy request flag for analytics.
 *
 * @param singleUseToken Single-use authentication token for this operation
 * @param isLegacyRequest True if called from deprecated callback API, false if from suspend API
 * @return Success with secure CVV view, or specific error
 */
internal suspend fun Card.getSecurityCodeImpl(
    singleUseToken: String,
    isLegacyRequest: Boolean,
): CardSecureDataResult<AbstractComposeView> =
    getSecureDataSuspend(
        logger = manager.logger,
        successLogEvent = LogEvent.GetCVV(cardId = id, state),
        logEventSource = GET_CVV,
        isLegacyRequest = isLegacyRequest,
        displaySecureData = {
            manager.service.displaySecurityCode(
                cardId = id,
                singleUseToken = singleUseToken,
                securityCodeViewConfiguration = manager.designSystem.securityCodeViewConfig,
            )
        },
    )

/**
 * Retrieves both the PAN and security code as secure views for display.
 *
 * This suspend function returns a pair of Compose views that securely display both the
 * full card number and security code without exposing them to the application code.
 * Both views are designed to prevent screenshots and screen recording on supported devices.
 *
 * Example usage:
 * ```kotlin
 * when (val result = card.getPANAndSecurityCode(token)) {
 *     is CardSecureDataResult.Success -> {
 *         val (panView, cvvView) = result.data
 *         // Display panView in your PAN container
 *         // Display cvvView in your CVV container
 *     }
 *     is CardSecureDataResult.Error -> {
 *         showError(result.message)
 *     }
 * }
 * ```
 *
 * @param singleUseToken Single-use authentication token for this operation
 * @return [CardSecureDataResult] containing either a Pair of views (PAN, CVV) or an error
 * @since 3.0.0
 */
public suspend fun Card.getPANAndSecurityCode(
    singleUseToken: String,
): CardSecureDataResult<Pair<AbstractComposeView, AbstractComposeView>> =
    getPANAndSecurityCodeImpl(singleUseToken, isLegacyRequest = false)

/**
 * Internal implementation of [getPANAndSecurityCode] that accepts a legacy request flag for analytics.
 *
 * @param singleUseToken Single-use authentication token for this operation
 * @param isLegacyRequest True if called from deprecated callback API, false if from suspend API
 * @return [CardSecureDataResult] containing either a Pair of views (PAN, CVV) or an error
 */
internal suspend fun Card.getPANAndSecurityCodeImpl(
    singleUseToken: String,
    isLegacyRequest: Boolean,
): CardSecureDataResult<Pair<AbstractComposeView, AbstractComposeView>> =
    getSecureDataSuspend(
        logger = manager.logger,
        successLogEvent = LogEvent.GetPanCVV(cardId = id, state),
        logEventSource = GET_PAN_AND_CVV,
        isLegacyRequest = isLegacyRequest,
        displaySecureData = {
            manager.service.displayPANAndSecurityCode(
                cardId = id,
                singleUseToken = singleUseToken,
                panTextViewConfiguration = manager.designSystem.panViewConfig,
                securityCodeViewConfiguration = manager.designSystem.securityCodeViewConfig,
            )
        },
    )

/**
 * Copies the PAN to the device clipboard with security features.
 *
 * The PAN must be viewed in the current session before copying (call [getPan] or
 * [getPANAndSecurityCode] first). Not supported on Android API 29-32 (Android 10-12L).
 *
 * Example usage:
 * ```kotlin
 * when (val result = card.copyPan(singleUseToken)) {
 *     is CardSecureDataResult.Success -> {
 *         showToast("Card number copied to clipboard")
 *     }
 *     is CardSecureDataResult.Error.PanNotViewed -> {
 *         showError(result.message)
 *         promptToViewPanFirst()
 *     }
 *     is CardSecureDataResult.Error.UnsupportedApiVersion -> {
 *         showError(result.message)
 *         disableCopyFeature()
 *     }
 *     is CardSecureDataResult.Error -> {
 *         showError(result.message)
 *     }
 * }
 * ```
 *
 * @param singleUseToken Single-use authentication token for this operation
 * @return Success (Unit), or specific error (PanNotViewed, UnsupportedApiVersion, etc.)
 * @since 3.0.0
 */
public suspend fun Card.copyPan(singleUseToken: String): CardSecureDataResult<Unit> =
    copyPanImpl(singleUseToken, isLegacyRequest = false)

/**
 * Internal implementation of [copyPan] that accepts a legacy request flag for analytics.
 *
 * @param singleUseToken Single-use authentication token for this operation
 * @param isLegacyRequest True if called from deprecated callback API, false if from suspend API
 * @return Success (Unit), or specific error (PanNotViewed, UnsupportedApiVersion, etc.)
 */
internal suspend fun Card.copyPanImpl(
    singleUseToken: String,
    isLegacyRequest: Boolean,
): CardSecureDataResult<Unit> {
    validateApiVersionForCopyPan()?.let { return it }

    return getSecureDataSuspend(
        logger = manager.logger,
        successLogEvent = LogEvent.CopyPan(cardId = id, state),
        logEventSource = COPY_PAN,
        isLegacyRequest = isLegacyRequest,
        displaySecureData = {
            manager.service.copyPan(
                cardId = id,
                singleUseToken = singleUseToken,
            )
        },
    )
}

internal suspend fun <T> getSecureDataSuspend(
    logger: CheckoutEventLogger,
    logEventSource: String,
    successLogEvent: LogEvent,
    isLegacyRequest: Boolean,
    displaySecureData: () -> Flow<Result<T>>,
): CardSecureDataResult<T> =
    withContext(Dispatchers.IO) {
        val startTime = Calendar.getInstance()
        try {
            displaySecureData()
                .catch { error ->
                    logger.log(LogEvent.Failure(logEventSource, error), startTime)
                    throw error
                }.first()
                .fold(
                    onSuccess = { data ->
                        logger.log(
                            successLogEvent,
                            startTime,
                            additionalInfo =
                                mapOf(
                                    KEY_LEGACY_REQUEST to isLegacyRequest.toString(),
                                ),
                        )
                        CardSecureDataResult.Success(data)
                    },
                    onFailure = { error ->
                        logger.log(
                            event = LogEvent.Failure(logEventSource, error),
                            startedAt = startTime,
                            additionalInfo =
                                mapOf(
                                    KEY_LEGACY_REQUEST to isLegacyRequest.toString(),
                                ),
                        )
                        error.toCardSecureDataError()
                    },
                )
        } catch (error: Throwable) {
            logger.log(LogEvent.Failure(logEventSource, error), startTime)
            error.toCardSecureDataError()
        }
    }
