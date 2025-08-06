package com.checkout.cardmanagement.model

import com.checkout.cardmanagement.model.CardManagementError.FetchDigitizationStateFailure
import com.checkout.cardmanagement.model.CardManagementError.PushProvisioningFailure
import com.checkout.cardnetwork.common.model.CardNetworkError
import com.checkout.cardnetwork.common.model.CardNetworkError.DigitizationStateFailureType
import com.checkout.cardnetwork.common.model.CardNetworkError.PushProvisioningFailureType

/** Errors encountered in the running of the management services */
public sealed class CardManagementError : Exception() {
    /** The authentication of the session has failed. Functionality will not be available until a successful authentication takes place */
    public object AuthenticationFailure : CardManagementError()

    /** The session is not authenticated. Provide a session token via `logInSession` and retry. */
    public object Unauthenticated : CardManagementError()

    /** A configuration seems to not be correct. Please review configuration of SDK and any other configuration leading to the call completion
     * Note: Use [hint] for advice on recovering and retrying. */
    public data class ConfigurationIssue(
        val hint: String,
    ) : CardManagementError()

    /**  There may be an issue with network conditions on device */
    public object ConnectionIssue : CardManagementError()

    /** There was a problem that prevented securely retrieving information */
    public object UnableToPerformSecureOperation : CardManagementError()

    /** Error when a pan is attempted to be copied without being viewed */
    public object PanNotViewedFailure : CardManagementError()

    /** Requested to change card to an unavailable state */
    public object InvalidStateRequested : CardManagementError()

    public data class UnsupportedAPIVersion(
        val currentVersion: Int,
    ) : CardManagementError() {
        override val message: String =
            "Unsupported Android version: $currentVersion. Supported API versions are 28 and lower and 33 and newer due to safety concerns"
    }

    /** Failed to complete Push Provisioning request */
    public data class PushProvisioningFailure(
        val type: PushProvisioningFailureType,
    ) : CardManagementError() {
        override val message: String = type.name
    }

    /** Failed to complete fetch Digitization state request */
    public data class FetchDigitizationStateFailure(
        val type: DigitizationStateFailureType,
    ) : CardManagementError() {
        override val message: String = type.name
    }

    /**  Types of the failure for Push Provisioning request */
    public enum class PushProvisioningFailureType {
        /** Push Provisioning operation is interrupted and cancelled */
        CANCELLED,

        /** Unable to configured properly for Push Provisioning */
        CONFIGURATION_FAILURE,

        /** Failed to perform Push Provisioning operation */
        OPERATION_FAILURE,
    }

    /** Types of the failure for Push Provisioning request */
    public enum class DigitizationStateFailureType {
        /** Unable to configure properly for fetching the Digitization state */
        CONFIGURATION_FAILURE,

        /** Failed to perform fetch digitization state operation */
        OPERATION_FAILURE,
    }
}

internal fun Throwable.toCardManagementError(): CardManagementError =
    @Suppress("ktlint:standard:if-else-wrapping")
    if (this is CardNetworkError) {
        when (this) {
            CardNetworkError.AuthenticationFailure -> CardManagementError.AuthenticationFailure
            is CardNetworkError.InvalidRequest -> CardManagementError.ConfigurationIssue(hint)
            is CardNetworkError.Misconfigured -> CardManagementError.ConfigurationIssue(hint)
            CardNetworkError.ParsingFailure, CardNetworkError.ServerIssue -> CardManagementError.ConnectionIssue
            CardNetworkError.Unauthenticated -> CardManagementError.Unauthenticated
            CardNetworkError.SecureOperationsFailure -> CardManagementError.UnableToPerformSecureOperation
            CardNetworkError.PanNotViewedFailure -> CardManagementError.PanNotViewedFailure
            is CardNetworkError.PushProvisioningFailure ->
                when (this.type) {
                    PushProvisioningFailureType.CANCELLED ->
                        PushProvisioningFailure(CardManagementError.PushProvisioningFailureType.CANCELLED)

                    PushProvisioningFailureType.CONFIGURATION_FAILURE ->
                        PushProvisioningFailure(CardManagementError.PushProvisioningFailureType.CONFIGURATION_FAILURE)

                    PushProvisioningFailureType.OPERATION_FAILURE ->
                        PushProvisioningFailure(CardManagementError.PushProvisioningFailureType.OPERATION_FAILURE)
                }

            is CardNetworkError.FetchDigitizationStateFailure ->
                when (this.type) {
                    DigitizationStateFailureType.CONFIGURATION_FAILURE ->
                        FetchDigitizationStateFailure(
                            CardManagementError.DigitizationStateFailureType.CONFIGURATION_FAILURE,
                        )

                    DigitizationStateFailureType.OPERATION_FAILURE ->
                        FetchDigitizationStateFailure(
                            CardManagementError.DigitizationStateFailureType.OPERATION_FAILURE,
                        )
                }
        }
    }

    // Fallback to ConnectionIssue if the error is not CardNetworkError.
    else {
        CardManagementError.ConnectionIssue
    }
