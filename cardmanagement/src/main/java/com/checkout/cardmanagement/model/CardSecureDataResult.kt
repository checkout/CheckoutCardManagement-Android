package com.checkout.cardmanagement.model

public sealed interface CardSecureDataResult<out T> {
    public data class Success<T>(
        val data: T,
    ) : CardSecureDataResult<T>

    public sealed interface Error : CardSecureDataResult<Nothing> {
        public val message: String

        public data class AuthenticationFailure(
            override val message: String,
            val tokenType: String,
        ) : Error

        public data class Unauthenticated(
            override val message: String,
        ) : Error

        public data class ConnectionIssue(
            override val message: String,
            val cause: Throwable?,
        ) : Error

        public data class PanNotViewed(
            override val message: String,
        ) : Error

        public data class UnableToPerformOperation(
            override val message: String,
            val cause: Throwable? = null,
        ) : Error

        public data class UnsupportedApiVersion(
            val version: Int,
            override val message: String,
        ) : Error
    }
}
