package com.auralis.protect.data.recovery

import android.content.Context

/**
 * Small persistent runtime-state store for the protected phone.
 *
 * This is not a new command channel. It only remembers the latest known recovery/ring
 * state so the Compose dashboard restores correctly after reopen, rotation, or process refresh.
 */
data class RecoveryRuntimeState(
    val recoveryActive: Boolean,
    val ringActive: Boolean,
    val lastSource: String,
    val lastCommand: String,
    val detail: String,
    val updatedAt: Long
)

object RecoveryStateStore {
    private const val PREFS_NAME = "auralis_recovery_state_store"
    private const val KEY_RECOVERY_ACTIVE = "recovery_active"
    private const val KEY_RING_ACTIVE = "ring_active"
    private const val KEY_LAST_SOURCE = "last_source"
    private const val KEY_LAST_COMMAND = "last_command"
    private const val KEY_DETAIL = "detail"
    private const val KEY_UPDATED_AT = "updated_at"

    fun markRecovery(
        context: Context,
        active: Boolean,
        source: String,
        detail: String
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RECOVERY_ACTIVE, active)
            .putString(KEY_LAST_SOURCE, clean(source))
            .putString(KEY_LAST_COMMAND, if (active) "RECOVERY START" else "RECOVERY STOP")
            .putString(KEY_DETAIL, clean(detail))
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun markRing(
        context: Context,
        active: Boolean,
        source: String,
        detail: String
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RING_ACTIVE, active)
            .putString(KEY_LAST_SOURCE, clean(source))
            .putString(KEY_LAST_COMMAND, if (active) "RING START" else "RING STOP")
            .putString(KEY_DETAIL, clean(detail))
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun markHeartbeat(
        context: Context,
        detail: String = "Recovery heartbeat updated"
    ) {
        val state = read(context)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RECOVERY_ACTIVE, state.recoveryActive)
            .putBoolean(KEY_RING_ACTIVE, state.ringActive)
            .putString(KEY_LAST_SOURCE, state.lastSource)
            .putString(KEY_LAST_COMMAND, if (state.recoveryActive) "RECOVERY HEARTBEAT" else state.lastCommand)
            .putString(KEY_DETAIL, clean(detail))
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun clearVolatileRing(context: Context) {
        markRing(
            context = context,
            active = false,
            source = "SYSTEM",
            detail = "Ring state cleared when app/session stopped"
        )
    }

    fun read(context: Context): RecoveryRuntimeState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        return RecoveryRuntimeState(
            recoveryActive = prefs.getBoolean(KEY_RECOVERY_ACTIVE, false),
            ringActive = prefs.getBoolean(KEY_RING_ACTIVE, false),
            lastSource = prefs.getString(KEY_LAST_SOURCE, "SYSTEM") ?: "SYSTEM",
            lastCommand = prefs.getString(KEY_LAST_COMMAND, "READY") ?: "READY",
            detail = prefs.getString(KEY_DETAIL, "State memory ready") ?: "State memory ready",
            updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)
        )
    }

    fun ageText(state: RecoveryRuntimeState): String {
        if (state.updatedAt <= 0L) return "not synced yet"

        val seconds = ((System.currentTimeMillis() - state.updatedAt) / 1000L).coerceAtLeast(0L)

        return when {
            seconds < 5 -> "just now"
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86400 -> "${seconds / 3600}h ago"
            else -> "${seconds / 86400}d ago"
        }
    }

    private fun clean(value: String): String {
        return value
            .replace("|", "/")
            .replace("\n", " ")
            .replace("\r", " ")
            .trim()
            .take(180)
    }
}
