package com.sara.android.modules.automation

data class Rule(
    val id: Int,
    val rawText: String,
    val subject: String,
    val condition: String,
    val value: String?,
    val action: String,
    var isEnabled: Boolean = true
)
