package com.checkout.cardmanagement.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

internal class DefaultCoroutineScopeHandler : CoroutineScopeOwner {
    private val scopeJob = SupervisorJob()
    private val scopeContext: CoroutineContext = scopeJob + Dispatchers.Main.immediate

    override val scope: CoroutineScope = CoroutineScope(scopeContext)

    override fun cancel() {
        scopeJob.cancel()
    }
}
