package com.checkout.cardmanagement.model

import androidx.compose.ui.platform.AbstractComposeView
import com.checkout.cardmanagement.logging.CheckoutEventLogger
import com.checkout.cardmanagement.logging.LogEvent
import com.checkout.cardmanagement.logging.LogEventSource.GET_CVV
import com.checkout.cardmanagement.logging.LogEventSource.GET_PAN
import com.checkout.cardmanagement.logging.LogEventSource.GET_PAN_AND_CVV
import com.checkout.cardmanagement.logging.LogEventSource.GET_PIN
import com.checkout.cardnetwork.CheckoutCardService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * Request a AbstractComposeView containing pin number for the card
 */
public fun Card.getPin(
	singleUseToken: String,
	completionHandler: SecureResultCompletion
): Unit = getSecureData(
	logger = manager.logger,
	successLogEvent = LogEvent.GetPin(partIdentifier, state),
	logEventSource = GET_PIN,
	completionHandler = completionHandler,
	displaySecureData = {
		manager.service.displayPin(
			cardId = id,
			singleUseToken = singleUseToken,
			pinViewConfiguration = manager.designSystem.pinViewConfig
		)
	}
)

/**
 * Request a AbstractComposeView containing long card number for the card
 */
public fun Card.getPan(
	singleUseToken: String,
	completionHandler: SecureResultCompletion
): Unit = getSecureData(
	logger = manager.logger,
	successLogEvent = LogEvent.GetPan(partIdentifier, state),
	logEventSource = GET_PAN,
	completionHandler = completionHandler,
	displaySecureData = {
		manager.service.displayPan(
			cardId = id,
			singleUseToken = singleUseToken,
			panTextViewConfiguration = manager.designSystem.panViewConfig
		)
	}
)

/**
 * Request a AbstractComposeView containing security code for the card
 */
public fun Card.getSecurityCode(
	singleUseToken: String,
	completionHandler: SecureResultCompletion
): Unit = getSecureData(
	logger = manager.logger,
	successLogEvent = LogEvent.GetCVV(partIdentifier, state),
	logEventSource = GET_CVV,
	completionHandler = completionHandler,
	displaySecureData = {
		manager.service.displaySecurityCode(
			cardId = id,
			singleUseToken = singleUseToken,
			securityCodeViewConfiguration = manager.designSystem.securityCodeViewConfig
		)
	}
)

/**
 * Request a pair of AbstractComposeView containing PAN and security code for the card
 */
public fun Card.getPANAndSecurityCode(
	singleUseToken: String,
	completionHandler: SecurePropertiesResultCompletion
): Unit = getSecureData(
	logger = manager.logger,
	successLogEvent = LogEvent.GetPanCVV(partIdentifier, state),
	logEventSource = GET_PAN_AND_CVV,
	completionHandler = completionHandler,
	displaySecureData = {
		manager.service.displayPANAndSecurityCode(
			cardId = id,
			singleUseToken = singleUseToken,
			panTextViewConfiguration = manager.designSystem.panViewConfig,
			securityCodeViewConfiguration = manager.designSystem.securityCodeViewConfig
		)
	}
)

/**
 * @param T type of secure data view, should be a single or pair of [AbstractComposeView]
 * @param logger event logger
 * @param logEventSource source of the log event
 * @param successLogEvent event to be logged when the request is successfully done
 * @param displaySecureData call [CheckoutCardService] to display the secure data
 * @param completionHandler to handle the outcome of the display request
 */
private fun <T> getSecureData(
	logger: CheckoutEventLogger,
	logEventSource: String,
	successLogEvent: LogEvent,
	displaySecureData: () -> Flow<Result<T>>,
	completionHandler: (Result<T>) -> Unit
) {
	runBlocking {
		launch {
			val startTime = Calendar.getInstance()
			displaySecureData()
				.catch { error ->
					logger.log(LogEvent.Failure(logEventSource, error), startTime)
					completionHandler(Result.failure(error.toCardManagementError()))
				}
				.collect { result ->
					result.onSuccess {
						logger.log(successLogEvent, startTime)
						completionHandler(result)
					}.onFailure { error ->
						logger.log(LogEvent.Failure(logEventSource, error), startTime)
						completionHandler(Result.failure(error.toCardManagementError()))
					}
				}
		}
	}
}

/**
 * Result type that on success delivers a [AbstractComposeView] to be presented to the user, and
 * on failure delivers an error to identify problem
 */
public typealias SecureResult = Result<AbstractComposeView>

/**
 * Completion handler returning a [SecureResult]
 */
public typealias SecureResultCompletion = (SecureResult) -> Unit

/**
 * Result type that on success delivers a pair of [AbstractComposeView] for PAN & SecurityCode,
 * and on failure delivers an error to identify problem
 */
public typealias SecurePropertiesResult = Result<Pair<AbstractComposeView, AbstractComposeView>>

/**
 * Completion handler returning a [SecurePropertiesResult]
 */
public typealias SecurePropertiesResultCompletion = (SecurePropertiesResult) -> Unit
