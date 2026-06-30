package com.auralis.protect.data.localcontrol

import android.content.Context

data class LocalControlStatus(
    val active: Boolean,
    val lastCommand: String,
    val detail: String,
    val updatedAt: Long
)

object LocalControlStore {
    private const val PREFS_NAME = "auralis_local_control_store"
    private const val KEY_ACTIVE = "active"
    private const val KEY_LAST_COMMAND = "last_command"
    private const val KEY_DETAIL = "detail"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val KEY_TOKEN = "local_control_token"
    private const val ACTIVE_STALE_AFTER_MS = 15_000L

    const val PORT = 4729
    private const val DEFAULT_TOKEN = "auralis-local-4729"

    fun setActive(
        context: Context,
        active: Boolean
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE, active)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun saveStatus(
        context: Context,
        active: Boolean,
        command: String,
        detail: String
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE, active)
            .putString(KEY_LAST_COMMAND, command)
            .putString(KEY_DETAIL, detail)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun markHeartbeat(context: Context) {
        val status = readStatus(context, applyStaleCheck = false)
        if (!status.active) return

        saveStatus(
            context = context,
            active = true,
            command = status.lastCommand,
            detail = status.detail
        )
    }

    fun readStatus(context: Context): LocalControlStatus {
        return readStatus(context, applyStaleCheck = true)
    }

    private fun readStatus(
        context: Context,
        applyStaleCheck: Boolean
    ): LocalControlStatus {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val active = prefs.getBoolean(KEY_ACTIVE, false)
        val updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)

        if (applyStaleCheck && active && isStale(updatedAt)) {
            return LocalControlStatus(
                active = false,
                lastCommand = "PANEL STALE",
                detail = "Local Wi-Fi control is not responding",
                updatedAt = updatedAt
            )
        }

        return LocalControlStatus(
            active = active,
            lastCommand = prefs.getString(KEY_LAST_COMMAND, "None") ?: "None",
            detail = prefs.getString(KEY_DETAIL, "Local Wi-Fi control is not running") ?: "Local Wi-Fi control is not running",
            updatedAt = updatedAt
        )
    }

    fun isActive(context: Context): Boolean {
        return readStatus(context).active
    }

    fun readToken(context: Context): String {
        val token = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, DEFAULT_TOKEN)
            .orEmpty()
            .trim()

        return token.ifBlank { DEFAULT_TOKEN }
    }

    fun saveToken(
        context: Context,
        token: String
    ) {
        val cleanToken = token
            .trim()
            .replace(" ", "-")
            .take(64)
            .ifBlank { DEFAULT_TOKEN }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, cleanToken)
            .apply()
    }

    fun resetToken(context: Context) {
        saveToken(context, DEFAULT_TOKEN)
    }

    fun ageText(status: LocalControlStatus): String {
        if (status.updatedAt <= 0L) return "waiting"

        val seconds = ((System.currentTimeMillis() - status.updatedAt) / 1000L).coerceAtLeast(0L)

        return when {
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60}m ago"
            else -> "${seconds / 3600}h ago"
        }
    }

    private fun isStale(updatedAt: Long): Boolean {
        if (updatedAt <= 0L) return true
        return System.currentTimeMillis() - updatedAt > ACTIVE_STALE_AFTER_MS
    }
}
