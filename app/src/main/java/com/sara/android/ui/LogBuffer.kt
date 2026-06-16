package com.sara.android.ui

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogBuffer(private val context: Context) {

    data class Entry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String
    ) {
        fun formatted(): String {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
            return "$time [$level] [$tag] $message"
        }
    }

    private val entries = mutableListOf<Entry>()
    private val listeners = mutableListOf<(Entry) -> Unit>()
    private val maxEntries = 500
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val logDir: File = File(context.filesDir, "logs")

    init {
        logDir.mkdirs()
    }

    fun log(level: String, tag: String, message: String) {
        val entry = Entry(System.currentTimeMillis(), level, tag, message)
        synchronized(entries) {
            entries.add(entry)
            if (entries.size > maxEntries) entries.removeAt(0)
        }
        writeToFile(entry)
        listeners.forEach { it(entry) }
    }

    fun info(tag: String, message: String) = log("INFO", tag, message)
    fun warn(tag: String, message: String) = log("WARN", tag, message)
    fun error(tag: String, message: String) = log("ERROR", tag, message)
    fun debug(tag: String, message: String) = log("DEBUG", tag, message)

    fun addListener(listener: (Entry) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Entry) -> Unit) {
        listeners.remove(listener)
    }

    fun getEntries(): List<Entry> = synchronized(entries) { entries.toList() }

    fun clear() {
        synchronized(entries) { entries.clear() }
    }

    private fun writeToFile(entry: Entry) {
        try {
            val dateStr = dateFormat.format(Date(entry.timestamp))
            val logFile = File(logDir, "sara_$dateStr.log")
            FileWriter(logFile, true).use { writer ->
                writer.write(entry.formatted() + "\n")
            }
        } catch (_: Exception) {}
    }

    companion object {
        @Volatile
        private var instance: LogBuffer? = null

        fun getInstance(context: Context): LogBuffer {
            return instance ?: synchronized(this) {
                instance ?: LogBuffer(context.applicationContext).also { instance = it }
            }
        }
    }
}
