package com.sara.android.modules.automation

import android.content.Context
import com.sara.android.events.*
import com.sara.android.runtime.Module
import com.sara.android.ui.LogBuffer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class AutomationEngine : Module {
    override val name = "AutomationEngine"

    private val rules = mutableListOf<Rule>()
    private val ruleIdCounter = AtomicInteger(1)
    private var appContext: Context? = null

    companion object {
        var instance: AutomationEngine? = null
            private set
    }

    override fun onStart(context: Context) {
        appContext = context.applicationContext
        instance = this
        loadRules()
        setupListeners()
    }

    override fun onStop() {
        EventBus.unsubscribeAll(this)
        saveRules()
        instance = null
    }

    fun addRule(raw: String, subject: String, condition: String, value: String?, action: String): Rule {
        val rule = Rule(ruleIdCounter.getAndIncrement(), raw, subject, condition, value, action)
        rules.add(rule)
        saveRules()
        return rule
    }

    fun removeRule(id: Int): Boolean {
        val removed = rules.removeIf { it.id == id }
        if (removed) saveRules()
        return removed
    }

    fun disableRule(id: Int): Boolean {
        val rule = rules.find { it.id == id }
        if (rule != null) {
            rule.isEnabled = false
            saveRules()
            return true
        }
        return false
    }

    fun listRules(): List<Rule> = rules.toList()

    private fun setupListeners() {
        EventBus.subscribe(this, BatteryEvent::class.java) { event ->
            evaluateRules("battery", event.level.toString(), event)
        }
        EventBus.subscribe(this, AppEvent::class.java) { event ->
            evaluateRules("app", event.packageName, event)
            if (event.packageName.contains("chrome") && event.action == "opened") {
                evaluateRules("chrome", "opened", event)
            }
        }
        EventBus.subscribe(this, NetworkEvent::class.java) { event ->
            evaluateRules("network", if (event.isConnected) "connected" else "disconnected", event)
            evaluateRules("wifi", if (event.isConnected && event.type == "Wi-Fi") "connected" else "disconnected", event)
        }
    }

    private fun evaluateRules(subject: String, stateValue: String, event: Event) {
        for (rule in rules) {
            if (!rule.isEnabled) continue
            if (rule.subject.equals(subject, ignoreCase = true)) {
                var match = false
                when (rule.condition) {
                    "<" -> {
                        val v1 = stateValue.toIntOrNull()
                        val v2 = rule.value?.toIntOrNull()
                        if (v1 != null && v2 != null) match = v1 < v2
                    }
                    ">" -> {
                        val v1 = stateValue.toIntOrNull()
                        val v2 = rule.value?.toIntOrNull()
                        if (v1 != null && v2 != null) match = v1 > v2
                    }
                    "==", "=" -> match = stateValue.equals(rule.value, ignoreCase = true)
                    "opens", "opened" -> match = stateValue.equals("opened", ignoreCase = true)
                    "closes", "closed" -> match = stateValue.equals("closed", ignoreCase = true)
                }

                if (match) {
                    executeAction(rule, event)
                }
            }
        }
    }

    private fun executeAction(rule: Rule, event: Event) {
        val log = LogBuffer.getInstance(appContext!!)
        log.info(name, "Rule triggered: ${rule.rawText}")
        
        when (rule.action.lowercase()) {
            "notify" -> {
                EventBus.publish(TelegramNotificationEvent("Rule triggered: ${rule.rawText}"))
            }
        }
    }

    private fun getRulesFile(): File = File(appContext!!.filesDir, "rules.json")

    private fun saveRules() {
        val array = JSONArray()
        for (rule in rules) {
            val obj = JSONObject()
            obj.put("id", rule.id)
            obj.put("rawText", rule.rawText)
            obj.put("subject", rule.subject)
            obj.put("condition", rule.condition)
            obj.put("value", rule.value)
            obj.put("action", rule.action)
            obj.put("isEnabled", rule.isEnabled)
            array.put(obj)
        }
        getRulesFile().writeText(array.toString())
    }

    private fun loadRules() {
        val file = getRulesFile()
        if (!file.exists()) return
        try {
            val array = JSONArray(file.readText())
            var maxId = 0
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getInt("id")
                if (id > maxId) maxId = id
                rules.add(Rule(
                    id,
                    obj.getString("rawText"),
                    obj.getString("subject"),
                    obj.getString("condition"),
                    if (obj.has("value")) obj.getString("value") else null,
                    obj.getString("action"),
                    obj.getBoolean("isEnabled")
                ))
            }
            ruleIdCounter.set(maxId + 1)
        } catch (e: Exception) {
            LogBuffer.getInstance(appContext!!).error(name, "Failed to load rules: ${e.message}")
        }
    }
}
