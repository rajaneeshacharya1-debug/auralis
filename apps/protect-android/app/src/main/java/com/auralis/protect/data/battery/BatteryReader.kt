package com.auralis.protect.data.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object BatteryReader {
    fun readBatteryPercent(context: Context): Int {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return 0

        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        if (level < 0 || scale <= 0) return 0

        return ((level * 100f) / scale).toInt()
    }

    fun label(percent: Int): String {
        return when {
            percent >= 70 -> "strong"
            percent >= 40 -> "stable"
            percent >= 20 -> "caution"
            percent > 0 -> "critical"
            else -> "unknown"
        }
    }
}
