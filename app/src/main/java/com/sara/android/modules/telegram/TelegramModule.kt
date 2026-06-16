package com.sara.android.modules.telegram

import android.content.Context
import com.sara.android.events.EventBus
import com.sara.android.events.TelegramStatusEvent
import com.sara.android.runtime.Module
import com.sara.android.ui.LogBuffer
import com.sara.android.ui.TokenStorage

class TelegramModule : Module {
    override val name = "TelegramModule"

    private var token: String? = null
    private var connected = false

    override fun onStart(context: Context) {
        val log = LogBuffer.getInstance(context)
        val storage = TokenStorage(context)
        token = storage.getToken()

        if (token.isNullOrBlank()) {
            log.warn(name, "No bot token found — Telegram not configured")
            EventBus.publish(TelegramStatusEvent(false, "No token configured"))
            return
        }

        log.info(name, "Initializing Telegram with token: ${token!!.take(8)}...")
        connected = try {
            attemptConnection(token!!)
            log.info(name, "Telegram placeholder connected successfully (no real network call)")
            EventBus.publish(TelegramStatusEvent(true, "Placeholder connected"))
            true
        } catch (e: Exception) {
            log.error(name, "Telegram init failed: ${e.message}")
            EventBus.publish(TelegramStatusEvent(false, "Init failed: ${e.message}"))
            false
        }
    }

    override fun onStop() {
        connected = false
        token = null
    }

    private fun attemptConnection(token: String): Boolean {
        // Placeholder — no real network call yet
        return token.isNotBlank()
    }

    fun isConnected(): Boolean = connected
}
