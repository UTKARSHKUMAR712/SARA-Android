package com.sara.android.modules.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object PermissionManager {
    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        return permissions.all { hasPermission(context, it) }
    }
    
    fun getMissingPermissions(context: Context, permissions: Array<String>): List<String> {
        return permissions.filter { !hasPermission(context, it) }
    }
}
