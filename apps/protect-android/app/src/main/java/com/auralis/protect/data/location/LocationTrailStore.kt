package com.auralis.protect.data.location

import android.content.Context
import android.location.Location
import com.auralis.protect.data.logs.EventLogStore
import com.auralis.protect.data.recovery.RecoveryStateStore
import kotlin.math.roundToLong

data class LocationTrailPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val timestamp: Long,
    val source: String
)

object LocationTrailStore {
    private const val PREFS_NAME = "auralis_location_trail_store"
    private const val KEY_POINTS = "points"
    private const val MAX_POINTS = 100
    private const val DUPLICATE_WINDOW_MS = 15_000L
    private const val COORDINATE_SCALE = 100_000.0

    fun recordIfRecoveryActive(
        context: Context,
        location: Location,
        source: String
    ): Boolean {
        return recordIfRecoveryActive(
            context = context,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            timestamp = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
            source = source
        )
    }

    fun recordIfRecoveryActive(
        context: Context,
        snapshot: LocationSnapshot,
        source: String
    ): Boolean {
        val latitude = snapshot.latitude ?: return false
        val longitude = snapshot.longitude ?: return false

        return recordIfRecoveryActive(
            context = context,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = snapshot.accuracyMeters,
            timestamp = snapshot.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            source = source
        )
    }

    fun recordIfRecoveryActive(
        context: Context,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float?,
        timestamp: Long,
        source: String
    ): Boolean {
        if (!isRecoveryActive(context)) return false

        val point = LocationTrailPoint(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            timestamp = timestamp.takeIf { it > 0L } ?: System.currentTimeMillis(),
            source = cleanSource(source)
        )

        val current = readPoints(context)
        if (current.firstOrNull()?.let { isDuplicate(point, it) } == true) {
            return false
        }

        val updated = (listOf(point) + current).take(MAX_POINTS)
        savePoints(context, updated)

        EventLogStore.append(
            context = context,
            channel = "TRAIL",
            command = "POINT SAVED",
            detail = "${point.source} - ${updated.size} trail points"
        )

        return true
    }

    fun readPoints(context: Context): List<LocationTrailPoint> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_POINTS, "")
            .orEmpty()

        if (raw.isBlank()) return emptyList()

        return raw
            .lineSequence()
            .mapNotNull { row ->
                val parts = row.split("|", limit = 5)
                if (parts.size != 5) return@mapNotNull null

                val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
                val latitude = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                val longitude = parts[2].toDoubleOrNull() ?: return@mapNotNull null
                val accuracy = parts[3].takeIf { it != "-" }?.toFloatOrNull()
                val source = parts[4].ifBlank { "UNKNOWN" }

                LocationTrailPoint(
                    latitude = latitude,
                    longitude = longitude,
                    accuracyMeters = accuracy,
                    timestamp = timestamp,
                    source = source
                )
            }
            .filter { it.timestamp > 0L }
            .toList()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_POINTS)
            .apply()

        EventLogStore.append(
            context = context,
            channel = "TRAIL",
            command = "TRAIL CLEARED",
            detail = "Local recovery location trail cleared"
        )
    }

    fun summaryText(context: Context): String {
        val points = readPoints(context)
        val latest = points.firstOrNull()

        return if (latest == null) {
            "Location trail: no recovery trail points recorded"
        } else {
            "Location trail: ${points.size} points, latest ${formatPoint(latest)}"
        }
    }

    fun recentPointsText(
        context: Context,
        limit: Int = 8
    ): String {
        val points = readPoints(context).take(limit)
        if (points.isEmpty()) return "No recovery location trail points recorded."

        return points.joinToString(separator = "\n") { point ->
            "${ageText(point.timestamp)} | ${point.source} | ${formatCoordinate(point.latitude)}, ${formatCoordinate(point.longitude)}${accuracyText(point.accuracyMeters)}"
        }
    }

    fun formatPoint(point: LocationTrailPoint): String {
        return "${formatCoordinate(point.latitude)}, ${formatCoordinate(point.longitude)} - ${ageText(point.timestamp)} - ${point.source}${accuracyText(point.accuracyMeters)}"
    }

    fun ageText(timestamp: Long): String {
        if (timestamp <= 0L) return "not timed"

        val seconds = ((System.currentTimeMillis() - timestamp) / 1000L).coerceAtLeast(0L)
        return when {
            seconds < 5 -> "just now"
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86400 -> "${seconds / 3600}h ago"
            else -> "${seconds / 86400}d ago"
        }
    }

    private fun savePoints(
        context: Context,
        points: List<LocationTrailPoint>
    ) {
        val raw = points.joinToString(separator = "\n") { point ->
            listOf(
                point.timestamp.toString(),
                point.latitude.toString(),
                point.longitude.toString(),
                point.accuracyMeters?.toString() ?: "-",
                cleanSource(point.source)
            ).joinToString(separator = "|")
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_POINTS, raw)
            .apply()
    }

    private fun isRecoveryActive(context: Context): Boolean {
        return RecoveryStateStore.read(context).recoveryActive ||
            LocationStore.isRecoveryActive(context)
    }

    private fun isDuplicate(
        next: LocationTrailPoint,
        latest: LocationTrailPoint
    ): Boolean {
        val closeInTime = kotlin.math.abs(next.timestamp - latest.timestamp) <= DUPLICATE_WINDOW_MS
        val sameRoundedLocation = rounded(next.latitude) == rounded(latest.latitude) &&
            rounded(next.longitude) == rounded(latest.longitude)

        return closeInTime && sameRoundedLocation
    }

    private fun rounded(value: Double): Long {
        return (value * COORDINATE_SCALE).roundToLong()
    }

    private fun formatCoordinate(value: Double): String {
        return "%.5f".format(value)
    }

    private fun accuracyText(value: Float?): String {
        return value?.let { " +/-${it.toInt()}m" }.orEmpty()
    }

    private fun cleanSource(source: String): String {
        return source
            .replace("|", "/")
            .replace("\n", " ")
            .replace("\r", " ")
            .trim()
            .uppercase()
            .ifBlank { "UNKNOWN" }
            .take(32)
    }
}
