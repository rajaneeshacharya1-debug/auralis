package com.auralis.protect.data.evidence

import android.content.Context
import com.auralis.protect.data.audio.RingStateStore
import com.auralis.protect.data.localcontrol.LocalControlStatus
import com.auralis.protect.data.localcontrol.LocalControlStore
import com.auralis.protect.data.location.LocationReader
import com.auralis.protect.data.location.LocationFreshness
import com.auralis.protect.data.location.LocationSnapshot
import com.auralis.protect.data.location.LocationStore
import com.auralis.protect.data.location.LocationTrailPoint
import com.auralis.protect.data.location.LocationTrailStore
import com.auralis.protect.data.logs.EventLogEntry
import com.auralis.protect.data.logs.EventLogStore
import com.auralis.protect.data.recovery.RecoveryRuntimeState
import com.auralis.protect.data.recovery.RecoveryStateStore
import com.auralis.protect.data.settings.TrustedSenderStore
import com.auralis.protect.data.sms.SmsCommandStatus
import com.auralis.protect.data.sms.SmsCommandStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EvidenceTimelineItem(
    val timestamp: Long,
    val category: String,
    val title: String,
    val detail: String
)

data class EvidenceTimelineSnapshot(
    val generatedAt: String,
    val recoveryState: RecoveryRuntimeState,
    val ringActive: Boolean,
    val localControlStatus: LocalControlStatus,
    val latestLocation: LocationSnapshot,
    val trailPoints: List<LocationTrailPoint>,
    val eventLogs: List<EventLogEntry>,
    val smsStatus: SmsCommandStatus,
    val trustedSenderMasked: String,
    val timeline: List<EvidenceTimelineItem>
)

object EvidenceTimeline {
    private const val RECENT_LIMIT = 10

    fun snapshot(
        context: Context,
        recordLocationRead: Boolean = false
    ): EvidenceTimelineSnapshot {
        val recoveryState = RecoveryStateStore.read(context)
        val ringActive = recoveryState.ringActive || RingStateStore.isActive(context)
        val localStatus = LocalControlStore.readStatus(context)
        val latestLocation = if (recordLocationRead) {
            LocationReader.readLastKnownLocation(context, trailSource = "REPORT")
        } else {
            LocationStore.readLatestLocation(context) ?: LocationSnapshot(
                value = if (LocationReader.hasLocationPermission(context)) "Waiting" else "Permission",
                detail = if (LocationReader.hasLocationPermission(context)) "no saved location yet" else "location access needed",
                latitude = null,
                longitude = null,
                hasPermission = LocationReader.hasLocationPermission(context),
                freshness = if (LocationReader.hasLocationPermission(context)) LocationFreshness.Waiting else LocationFreshness.Permission
            )
        }
        val trailPoints = LocationTrailStore.readPoints(context)
        val eventLogs = EventLogStore.readEvents(context)
        val smsStatus = SmsCommandStore.readStatus(context)
        val trustedSenderMasked = TrustedSenderStore.mask(TrustedSenderStore.readTrustedSender(context))
        val timeline = buildTimeline(eventLogs, trailPoints)

        return EvidenceTimelineSnapshot(
            generatedAt = timestampText(System.currentTimeMillis()),
            recoveryState = recoveryState,
            ringActive = ringActive,
            localControlStatus = localStatus,
            latestLocation = latestLocation,
            trailPoints = trailPoints,
            eventLogs = eventLogs,
            smsStatus = smsStatus,
            trustedSenderMasked = trustedSenderMasked,
            timeline = timeline
        )
    }

    fun reportText(context: Context): String {
        return reportText(snapshot(context, recordLocationRead = true))
    }

    fun reportText(snapshot: EvidenceTimelineSnapshot): String {
        val recovery = if (snapshot.recoveryState.recoveryActive) "ACTIVE" else "INACTIVE"
        val ring = if (snapshot.ringActive) "ACTIVE" else "OFF"
        val localControl = if (snapshot.localControlStatus.active) "ONLINE" else "OFFLINE"
        val location = snapshot.latestLocation
        val latestLocationText = if (location.hasCoordinates) {
            "${LocationReader.formatCoordinate(location.latitude)}, ${LocationReader.formatCoordinate(location.longitude)} - ${location.freshnessLabel} - ${location.detail}"
        } else {
            "${location.value} - ${location.detail}"
        }
        val latestTrail = snapshot.trailPoints.firstOrNull()?.let {
            LocationTrailStore.formatPoint(it)
        } ?: "No trail point recorded"
        val recentTrail = if (snapshot.trailPoints.isEmpty()) {
            "No recovery location trail points recorded."
        } else {
            snapshot.trailPoints.take(RECENT_LIMIT).joinToString(separator = "\n") { point ->
                "${LocationTrailStore.ageText(point.timestamp)} | ${point.source} | ${LocationReader.formatCoordinate(point.latitude)}, ${LocationReader.formatCoordinate(point.longitude)}${accuracyText(point.accuracyMeters)}"
            }
        }
        val recentEvents = if (snapshot.eventLogs.isEmpty()) {
            "No command timeline entries recorded yet."
        } else {
            snapshot.eventLogs.take(RECENT_LIMIT).joinToString(separator = "\n") { entry ->
                "${EventLogStore.ageText(entry)} | ${entry.channel} | ${entry.command} | ${entry.detail}"
            }
        }
        val combinedTimeline = if (snapshot.timeline.isEmpty()) {
            "No evidence timeline entries recorded yet."
        } else {
            snapshot.timeline.take(RECENT_LIMIT).joinToString(separator = "\n") { item ->
                "${ageText(item.timestamp)} | ${item.category} | ${item.title} | ${item.detail}"
            }
        }

        return """
            AURALIS EVIDENCE REPORT
            Generated: ${snapshot.generatedAt}

            RECOVERY STATUS
            Recovery: $recovery
            Ring: $ring
            Local control: $localControl - ${snapshot.localControlStatus.lastCommand} - ${LocalControlStore.ageText(snapshot.localControlStatus)}
            State memory: ${snapshot.recoveryState.lastCommand} - ${RecoveryStateStore.ageText(snapshot.recoveryState)}

            LATEST LOCATION
            $latestLocationText
            Maps: ${location.mapsUrl ?: "unavailable"}

            LOCATION TRAIL
            Count: ${snapshot.trailPoints.size}
            Latest: $latestTrail

            RECENT TRAIL POINTS
            $recentTrail

            SMS EMERGENCY SUMMARY
            Trusted sender: ${snapshot.trustedSenderMasked}
            Last SMS event: ${snapshot.smsStatus.lastCommand} - ${SmsCommandStore.ageText(snapshot.smsStatus)}
            Detail: ${snapshot.smsStatus.lastDetail}
            Commands: #AURALIS-BOOT-4729, #AURALIS-STOP-4729

            RECENT EVENT LOG
            $recentEvents

            COMBINED EVIDENCE TIMELINE
            $combinedTimeline
        """.trimIndent()
    }

    private fun buildTimeline(
        eventLogs: List<EventLogEntry>,
        trailPoints: List<LocationTrailPoint>
    ): List<EvidenceTimelineItem> {
        val eventItems = eventLogs.map { entry ->
            EvidenceTimelineItem(
                timestamp = entry.timestamp,
                category = entry.channel,
                title = entry.command,
                detail = entry.detail
            )
        }

        val trailItems = trailPoints.map { point ->
            EvidenceTimelineItem(
                timestamp = point.timestamp,
                category = "TRAIL",
                title = point.source,
                detail = "${LocationReader.formatCoordinate(point.latitude)}, ${LocationReader.formatCoordinate(point.longitude)}${accuracyText(point.accuracyMeters)}"
            )
        }

        return (eventItems + trailItems)
            .filter { it.timestamp > 0L }
            .sortedByDescending { it.timestamp }
    }

    private fun timestampText(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))
    }

    private fun ageText(timestamp: Long): String {
        if (timestamp <= 0L) return "not timed"
        val seconds = ((System.currentTimeMillis() - timestamp) / 1000L).coerceAtLeast(0L)
        return when {
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86400 -> "${seconds / 3600}h ago"
            else -> "${seconds / 86400}d ago"
        }
    }

    private fun accuracyText(value: Float?): String {
        return value?.let { " +/-${it.toInt()}m" }.orEmpty()
    }
}
