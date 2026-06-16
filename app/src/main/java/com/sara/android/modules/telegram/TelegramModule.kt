package com.sara.android.modules.telegram

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.sara.android.events.EventBus
import com.sara.android.events.TelegramStatusEvent
import com.sara.android.runtime.Module
import com.sara.android.ui.LogBuffer
import com.sara.android.ui.TokenStorage
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class TelegramModule : Module {

    override val name = "TelegramModule"

    private var appContext: Context? = null
    private var client: TelegramClient? = null
    private var pollThread: HandlerThread? = null
    private var pollHandler: Handler? = null
    private var polling = false
    private var lastUpdateId = 0L
    private val pollIntervalMs = 2000L

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!polling) return
            val c = client ?: return
            val ctx = appContext ?: return
            try {
                val updates = c.getUpdates(lastUpdateId + 1, 10)
                for (i in 0 until updates.length()) {
                    val update = updates.getJSONObject(i)
                    val uid = update.optLong("update_id", 0)
                    if (uid > lastUpdateId) {
                        lastUpdateId = uid
                        processUpdate(ctx, c, update)
                    }
                }
            } catch (e: Exception) {
                LogBuffer.getInstance(ctx).error(name, "Poll error: ${e.message}")
            }
            if (polling) {
                pollHandler?.postDelayed(this, pollIntervalMs)
            }
        }
    }

    override fun onStart(context: Context) {
        appContext = context.applicationContext
        val storage = TokenStorage(context)
        val token = storage.getToken()
        if (token.isNullOrBlank()) {
            LogBuffer.getInstance(context).warn(name, "No bot token — Telegram not configured")
            EventBus.publish(TelegramStatusEvent(false, "No token"))
            return
        }
        client = TelegramClient(token)
        LogBuffer.getInstance(context).info(name, "Connecting to Telegram...")
        try {
            val me = client!!.getMe()
            if (me != null) {
                val username = me.optString("username", "?")
                LogBuffer.getInstance(context).info(name, "Connected as @$username")
                EventBus.publish(TelegramStatusEvent(true, "Connected as @$username"))
                startPolling(context)
            } else {
                LogBuffer.getInstance(context).error(name, "getMe returned null — invalid token?")
                EventBus.publish(TelegramStatusEvent(false, "getMe failed"))
            }
        } catch (e: Exception) {
            LogBuffer.getInstance(context).error(name, "Connection failed: ${e.message}")
            EventBus.publish(TelegramStatusEvent(false, e.message ?: "Unknown error"))
        }
    }

    override fun onStop() {
        polling = false
        pollHandler?.removeCallbacksAndMessages(null)
        pollThread?.quitSafely()
        pollThread = null
        pollHandler = null
        client = null
    }

    private fun startPolling(context: Context) {
        pollThread = HandlerThread("TelegramPoll").apply { start() }
        pollHandler = Handler(pollThread!!.looper)
        polling = true
        LogBuffer.getInstance(context).info(name, "Polling started (interval: ${pollIntervalMs}ms)")
        pollHandler?.post(pollRunnable)
    }

    private fun processUpdate(ctx: Context, client: TelegramClient, update: JSONObject) {
        val message = update.optJSONObject("message") ?: return
        val text = message.optString("text", "")
        val chat = message.optJSONObject("chat") ?: return
        val chatId = chat.optLong("id", 0)
        val from = message.optJSONObject("from")
        val userName = from?.optString("username", "?") ?: "?"
        val firstName = from?.optString("first_name", "") ?: ""

        val log = LogBuffer.getInstance(ctx)
        log.info(name, "Incoming from @$userName ($firstName): $text")

        when {
            text.equals("hi", ignoreCase = true) || text.equals("/start", ignoreCase = true) -> {
                val reply = "Hello! I am SARA \uD83E\uDD16"
                try {
                    client.sendMessage(chatId, reply)
                    log.info(name, "Replied to @$userName: $reply")
                } catch (e: Exception) {
                    log.error(name, "Reply failed: ${e.message}")
                }
            }
        }
    }

    companion object {
        fun testToken(context: Context, token: String) {
            val log = LogBuffer.getInstance(context)
            Thread {
                log.info("Telegram", "Testing token: ${token.take(8)}...")
                val client = TelegramClient(token)
                try {
                    val me = client.getMe()
                    if (me != null) {
                        val username = me.optString("username", "?")
                        val firstName = me.optString("first_name", "")
                        log.info("Telegram", "Token valid! Bot: @$username ($firstName)")

                        val updates = client.getUpdates(0, 5)
                        var sent = false
                        for (i in 0 until updates.length()) {
                            val msg = updates.getJSONObject(i)
                                .optJSONObject("message") ?: continue
                            val cid = msg.optJSONObject("chat")?.optLong("id", 0) ?: 0
                            if (cid != 0L) {
                                try {
                                    client.sendMessage(
                                        cid,
                                        "✅ SARA Android is online and connected successfully."
                                    )
                                    log.info("Telegram", "Test message sent to chat $cid")
                                    sent = true
                                } catch (e: Exception) {
                                    log.error("Telegram", "Send to $cid failed: ${e.message}")
                                }
                            }
                        }
                        if (!sent) {
                            log.warn("Telegram", "No chat found. Send a message to the bot on Telegram first.")
                        }
                    } else {
                        log.error("Telegram", "Token invalid (getMe returned null)")
                    }
                } catch (e: Exception) {
                    log.error("Telegram", "Test failed: ${e.message}")
                }
            }.start()
        }
    }
}

class TelegramClient(private val token: String) {

    private val baseUrl = "https://api.telegram.org/bot$token"

    fun getMe(): JSONObject? {
        val json = get("$baseUrl/getMe")
        return json?.optJSONObject("result")
    }

    fun getUpdates(offset: Long, timeout: Int): JSONArray {
        val url = "$baseUrl/getUpdates?offset=$offset&timeout=$timeout"
        val json = get(url)
        return json?.optJSONArray("result") ?: JSONArray()
    }

    fun sendMessage(chatId: Long, text: String): Boolean {
        val encoded = URLEncoder.encode(text, "UTF-8")
        val url = "$baseUrl/sendMessage?chat_id=$chatId&text=$encoded&parse_mode=HTML"
        val json = get(url)
        return json?.optBoolean("ok", false) == true
    }

    private fun get(url: String): JSONObject? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        try {
            val code = conn.responseCode
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                return JSONObject().apply { put("ok", false); put("error_body", err) }
            }
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }
}
