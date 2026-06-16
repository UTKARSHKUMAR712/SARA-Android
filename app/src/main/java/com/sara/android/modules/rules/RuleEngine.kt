package com.sara.android.modules.rules

import android.content.Context
import com.sara.android.runtime.Module

class RuleEngine : Module {
    override val name = "RuleEngine"

    private val rules = mutableListOf<Rule>()

    override fun onStart(context: Context) {
    }

    override fun onStop() {
    }
}
