package com.sara.android.modules.telegram

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.sara.android.events.EventBus
import com.sara.android.events.TelegramStatusEvent
import com.sara.android.events.TelegramNotificationEvent
import com.sara.android.events.TrackingSessionEvent
import com.sara.android.events.TrackingUpdateEvent
import com.sara.android.modules.commands.BatteryCommand
import com.sara.android.modules.commands.CameraCommand
import com.sara.android.modules.commands.ClipboardCommand
import com.sara.android.modules.commands.CommandResult
import com.sara.android.modules.commands.GpsCommand
import com.sara.android.modules.commands.DiagnosticsCommand
import com.sara.android.modules.commands.FileCommand
import com.sara.android.modules.commands.HelpCommand
import com.sara.android.modules.commands.LocationCommand
import com.sara.android.modules.commands.LockCommand
import com.sara.android.modules.commands.NativeCommandRouter
import com.sara.android.modules.commands.NetworkCommand
import com.sara.android.modules.commands.NotifyCommand
import com.sara.android.modules.commands.PingCommand
import com.sara.android.modules.commands.RingCommand
import com.sara.android.modules.commands.RuleCommand
import com.sara.android.modules.commands.ScreenshotCommand
import com.sara.android.modules.commands.StatusCommand
import com.sara.android.modules.commands.TorchCommand
import com.sara.android.modules.commands.TrackCommand
import com.sara.android.modules.commands.VolumeCommand
import com.sara.android.modules.commands.WifiCommand
import com.sara.android.runtime.Module
import com.sara.android.ui.LogBuffer
import com.sara.android.ui.TokenStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class TelegramModule : Module {

    override val name = "TelegramModule"

    private var appContext: Context? = null
    private var client: TelegramClient? = null
    private var initThread: HandlerThread? = null
    private var pollThread: HandlerThread? = null
    private var pollHandler: Handler? = null
    private var polling = false
    private var lastUpdateId = 0L
    private val pollIntervalMs = 2000L
    private var backoffMs = 1000L
    private val commandRouter = NativeCommandRouter()

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!polling) return
            val c = client ?: return
            val ctx = appContext ?: return
            val log = LogBuffer.getInstance(ctx)
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
                backoffMs = 1000L
            } catch (e: Exception) {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                log.error(name, "Poll error: ${e::class.java.simpleName}: ${e.message}")
                for (line in sw.toString().lines().take(5)) {
                    if (line.isNotBlank()) log.error(name, "  $line")
                }
                log.error(name, "Backing off ${backoffMs}ms")
                backoffMs = (backoffMs * 2).coerceAtMost(30000L)
            }
            if (polling) {
                pollHandler?.postDelayed(this, backoffMs)
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
        commandRouter.registerAll(listOf(
            PingCommand(),
            StatusCommand(),
            DiagnosticsCommand(),
            LockCommand(),
            HelpCommand(commandRouter),
            BatteryCommand(),
            NetworkCommand(),
            WifiCommand(),
            GpsCommand(),
            LocationCommand(),
            VolumeCommand(),
            RingCommand(),
            TorchCommand(),
            ClipboardCommand(),
            NotifyCommand(),
            CameraCommand(),
            ScreenshotCommand(),
            TrackCommand(),
            RuleCommand(),
            FileCommand()
        ))
        subscribeTrackingEvents()
        LogBuffer.getInstance(context).info(name, "Connecting to Telegram...")

        initThread = HandlerThread("TelegramInit").apply { start() }
        Handler(initThread!!.looper).post {
            val log = LogBuffer.getInstance(context)
            try {
                log.info(name, "[HTTP] GET https://api.telegram.org/bot${token.take(8)}.../getMe")
                val me = client!!.getMe()
                if (me != null) {
                    val username = me.optString("username", "?")
                    val firstName = me.optString("first_name", "")
                    log.info(name, "Connected as @$username ($firstName)")
                    EventBus.publish(TelegramStatusEvent(true, "Connected as @$username"))
                    startPolling(context)
                } else {
                    log.error(name, "getMe returned null — token might be invalid")
                    EventBus.publish(TelegramStatusEvent(false, "getMe returned null"))
                }
            } catch (e: Exception) {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                log.error(name, "Connection failed: ${e::class.java.simpleName}: ${e.message}")
                for (line in sw.toString().lines().take(8)) {
                    if (line.isNotBlank()) log.error(name, "  $line")
                }
                EventBus.publish(TelegramStatusEvent(false, "${e::class.java.simpleName}: ${e.message}"))
            }
        }
    }

    override fun onStop() {
        EventBus.unsubscribeAll(this)
        polling = false
        pollHandler?.removeCallbacksAndMessages(null)
        pollThread?.quitSafely()
        pollThread = null
        pollHandler = null
        initThread?.quitSafely()
        initThread = null
        client = null
    }

    private fun subscribeTrackingEvents() {
        EventBus.subscribe(this, TrackingUpdateEvent::class.java) { event ->
            val c = client ?: return@subscribe
            val u = event.update
            val df = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            val timeStr = df.format(java.util.Date(u.locationTime))
            val text = buildString {
                appendLine("\uD83D\uDCCD Tracking Update #${u.updateNumber}")
                appendLine()
                appendLine("Latitude: ${"%.6f".format(u.latitude)}")
                appendLine("Longitude: ${"%.6f".format(u.longitude)}")
                appendLine("Accuracy: ${"%.1f".format(u.accuracy)} m")
                appendLine("Provider: ${u.provider}")
                appendLine("Timestamp: $timeStr")
                if (u.speed > 0f) appendLine("Speed: ${"%.1f".format(u.speed)} m/s")
                if (u.bearing > 0f) appendLine("Bearing: ${"%.1f".format(u.bearing)}\u00b0")
                appendLine()
                appendLine("https://www.google.com/maps?q=${"%.6f".format(u.latitude)},${"%.6f".format(u.longitude)}")
            }
            try {
                c.sendMessage(u.chatId, text)
            } catch (_: Exception) {}
        }

        EventBus.subscribe(this, TrackingSessionEvent::class.java) { event ->
            val c = client ?: return@subscribe
            val s = event.summary
            val duration = s.endTime - s.startTime
            val durSec = duration / 1000
            val hrs = durSec / 3600
            val mins = (durSec % 3600) / 60
            val secs = durSec % 60
            val df = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            val text = buildString {
                appendLine("Tracking Session Summary")
                appendLine()
                appendLine("Start: ${df.format(java.util.Date(s.startTime))}")
                appendLine("End: ${df.format(java.util.Date(s.endTime))}")
                appendLine("Duration: ${hrs}h ${mins}m ${secs}s")
                appendLine("Updates sent: ${s.totalUpdates}")
                appendLine("Total distance: ${"%.1f".format(s.totalDistance)} m")
                if (s.averageSpeed > 0f) {
                    append("Avg speed: ${"%.2f".format(s.averageSpeed)} m/s")
                    val kmh = s.averageSpeed * 3.6f
                    append(" (${"%.1f".format(kmh)} km/h)")
                    appendLine()
                }
            }
            try {
                c.sendMessage(s.chatId, text)
            } catch (_: Exception) {}
        }

        EventBus.subscribe(this, TelegramNotificationEvent::class.java) { event ->
            val c = client ?: return@subscribe
            val chatId = com.sara.android.modules.commands.TrackCommand.lastChatId
            if (chatId != 0L) {
                try {
                    c.sendMessage(chatId, event.message)
                } catch (_: Exception) {}
            }
        }
    }

    private fun startPolling(context: Context) {
        pollThread = HandlerThread("TelegramPoll").apply { start() }
        pollHandler = Handler(pollThread!!.looper)
        polling = true
        backoffMs = 1000L
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

        com.sara.android.modules.commands.TrackCommand.lastChatId = chatId

        val log = LogBuffer.getInstance(ctx)
        val startMs = System.currentTimeMillis()
        log.info(name, "Incoming from @$userName ($firstName): $text")

        val reply: CommandResult? = try {
            when {
                text.equals("hi", ignoreCase = true) || text.equals("/start", ignoreCase = true) -> {
                    CommandResult.Text("Hello! I am SARA \uD83E\uDD16")
                }
                text.startsWith("/") || text.startsWith("\\") -> {
                    commandRouter.route(text, ctx)
                }
                else -> null
            }
        } catch (e: Exception) {
            log.error(name, "Command crashed: ${e.message}")
            CommandResult.Text("⚠️ Internal Error: ${e.message}")
        }

        if (reply != null) {
            val elapsed = System.currentTimeMillis() - startMs
            try {
                when (reply) {
                    is CommandResult.Text -> {
                        client.sendMessage(chatId, reply.message)
                        log.info(name, "Replied to @$userName (${elapsed}ms): ${reply.message.take(60)}")
                    }
                    is CommandResult.Photo -> {
                        val file = java.io.File(reply.filePath)
                        if (file.exists()) {
                            client.sendPhoto(chatId, reply.filePath, reply.caption)
                            log.info(name, "Sent photo to @$userName (${elapsed}ms): ${file.name}")
                        } else {
                            client.sendMessage(chatId, "Photo file not found: ${reply.filePath}")
                            log.error(name, "Photo file missing: ${reply.filePath}")
                        }
                    }
                }
            } catch (e: Exception) {
                log.error(name, "Reply failed: ${e::class.java.simpleName}: ${e.message}")
            }
        }
    }

    companion object {
        fun testToken(context: Context, token: String) {
            val log = LogBuffer.getInstance(context)
            Thread {
                log.info("Telegram", "Testing token: ${token.take(8)}...")
                log.info("Telegram", "[HTTP] GET https://api.telegram.org/bot${token.take(8)}.../getMe")
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
                                    log.error("Telegram", "Send to $cid failed: ${e::class.java.simpleName}: ${e.message}")
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
                    log.error("Telegram", "Test failed: ${e::class.java.simpleName}: ${e.message}")
                }
            }.start()
        }
    }
}

class TelegramClient(private val token: String) {

    private val baseUrl = "https://api.telegram.org/bot$token"

    fun getMe(): JSONObject? {
        val json = request("$baseUrl/getMe", 10000)
        return json?.optJSONObject("result")
    }

    fun getUpdates(offset: Long, timeout: Int): JSONArray {
        val url = "$baseUrl/getUpdates?offset=$offset&timeout=$timeout"
        val json = request(url, 35000)
        return json?.optJSONArray("result") ?: JSONArray()
    }

    fun sendMessage(chatId: Long, text: String): Boolean {
        val encoded = URLEncoder.encode(text, "UTF-8")
        val url = "$baseUrl/sendMessage?chat_id=$chatId&text=$encoded&parse_mode=HTML"
        val json = request(url, 10000)
        return json?.optBoolean("ok", false) == true
    }

    fun sendPhoto(chatId: Long, filePath: String, caption: String? = null): Boolean {
        val url = URL("$baseUrl/sendPhoto")
        val boundary = "SARA${System.currentTimeMillis()}"
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.useCaches = false
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        try {
            val outputStream = conn.outputStream
            val writer = java.io.BufferedWriter(java.io.OutputStreamWriter(outputStream, "UTF-8"))

            writer.write("--$boundary\r\n")
            writer.write("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n")
            writer.write(chatId.toString())
            writer.write("\r\n")

            if (caption != null) {
                writer.write("--$boundary\r\n")
                writer.write("Content-Disposition: form-data; name=\"caption\"\r\n\r\n")
                writer.write(caption)
                writer.write("\r\n")
            }

            val file = java.io.File(filePath)
            writer.write("--$boundary\r\n")
            writer.write("Content-Disposition: form-data; name=\"photo\"; filename=\"${file.name}\"\r\n")
            writer.write("Content-Type: image/jpeg\r\n\r\n")
            writer.flush()

            outputStream.write(file.readBytes())
            outputStream.flush()

            writer.write("\r\n--$boundary--\r\n")
            writer.flush()
            writer.close()

            val code = conn.responseCode
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
                return false
            }
            val json = JSONObject(body)
            return json.optBoolean("ok", false)
        } finally {
            conn.disconnect()
        }
    }

    private fun request(url: String, readTimeoutMs: Int): JSONObject? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = readTimeoutMs
        try {
            val code = conn.responseCode
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                return null
            }
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }
}
