package com.sara.android

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sara.android.events.EventBus
import com.sara.android.events.HealthEvent
import com.sara.android.events.ServiceStatus
import com.sara.android.events.ServiceStatusEvent
import com.sara.android.events.TelegramStatusEvent
import com.sara.android.service.SaraForegroundService
import com.sara.android.ui.LogBuffer
import com.sara.android.ui.TokenStorage

class MainActivity : AppCompatActivity() {

    private lateinit var tokenStorage: TokenStorage
    private lateinit var logBuffer: LogBuffer
    private lateinit var handler: Handler

    private var service: SaraForegroundService? = null
    private var bound = false

    private lateinit var tokenInput: EditText
    private lateinit var saveTokenBtn: Button
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var restartBtn: Button
    private lateinit var statusText: TextView
    private lateinit var uptimeText: TextView
    private lateinit var memoryText: TextView
    private lateinit var modulesText: TextView
    private lateinit var eventCountText: TextView
    private lateinit var telegramStatusText: TextView
    private lateinit var lastErrorText: TextView
    private lateinit var logConsole: TextView
    private lateinit var clearLogsBtn: TextView

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as SaraForegroundService.LocalBinder).getService()
            bound = true
            refreshHealth()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tokenStorage = TokenStorage(this)
        logBuffer = LogBuffer.getInstance(this)
        handler = Handler(Looper.getMainLooper())

        bindViews()
        loadSavedToken()
        setupEventListeners()
        setupLogListener()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }

        updateServiceStatusText(ServiceStatus.STOPPED)
    }

    override fun onStart() {
        super.onStart()
        bindService()
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }

    override fun onDestroy() {
        EventBus.clear()
        super.onDestroy()
    }

    private fun bindViews() {
        tokenInput = findViewById(R.id.tokenInput)
        saveTokenBtn = findViewById(R.id.saveTokenBtn)
        startBtn = findViewById(R.id.startBtn)
        stopBtn = findViewById(R.id.stopBtn)
        restartBtn = findViewById(R.id.restartBtn)
        statusText = findViewById(R.id.serviceStatus)
        uptimeText = findViewById(R.id.uptimeText)
        memoryText = findViewById(R.id.memoryText)
        modulesText = findViewById(R.id.modulesText)
        eventCountText = findViewById(R.id.eventCountText)
        telegramStatusText = findViewById(R.id.telegramStatusText)
        lastErrorText = findViewById(R.id.lastErrorText)
        logConsole = findViewById(R.id.logConsole)
        clearLogsBtn = findViewById(R.id.clearLogsBtn)

        saveTokenBtn.setOnClickListener { saveToken() }
        startBtn.setOnClickListener { startService() }
        stopBtn.setOnClickListener { stopService() }
        restartBtn.setOnClickListener { restartService() }
        clearLogsBtn.setOnClickListener {
            logBuffer.clear()
            logConsole.text = ""
        }
    }

    private fun loadSavedToken() {
        tokenStorage.getToken()?.let { token ->
            tokenInput.setText(token)
        }
    }

    private fun saveToken() {
        val token = tokenInput.text.toString().trim()
        if (token.isNotBlank()) {
            tokenStorage.saveToken(token)
            logBuffer.info("UI", "Bot token saved (${token.take(8)}...)")
        }
    }

    private fun setupEventListeners() {
        EventBus.subscribe(ServiceStatusEvent::class.java) { event ->
            runOnUiThread { updateServiceStatusText(event.status) }
        }
        EventBus.subscribe(HealthEvent::class.java) { event ->
            runOnUiThread { updateHealth(event) }
        }
        EventBus.subscribe(TelegramStatusEvent::class.java) { event ->
            runOnUiThread {
                telegramStatusText.text = "Telegram: ${if (event.connected) "Connected" else "Disconnected"}"
                if (event.message.isNotBlank()) {
                    logBuffer.info("Telegram", event.message)
                }
            }
        }
    }

    private fun setupLogListener() {
        logBuffer.addListener { entry ->
            runOnUiThread {
                logConsole.append(entry.formatted() + "\n")
                val scroll = findViewById<android.widget.ScrollView>(R.id.logScroll)
                scroll.fullScroll(android.widget.ScrollView.FOCUS_DOWN)
            }
        }
        for (entry in logBuffer.getEntries()) {
            logConsole.append(entry.formatted() + "\n")
        }
    }

    private fun updateServiceStatusText(status: ServiceStatus) {
        val color = when (status) {
            ServiceStatus.RUNNING -> "#4CAF50"
            ServiceStatus.STARTING -> "#FFC107"
            ServiceStatus.STOPPED -> "#FF5252"
            ServiceStatus.ERROR -> "#FF5252"
        }
        statusText.text = "Status: ${status.name}"
        statusText.setTextColor(android.graphics.Color.parseColor(color))
    }

    private fun updateHealth(event: HealthEvent) {
        val uptimeSec = event.uptime / 1000
        val hrs = uptimeSec / 3600
        val mins = (uptimeSec % 3600) / 60
        val secs = uptimeSec % 60
        uptimeText.text = "Uptime: ${hrs}h ${mins}m ${secs}s"
        memoryText.text = "Memory: ${event.memoryUsage}MB"
        modulesText.text = "Modules: ${event.moduleCount}"
        eventCountText.text = "Events: ${event.eventCount}"
        if (event.lastError != null) {
            lastErrorText.text = "Last Error: ${event.lastError}"
            lastErrorText.visibility = android.view.View.VISIBLE
        } else {
            lastErrorText.visibility = android.view.View.GONE
        }
    }

    private fun refreshHealth() {
        service?.runtime?.publishHealth()
    }

    private fun startService() {
        logBuffer.info("UI", "Starting SARA service...")
        val intent = Intent(this, SaraForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService()
    }

    private fun stopService() {
        logBuffer.info("UI", "Stopping SARA service...")
        val intent = Intent(this, SaraForegroundService::class.java).apply {
            action = SaraForegroundService.ACTION_STOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun restartService() {
        logBuffer.info("UI", "Restarting SARA service...")
        val intent = Intent(this, SaraForegroundService::class.java).apply {
            action = SaraForegroundService.ACTION_RESTART
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun bindService() {
        val intent = Intent(this, SaraForegroundService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE) {
            logBuffer.info("UI", "Notification permission: ${if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) "granted" else "denied"}")
        }
    }

    companion object {
        private const val PERMISSION_CODE = 1001
    }
}
