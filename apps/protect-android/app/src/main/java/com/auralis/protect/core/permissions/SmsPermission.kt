package com.auralis.protect.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object SmsPermission {
    const val RECEIVE_PERMISSION = Manifest.permission.RECEIVE_SMS
    const val SEND_PERMISSION = Manifest.permission.SEND_SMS

    fun canReceive(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            RECEIVE_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun canSend(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            SEND_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun allGranted(context: Context): Boolean {
        return canReceive(context) && canSend(context)
    }

    fun statusText(context: Context): String {
        return when {
            allGranted(context) -> "Ready"
            canReceive(context) && !canSend(context) -> "Receive only"
            !canReceive(context) && canSend(context) -> "Reply only"
            else -> "Permission needed"
        }
    }
}
