package com.sara.android.modules.rules

data class Rule(
    val id: String,
    val condition: String,
    val action: String,
    val enabled: Boolean = true
)
