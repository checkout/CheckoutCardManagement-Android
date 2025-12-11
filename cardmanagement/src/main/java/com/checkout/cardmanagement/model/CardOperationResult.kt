package com.checkout.cardmanagement.model

/**
 * Result type for card operations in the Card Management SDK.
 *
 * CardOperationResult represents the outcome of card operations such as state changes
 * (activate, suspend, revoke), fetching digitization state, and provisioning to digital wallets.
 * It provides a type-safe way to handle both successful operations and various error scenarios.
 *
 * This sealed interface has two main outcomes:
 * - [Success]: The operation completed successfully and contains result data of type T
 * - [Error]: The operation failed with a specific error type providing context about the failure
 *
 * Use pattern matching with when statements to handle different outcomes:
 * ```kotlin
 * when (result) {
 *     is CardOperationResult.Success -> // Handle success
 *     is CardOperationResult.Error.Unauthenticated -> // Handle auth error
 *     is CardOperationResult.Error -> // Handle other errors
 * }
 * ```
 *
 * @param T The type of data returned on successful operations
 * @see Card.activate
 * @see Card.suspend
 * @see Card.revoke
 */
public sealed interface CardOperationResult<out T> {
    /**
     * Indicates the operation completed successfully.
     *
     * @param data The result data of type T returned by the successful operation
     */
    public data class Success<T>(
        val data: T,
    ) : CardOperationResult<T>

    /**
     * Base type for all operation errors.
     *
     * Each error subtype provides specific context about what went wrong, including
     * error messages and additional properties relevant to the failure. Use pattern
     * matching with when statements to handle different error scenarios appropriately.
     *
     * @property message A human-readable description of the error
     */
    public sealed interface Error : CardOperationResult<Nothing> {
        public val message: String

        /**
         * The authentication token failed validation.
         *
         * @property tokenType The type of token that failed authentication
         */
        public data class AuthenticationFailure(
            override val message: String,
            val tokenType: String,
        ) : Error

        /**
         * The SDK or operation is misconfigured.
         *
         * @property hint Guidance on how to resolve the configuration issue
         */
        public data class ConfigurationFailure(
            override val message: String,
            val hint: String,
        ) : Error

        /**
         * The operation was cancelled by the user or system.
         */
        public data class OperationCancelled(
            override val message: String,
        ) : Error

        /**
         * Failed to fetch the card's digitization state from the wallet provider.
         *
         * @property cause The underlying exception that caused the failure, if available
         */
        public data class DigitizationStateFailure(
            override val message: String,
            val cause: Throwable?,
        ) : Error

        public data class DeviceEnvironmentUnsafe(
            override val message: String,
        ) : Error

        public data class GooglePayUnsupported(
            override val message: String,
        ) : Error

        public data class DebugSDKUsed(
            override val message: String,
        ) : Error

        public data class CardNotFound(
            override val message: String,
        ) : Error

        /**
         * Network connectivity problems prevented the operation from completing.
         *
         * @property cause The underlying exception that caused the connection issue, if available
         */
        public data class ConnectionIssue(
            override val message: String,
            val cause: Throwable?,
        ) : Error

        /**
         * The requested state change is not allowed from the card's current state.
         *
         * @property currentState The card's current state
         * @property requestedState The state that was attempted to transition to
         */
        public data class InvalidStateTransition(
            override val message: String,
            val currentState: CardState,
            val requestedState: CardState,
        ) : Error

        /**
         * No active session exists. Call [com.checkout.cardmanagement.CheckoutCardManager.logInSession] to authenticate.
         */
        public data class Unauthenticated(
            override val message: String,
        ) : Error

        /**
         * A general operation failure occurred.
         *
         * @property cause The underlying exception that caused the failure, if available
         */
        public data class OperationFailure(
            override val message: String,
            val cause: Throwable?,
        ) : Error
    }
}

/**
 * Converts a [CardOperationResult] to a standard Kotlin [Result].
 *
 * This extension function provides interoperability with Result-based APIs by converting
 * the CardOperationResult to Kotlin's built-in Result type. Success cases are mapped to
 * Result.success, and error cases are mapped to Result.failure with appropriate exceptions.
 *
 * @return Result.success with the data on success, or Result.failure with an exception on error
 */
public fun <T> CardOperationResult<T>.toResult(): Result<T> =
    when (this) {
        is CardOperationResult.Success -> Result.success(data)
        is CardOperationResult.Error.ConnectionIssue -> Result.failure(cause ?: Exception(message))
        is CardOperationResult.Error.DigitizationStateFailure -> Result.failure(cause ?: Exception(message))
        is CardOperationResult.Error.OperationFailure -> Result.failure(cause ?: Exception(message))
        is CardOperationResult.Error -> Result.failure(Exception(message))
    }
