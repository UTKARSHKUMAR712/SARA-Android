package com.sara.android.modules.commands

import android.content.Context
import com.sara.android.modules.automation.AutomationEngine

class RuleCommand : Command {
    override val name = "rule"
    override val description = "Manage automation rules. Usage: /rule add [when X then Y] | /rule list | /rule disable <id> | /rule delete <id>"

    override fun execute(context: Context, args: List<String>): CommandResult {
        val engine = AutomationEngine.instance ?: return CommandResult.Text("⚙️ AutomationEngine is not running.")
        
        if (args.isEmpty()) {
            return CommandResult.Text(description)
        }
        
        val action = args[0].lowercase()
        
        when (action) {
            "list" -> {
                val rules = engine.listRules()
                if (rules.isEmpty()) return CommandResult.Text("📝 No rules configured.")
                
                val sb = StringBuilder("<b>📝 Automation Rules</b>\n\n")
                for (rule in rules) {
                    val status = if (rule.isEnabled) "✅" else "❌"
                    sb.append("$status [${rule.id}] ${rule.rawText}\n")
                }
                return CommandResult.Text(sb.toString())
            }
            "disable" -> {
                val id = args.getOrNull(1)?.toIntOrNull() ?: return CommandResult.Text("Usage: /rule disable <id>")
                val success = engine.disableRule(id)
                return if (success) CommandResult.Text("✅ Rule $id disabled.") else CommandResult.Text("❌ Rule $id not found.")
            }
            "delete", "remove" -> {
                val id = args.getOrNull(1)?.toIntOrNull() ?: return CommandResult.Text("Usage: /rule delete <id>")
                val success = engine.removeRule(id)
                return if (success) CommandResult.Text("🗑️ Rule $id deleted.") else CommandResult.Text("❌ Rule $id not found.")
            }
            "add" -> {
                val raw = args.drop(1).joinToString(" ")
                // Example parse: when battery < 20 then notify
                // Or: when chrome opens then notify
                
                val whenIndex = args.indexOf("when")
                val thenIndex = args.indexOf("then")
                
                if (whenIndex == -1 || thenIndex == -1 || whenIndex >= thenIndex) {
                    return CommandResult.Text("❌ Invalid format. Use: /rule add when <subject> <condition> [value] then <action>")
                }
                
                val conditionParts = args.subList(whenIndex + 1, thenIndex)
                val actionParts = args.subList(thenIndex + 1, args.size)
                
                if (conditionParts.size < 2 || actionParts.isEmpty()) {
                    return CommandResult.Text("❌ Invalid condition or action.")
                }
                
                val subject = conditionParts[0]
                val condition = conditionParts[1]
                val value = if (conditionParts.size > 2) conditionParts.subList(2, conditionParts.size).joinToString(" ") else null
                val resultingAction = actionParts.joinToString(" ")
                
                val rule = engine.addRule(raw, subject, condition, value, resultingAction)
                return CommandResult.Text("✅ Added rule [${rule.id}]: $raw")
            }
            else -> {
                return CommandResult.Text("Unknown rule action: $action\n$description")
            }
        }
    }
}
