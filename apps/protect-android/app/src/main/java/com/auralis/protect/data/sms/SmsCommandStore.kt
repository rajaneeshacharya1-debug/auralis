package com.auralis.protect.data.sms

import android.content.Context

data class SmsCommandStatus(
    val lastCommand: String,
    val lastDetail: String,
    val lastUpdatedAt: Long
)

object SmsCommandStore {
    private const val PREFS_NAME = "auralis_sms_command_store"
    private const val KEY_LAST_COMMAND = "last_command"
    private const val KEY_LAST_DETAIL = "last_detail"
    private const val KEY_LAST_UPDATED_AT = "last_updated_at"

    const val BOOT_COMMAND = "#AURALIS-BOOT-4729"
    const val STOP_COMMAND = "#AURALIS-STOP-4729"
    const val STATUS_COMMAND = "#AURALIS-STATUS-4729"
    const val SNAPSHOT_COMMAND = "#AURALIS-SNAPSHOT-4729"
    const val REPORT_COMMAND = "#AURALIS-REPORT-4729"

    fun saveCommand(
        context: Context,
        command: String,
        detail: String
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_COMMAND, command)
            .putString(KEY_LAST_DETAIL, detail)
            .putLong(KEY_LAST_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun readStatus(context: Context): SmsCommandStatus {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        return SmsCommandStatus(
            lastCommand = prefs.getString(KEY_LAST_COMMAND, "None") ?: "None",
            lastDetail = prefs.getString(KEY_LAST_DETAIL, "No SMS command received yet") ?: "No SMS command received yet",
            lastUpdatedAt = prefs.getLong(KEY_LAST_UPDATED_AT, 0L)
        )
    }

    fun ageText(status: SmsCommandStatus): String {
        if (status.lastUpdatedAt <= 0L) return "waiting"

        val seconds = ((System.currentTimeMillis() - status.lastUpdatedAt) / 1000L).coerceAtLeast(0L)

        return when {
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60}m ago"
            else -> "${seconds / 3600}h ago"
        }
    }
}
