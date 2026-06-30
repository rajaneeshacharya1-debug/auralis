package com.auralis.protect.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat

enum class LocationFreshness {
    Fresh,
    Recent,
    Stale,
    Waiting,
    Permission,
    Unavailable
}

// Snapshot shown in the app, sent over SMS, and returned by the local controller.
data class LocationSnapshot(
    val value: String,
    val detail: String,
    val latitude: Double?,
    val longitude: Double?,
    val hasPermission: Boolean,
    val updatedAt: Long = 0L,
    val source: String = "unknown",
    val freshness: LocationFreshness = LocationFreshness.Unavailable,
    val accuracyMeters: Float? = null,
    val provider: String? = source
) {
    val hasCoordinates: Boolean
        get() = latitude != null && longitude != null

    val mapsUrl: String?
        get() = if (latitude != null && longitude != null) {
            "https://maps.google.com/?q=$latitude,$longitude"
        } else {
            null
        }

    val updatedAtMillis: Long?
        get() = updatedAt.takeIf { it > 0L }

    val ageSeconds: Long?
        get() = updatedAt.takeIf { it > 0L }?.let {
            ((System.currentTimeMillis() - it) / 1000L).coerceAtLeast(0L)
        }

    val freshnessLabel: String
        get() = when (freshness) {
            LocationFreshness.Fresh -> "fresh"
            LocationFreshness.Recent -> "recent"
            LocationFreshness.Stale -> "stale"
            LocationFreshness.Waiting -> "waiting"
            LocationFreshness.Permission -> "permission needed"
            LocationFreshness.Unavailable -> "unavailable"
        }
}

object LocationReader {
    private const val FRESH_SECONDS = 60L
    private const val RECENT_SECONDS = 10 * 60L

    private val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    )

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    fun readLastKnownLocation(context: Context): LocationSnapshot {
        if (!hasLocationPermission(context)) {
            return LocationSnapshot(
                value = "Permission",
                detail = "location access needed",
                latitude = null,
                longitude = null,
                hasPermission = false,
                freshness = LocationFreshness.Permission
            )
        }

        if (LocationStore.isRecoveryActive(context)) {
            publishBestAvailableLocation(context, providerOverride = "dashboard")
        }

        LocationStore.readLatestLocation(context)?.let { liveSnapshot ->
            return liveSnapshot
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val bestLocation = bestLastKnownLocation(locationManager)

        return if (bestLocation != null) {
            LocationStore.saveLatestLocation(
                context = context,
                location = bestLocation,
                providerOverride = bestLocation.provider ?: "last-known"
            )

            LocationStore.readLatestLocation(context) ?: snapshotFromLocation(bestLocation)
        } else {
            LocationSnapshot(
                value = "Waiting",
                detail = "start recovery or open maps to get first fix",
                latitude = null,
                longitude = null,
                hasPermission = true,
                freshness = LocationFreshness.Waiting
            )
        }
    }

    fun refreshNow(context: Context): LocationSnapshot {
        if (!hasLocationPermission(context)) {
            return readLastKnownLocation(context)
        }

        val saved = publishBestAvailableLocation(context, providerOverride = "manual refresh")
        if (saved) {
            return LocationStore.readLatestLocation(context) ?: readLastKnownLocation(context)
        }

        requestSingleProviderUpdate(context)
        return readLastKnownLocation(context)
    }

    fun publishBestAvailableLocation(
        context: Context,
        providerOverride: String? = null
    ): Boolean {
        if (!hasLocationPermission(context)) return false

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val bestLocation = bestLastKnownLocation(locationManager) ?: return false
        LocationStore.saveLatestLocation(
            context = context,
            location = bestLocation,
            providerOverride = providerOverride ?: bestLocation.provider ?: "provider"
        )
        return true
    }

    fun requestSingleProviderUpdate(context: Context) {
        if (!hasLocationPermission(context)) return

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    val selfRemovingListener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            LocationStore.saveLatestLocation(
                                context = context.applicationContext,
                                location = location,
                                providerOverride = location.provider ?: "single update"
                            )
                            try {
                                locationManager.removeUpdates(this)
                            } catch (_: Exception) {
                                // Ignore cleanup failure.
                            }
                        }

                        @Deprecated("Deprecated in Android platform")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) = Unit
                    }
                    locationManager.requestSingleUpdate(provider, selfRemovingListener, Looper.getMainLooper())
                    return
                }
            } catch (_: SecurityException) {
                return
            } catch (_: Exception) {
                // Try the next provider.
            }
        }
    }

    fun bestKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return bestLastKnownLocation(locationManager)
    }

    fun bestLastKnownLocation(locationManager: LocationManager): Location? {
        return providers
            .mapNotNull { provider -> safeLastKnownLocation(locationManager, provider) }
            .maxWithOrNull(
                compareBy<Location> { it.time }
                    .thenBy { if (it.hasAccuracy()) -it.accuracy else Float.NEGATIVE_INFINITY }
            )
    }

    private fun safeLastKnownLocation(
        locationManager: LocationManager,
        provider: String
    ): Location? {
        return try {
            if (locationManager.isProviderEnabled(provider)) {
                locationManager.getLastKnownLocation(provider)
            } else {
                null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun freshnessFor(updatedAt: Long): LocationFreshness {
        if (updatedAt <= 0L) return LocationFreshness.Unavailable
        val seconds = ((System.currentTimeMillis() - updatedAt) / 1000L).coerceAtLeast(0L)
        return when {
            seconds <= FRESH_SECONDS -> LocationFreshness.Fresh
            seconds <= RECENT_SECONDS -> LocationFreshness.Recent
            else -> LocationFreshness.Stale
        }
    }

    fun labelFor(freshness: LocationFreshness): String {
        return when (freshness) {
            LocationFreshness.Fresh -> "Fresh"
            LocationFreshness.Recent -> "Recent"
            LocationFreshness.Stale -> "Stale"
            LocationFreshness.Waiting -> "Waiting"
            LocationFreshness.Permission -> "Permission"
            LocationFreshness.Unavailable -> "Unavailable"
        }
    }

    fun ageText(updatedAt: Long): String {
        if (updatedAt <= 0L) return "not fixed yet"
        val seconds = ((System.currentTimeMillis() - updatedAt) / 1000L).coerceAtLeast(0L)
        return when {
            seconds < 5 -> "just now"
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86400 -> "${seconds / 3600}h ago"
            else -> "${seconds / 86400}d ago"
        }
    }

    fun formatCoordinate(value: Double?): String {
        return value?.let { "%.5f".format(it) } ?: "--"
    }

    fun shortMapsLine(snapshot: LocationSnapshot): String {
        return snapshot.mapsUrl ?: "Maps link unavailable"
    }

    private fun snapshotFromLocation(location: Location): LocationSnapshot {
        val provider = cleanProvider(location.provider)
        val freshness = freshnessFor(location.time)
        return LocationSnapshot(
            value = labelFor(freshness),
            detail = "${formatCoordinate(location.latitude)}, ${formatCoordinate(location.longitude)} · ${ageText(location.time)} · $provider",
            latitude = location.latitude,
            longitude = location.longitude,
            hasPermission = true,
            updatedAt = location.time,
            source = provider,
            freshness = freshness,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            provider = provider
        )
    }

    private fun cleanProvider(provider: String?): String {
        return when (provider?.lowercase()) {
            LocationManager.GPS_PROVIDER -> "gps"
            LocationManager.NETWORK_PROVIDER -> "network"
            LocationManager.PASSIVE_PROVIDER -> "passive"
            null -> "unknown"
            else -> provider.lowercase().take(18)
        }
    }
}
