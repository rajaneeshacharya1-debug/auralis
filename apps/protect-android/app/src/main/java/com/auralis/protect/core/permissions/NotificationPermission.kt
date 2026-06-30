package com.auralis.protect.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object NotificationPermission {
    const val PERMISSION = Manifest.permission.POST_NOTIFICATIONS

    fun isRequired(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    fun isGranted(context: Context): Boolean {
        if (!isRequired()) return true

        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun statusText(context: Context): String {
        return if (isGranted(context)) {
            "Allowed"
        } else {
            "Permission needed"
        }
    }
}
