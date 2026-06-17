package com.sara.android.runtime

import android.content.Context

interface Module {
    val name: String
    val isInitialized: Boolean get() = true
    val lastError: String? get() = null

    fun onStart(context: Context)
    fun onStop()

    fun getHealthStatus(): HealthStatus {
        return HealthStatus(isInitialized, lastError)
    }
}

data class HealthStatus(val isHealthy: Boolean, val message: String?)
