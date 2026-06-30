package com.auralis.protect.data.location

import android.content.Context
import android.location.Location

object LocationStore {
    private const val PREFS_NAME = "auralis_location_store"
    private const val KEY_HAS_LOCATION = "has_location"
    private const val KEY_LATITUDE = "latitude"
    private const val KEY_LONGITUDE = "longitude"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_ACCURACY = "accuracy"
    private const val KEY_RECOVERY_ACTIVE = "recovery_active"

    fun saveLatestLocation(
        context: Context,
        location: Location,
        providerOverride: String? = null
    ) {
        saveLatestLocation(
            context = context,
            latitude = location.latitude,
            longitude = location.longitude,
            provider = providerOverride ?: location.provider ?: "live",
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            timestamp = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
    }

    fun saveLatestLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
        provider: String
    ) {
        saveLatestLocation(
            context = context,
            latitude = latitude,
            longitude = longitude,
            provider = provider,
            accuracyMeters = null,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun saveLatestLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
        provider: String,
        accuracyMeters: Float?,
        timestamp: Long
    ) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAS_LOCATION, true)
            .putString(KEY_LATITUDE, latitude.toString())
            .putString(KEY_LONGITUDE, longitude.toString())
            .putString(KEY_PROVIDER, cleanProvider(provider))
            .putLong(KEY_UPDATED_AT, timestamp)

        if (accuracyMeters != null) {
            editor.putString(KEY_ACCURACY, accuracyMeters.toString())
        } else {
            editor.remove(KEY_ACCURACY)
        }

        editor.apply()
    }

    fun readLatestLocation(context: Context): LocationSnapshot? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (!prefs.getBoolean(KEY_HAS_LOCATION, false)) {
            return null
        }

        val latitude = prefs.getString(KEY_LATITUDE, null)?.toDoubleOrNull()
        val longitude = prefs.getString(KEY_LONGITUDE, null)?.toDoubleOrNull()
        val updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)
        val provider = prefs.getString(KEY_PROVIDER, "recovery service") ?: "recovery service"
        val accuracy = prefs.getString(KEY_ACCURACY, null)?.toFloatOrNull()

        if (latitude == null || longitude == null || updatedAt <= 0L) {
            return null
        }

        val freshness = LocationReader.freshnessFor(updatedAt)
        val accuracyText = accuracy?.let { " · ±${it.toInt()}m" }.orEmpty()

        return LocationSnapshot(
            value = LocationReader.labelFor(freshness),
            detail = "${LocationReader.formatCoordinate(latitude)}, ${LocationReader.formatCoordinate(longitude)} · ${LocationReader.ageText(updatedAt)} · $provider$accuracyText",
            latitude = latitude,
            longitude = longitude,
            hasPermission = true,
            updatedAt = updatedAt,
            source = provider,
            freshness = freshness,
            accuracyMeters = accuracy,
            provider = provider
        )
    }

    fun setRecoveryActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RECOVERY_ACTIVE, active)
            .apply()
    }

    fun isRecoveryActive(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_RECOVERY_ACTIVE, false)
    }

    private fun cleanProvider(provider: String): String {
        return provider
            .replace("\n", " ")
            .replace("\r", " ")
            .trim()
            .ifBlank { "recovery service" }
            .take(24)
    }
}
