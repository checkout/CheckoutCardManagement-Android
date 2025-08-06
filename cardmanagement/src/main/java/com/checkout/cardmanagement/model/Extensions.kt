package com.checkout.cardmanagement.model

// Parse to Environment in Sian
internal fun Environment.parse() =
    when (this) {
        Environment.SANDBOX -> com.checkout.cardnetwork.common.model.Environment.SANDBOX
        Environment.PRODUCTION -> com.checkout.cardnetwork.common.model.Environment.PRODUCTION
    }

internal fun com.checkout.cardnetwork.data.dto.CardState.fromNetworkCardState(): CardState =
    CardState.values().find { state -> state.name == name } ?: CardState.INACTIVE

// Possible Card State changes from the current state
internal fun CardState.getPossibleStateChanges(): List<CardState> =
    when (this) {
        CardState.ACTIVE -> listOf(CardState.SUSPENDED, CardState.REVOKED)
        CardState.INACTIVE, CardState.SUSPENDED -> listOf(CardState.ACTIVE, CardState.REVOKED)
        CardState.REVOKED -> emptyList()
    }

internal fun CardSuspendReason.toCardNetworkSuspendReason(): com.checkout.cardnetwork.data.dto.CardSuspendReason =
    com.checkout.cardnetwork.data.dto.CardSuspendReason
        .values()
        .first { it.name == this.name }

internal fun CardRevokeReason.toCardNetworkRevokeReason(): com.checkout.cardnetwork.data.dto.CardRevokeReason =
    com.checkout.cardnetwork.data.dto.CardRevokeReason
        .values()
        .first { it.name == this.name }
