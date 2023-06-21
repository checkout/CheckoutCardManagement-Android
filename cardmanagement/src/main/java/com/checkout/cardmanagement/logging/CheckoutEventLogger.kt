package com.checkout.cardmanagement.logging

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.checkout.cardmanagement.logging.LogEventUtils.Companion.LOGGER_PRODUCTION_ID
import com.checkout.cardnetwork.common.NetworkLogger
import com.checkout.cardnetwork.common.model.CardServiceVersion
import com.checkout.cardnetwork.common.model.Environment
import com.checkout.eventlogger.METADATA_CORRELATION_ID
import com.checkout.eventlogger.domain.model.MonitoringLevel
import com.checkout.eventlogger.domain.model.RemoteProcessorMetadata
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import com.checkout.eventlogger.CheckoutEventLogger as EventLogger
import com.checkout.eventlogger.Environment as LoggerEnvironment

internal class CheckoutEventLogger @VisibleForTesting internal constructor(
	private val logEventUtils: LogEventUtils,
	private val logger: EventLogger
) : NetworkLogger {

	internal constructor() : this(
		LogEventUtils(),
		EventLogger(com.checkout.cardmanagement.BuildConfig.PRODUCT_NAME).apply {
			if (com.checkout.cardmanagement.BuildConfig.DEFAULT_LOGCAT_MONITORING_ENABLED) {
				enableLocalProcessor(MonitoringLevel.DEBUG)
			}
		}
	)

	override val sessionID: String by lazy { UUID.randomUUID().toString() }

	/**
	 * Enable logger to dispatch events
	 */
	fun initialise(
		context: Context,
		environment: Environment,
		serviceVersion: CardServiceVersion
	) {
		logger.addMetadata(METADATA_CORRELATION_ID, sessionID)

		logger.enableRemoteProcessor(
			environment.toLoggingEnvironment(),
			RemoteProcessorMetadata.from(
				context = context,
				environment = environment.name.lowercase(Locale.ENGLISH),
				productIdentifier = LOGGER_PRODUCTION_ID,
				productVersion = "${serviceVersion.name}-${serviceVersion.number}"
			)
		)
	}

	/**
	 * Convenience method wrapping formatting from project specific Event format to generic SDK expectation
	 */
	fun log(event: LogEvent, startedAt: Calendar? = null) {
		logger.logEvent(logEventUtils.buildEvent(event, startedAt))
	}

	/**
	 * Network Logger conformance, enabling to collect network level details if error is encountered
	 */
	override fun log(error: Throwable, additionalInfo: Map<String, String>) {
		logger.logEvent(
			logEventUtils.buildEvent(
				event = LogEvent.Failure(
					source = additionalInfo[LogEventUtils.KEY_SOURCE] ?: "",
					error = error
				),
				startedAt = null,
				extraProperties = additionalInfo
			)
		)
	}

	private companion object {
		private fun Environment.toLoggingEnvironment(): LoggerEnvironment = when (this) {
			Environment.SANDBOX -> LoggerEnvironment.SANDBOX
			Environment.PRODUCTION -> LoggerEnvironment.PRODUCTION
		}
	}
}
