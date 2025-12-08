package com.checkout.cardmanagement.utils

import kotlinx.coroutines.CoroutineScope

internal interface CoroutineScopeOwner {
    val scope: CoroutineScope

    fun cancel()
}
