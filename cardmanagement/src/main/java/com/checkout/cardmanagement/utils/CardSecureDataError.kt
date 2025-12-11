package com.checkout.cardmanagement.utils

import com.checkout.cardmanagement.model.CardManagementError
import com.checkout.cardmanagement.model.CardSecureDataResult
import com.checkout.cardmanagement.model.toCardManagementError

internal fun Throwable.toCardSecureDataError(): CardSecureDataResult.Error =
    when (val error = this.toCardManagementError()) {
        CardManagementError.AuthenticationFailure ->
            CardSecureDataResult.Error.AuthenticationFailure(
                message = error.message ?: "Authentication failed",
                tokenType = "single-use token",
            )

        CardManagementError.Unauthenticated ->
            CardSecureDataResult.Error.Unauthenticated(
                message = error.message ?: "No active session",
            )

        CardManagementError.ConnectionIssue ->
            CardSecureDataResult.Error.ConnectionIssue(
                message = this.message ?: error.message ?: "Connection error",
                cause = this,
            )

        CardManagementError.PanNotViewedFailure ->
            CardSecureDataResult.Error.PanNotViewed(
                message = error.message ?: "PAN not viewed",
            )

        CardManagementError.UnableToPerformSecureOperation ->
            CardSecureDataResult.Error.UnableToPerformOperation(
                message = this.message ?: error.message ?: "Secure operation failed",
                cause = this,
            )

        is CardManagementError.UnsupportedAPIVersion ->
            CardSecureDataResult.Error.UnsupportedApiVersion(
                version = error.currentVersion,
                message = error.message,
            )

        CardManagementError.InvalidStateRequested ->
            CardSecureDataResult.Error.UnableToPerformOperation(
                message = error.message ?: "Invalid card state",
                cause = error,
            )
        else ->
            CardSecureDataResult.Error.UnableToPerformOperation(
                message = error.message ?: "",
                cause = error,
            )
    }

internal fun <T> CardSecureDataResult<T>.getOrThrow(): T =
    when (this) {
        is CardSecureDataResult.Success -> data

        is CardSecureDataResult.Error.AuthenticationFailure ->
            throw CardManagementError.AuthenticationFailure

        is CardSecureDataResult.Error.Unauthenticated ->
            throw CardManagementError.Unauthenticated

        is CardSecureDataResult.Error.ConnectionIssue ->
            throw CardManagementError.ConnectionIssue

        is CardSecureDataResult.Error.PanNotViewed ->
            throw CardManagementError.PanNotViewedFailure

        is CardSecureDataResult.Error.UnableToPerformOperation ->
            throw CardManagementError.UnableToPerformSecureOperation

        is CardSecureDataResult.Error.UnsupportedApiVersion ->
            throw CardManagementError.UnsupportedAPIVersion(version)
    }
