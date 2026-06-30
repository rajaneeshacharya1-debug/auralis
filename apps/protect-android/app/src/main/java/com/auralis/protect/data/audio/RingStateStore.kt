package com.auralis.protect.data.audio

import android.content.Context

object RingStateStore {
    private const val PREFS_NAME = "auralis_ring_state"
    private const val KEY_RING_ACTIVE = "ring_active"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val START_GRACE_MS = 10_000L

    fun setActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RING_ACTIVE, active)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun isActive(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_RING_ACTIVE, false)
    }

    fun isWithinStartGrace(context: Context): Boolean {
        val updatedAt = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_UPDATED_AT, 0L)

        if (updatedAt <= 0L) return false
        return System.currentTimeMillis() - updatedAt <= START_GRACE_MS
    }
}
