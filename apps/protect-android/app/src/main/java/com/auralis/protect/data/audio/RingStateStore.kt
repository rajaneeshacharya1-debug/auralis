package com.auralis.protect.data.audio

import android.content.Context

object RingStateStore {
    private const val PREFS_NAME = "auralis_ring_state"
    private const val KEY_RING_ACTIVE = "ring_active"

    fun setActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RING_ACTIVE, active)
            .apply()
    }

    fun isActive(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_RING_ACTIVE, false)
    }
}
