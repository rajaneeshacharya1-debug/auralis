package com.auralis.protect.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

object SmsReplySender {
    fun canSend(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION")
    fun sendReply(
        context: Context,
        phoneNumber: String?,
        message: String,
        subscriptionId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID
    ): Boolean {
        if (phoneNumber.isNullOrBlank()) return false
        if (!canSend(context)) return false

        return try {
            val smsManager: SmsManager = when {
                subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID -> {
                    SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    context.getSystemService(SmsManager::class.java)
                }

                else -> {
                    SmsManager.getDefault()
                }
            }

            val parts = smsManager.divideMessage(message)

            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    parts,
                    null,
                    null
                )
            } else {
                smsManager.sendTextMessage(
                    phoneNumber,
                    null,
                    message,
                    null,
                    null
                )
            }

            true
        } catch (_: Exception) {
            false
        }
    }
}
