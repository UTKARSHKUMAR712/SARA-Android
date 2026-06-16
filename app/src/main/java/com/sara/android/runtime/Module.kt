package com.sara.android.runtime

import android.content.Context

interface Module {
    val name: String
    fun onStart(context: Context)
    fun onStop()
}
