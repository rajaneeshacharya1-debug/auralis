package com.auralis.protect.domain.usecase

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.auralis.protect.data.audio.RingStateStore
import com.auralis.protect.data.battery.BatteryReader
import com.auralis.protect.data.evidence.EvidenceTimeline
import com.auralis.protect.data.location.LocationReader
import com.auralis.protect.data.location.LocationStore
import com.auralis.protect.data.location.LocationTrailStore
import com.auralis.protect.data.logs.EventLogStore
import com.auralis.protect.data.network.DeviceAddressReader
import com.auralis.protect.data.network.NetworkReader
import com.auralis.protect.data.recovery.RecoveryStateStore
import com.auralis.protect.domain.model.AuralisCommand
import com.auralis.protect.domain.model.CommandResult
import com.auralis.protect.domain.model.CommandSource
import com.auralis.protect.service.RecoveryForegroundService

object CommandEngine {
    private var activeRingtone: Ringtone? = null

    fun execute(
        context: Context,
        source: CommandSource,
        command: AuralisCommand,
        logEvent: Boolean = true
    ): CommandResult {
        val result = when (command) {
            AuralisCommand.BOOT -> boot(context, source)
            AuralisCommand.STOP -> stop(context, source)
            AuralisCommand.RING -> ring(context, source)
            AuralisCommand.RING_STOP -> ringStop(context, source)
            AuralisCommand.STATUS -> status(context, source)
            AuralisCommand.SNAPSHOT -> snapshot(context, source)
            AuralisCommand.REPORT -> report(context, source)
            AuralisCommand.PING -> ping(context, source)
        }

        if (logEvent) {
            EventLogStore.append(
                context = context,
                channel = result.source.label,
                command = result.command.label,
                detail = result.detail
            )
        }

        return result
    }

    fun reconcileRuntimeState(context: Context) {
        val state = RecoveryStateStore.read(context)
        val ringStoreActive = RingStateStore.isActive(context)
        val ringtonePlaying = activeRingtone?.isPlaying == true

        when {
            ringtonePlaying && (!state.ringActive || !ringStoreActive) -> {
                RingStateStore.setActive(context, true)
                RecoveryStateStore.markRing(
                    context = context,
                    active = true,
                    source = "SYSTEM",
                    detail = "Ring state reconciled from active ringtone"
                )
            }

            (state.ringActive || ringStoreActive) &&
                !ringtonePlaying &&
                !RingStateStore.isWithinStartGrace(context) -> {
                RingStateStore.setActive(context, false)
                RecoveryStateStore.markRing(
                    context = context,
                    active = false,
                    source = "SYSTEM",
                    detail = "Ring state cleared after app/service restart"
                )
            }
        }
    }

    fun statusText(
        context: Context,
        trailSource: String = "RECOVERY_STATUS"
    ): String {
        reconcileRuntimeState(context)
        val battery = BatteryReader.readBatteryPercent(context)
        val network = NetworkReader.read(context)
        val location = LocationReader.readLastKnownLocation(context, trailSource = trailSource)
        val state = RecoveryStateStore.read(context)
        val recovery = if (state.recoveryActive) "ON" else "OFF"
        val ring = if (state.ringActive) "ON" else "OFF"
        val ip = DeviceAddressReader.primaryAddress()
        val trailSummary = LocationTrailStore.summaryText(context)

        return """
            AURALIS STATUS
            IP: $ip
            Recovery: $recovery
            Ring: $ring
            State memory: ${state.lastCommand} - ${RecoveryStateStore.ageText(state)}
            Battery: $battery%
            Network: ${network.value} - ${network.detail}
            Location: ${location.value} - ${location.detail}
            Maps: ${mapUrl(location.latitude, location.longitude)}
            $trailSummary
        """.trimIndent()
    }

    fun snapshotText(
        context: Context,
        trailSource: String = "SNAPSHOT"
    ): String {
        reconcileRuntimeState(context)
        val battery = BatteryReader.readBatteryPercent(context)
        val network = NetworkReader.read(context)
        val location = LocationReader.readLastKnownLocation(context, trailSource = trailSource)
        val state = RecoveryStateStore.read(context)
        val recovery = if (state.recoveryActive) "ACTIVE" else "OFF"
        val ring = if (state.ringActive) "ON" else "OFF"
        val ip = DeviceAddressReader.primaryAddress()
        val trailSummary = LocationTrailStore.summaryText(context)

        return """
            AURALIS RECOVERY SNAPSHOT
            Recovery: $recovery
            Ring: $ring
            Battery: $battery%
            Network: ${network.value} - ${network.detail}
            Location: ${location.value} - ${location.detail}
            Latitude: ${LocationReader.formatCoordinate(location.latitude)}
            Longitude: ${LocationReader.formatCoordinate(location.longitude)}
            Maps: ${mapUrl(location.latitude, location.longitude)}
            Local IP: $ip
            State memory: ${state.lastCommand} - ${RecoveryStateStore.ageText(state)}
            $trailSummary
        """.trimIndent()
    }

    fun evidenceReportText(context: Context): String {
        return EvidenceTimeline.reportText(context)
    }

    private fun mapUrl(latitude: Double?, longitude: Double?): String {
        return if (latitude != null && longitude != null) {
            "https://maps.google.com/?q=$latitude,$longitude"
        } else {
            "unavailable"
        }
    }

    private fun boot(
        context: Context,
        source: CommandSource
    ): CommandResult {
        return try {
            LocationStore.setRecoveryActive(context, true)
            RecoveryStateStore.markRecovery(
                context = context,
                active = true,
                source = source.label,
                detail = "Recovery service start requested through ${source.label}"
            )

            val serviceIntent = Intent(context, RecoveryForegroundService::class.java).apply {
                action = RecoveryForegroundService.ACTION_START
            }

            ContextCompat.startForegroundService(context, serviceIntent)

            CommandResult(
                success = true,
                command = AuralisCommand.BOOT,
                source = source,
                publicMessage = "AURALIS: BOOT accepted. Recovery mode is starting.",
                detail = "Recovery service start requested through ${source.label}"
            )
        } catch (error: Exception) {
            LocationStore.setRecoveryActive(context, false)
            RecoveryStateStore.markRecovery(
                context = context,
                active = false,
                source = source.label,
                detail = error.message ?: "Recovery service start failed"
            )

            CommandResult(
                success = false,
                command = AuralisCommand.BOOT,
                source = source,
                publicMessage = "AURALIS: BOOT failed. Open Auralis Protect and check permissions.",
                detail = error.message ?: "Recovery service start failed"
            )
        }
    }

    private fun stop(
        context: Context,
        source: CommandSource
    ): CommandResult {
        return try {
            LocationStore.setRecoveryActive(context, false)
            stopRingtoneSilently(context)
            RecoveryStateStore.markRecovery(
                context = context,
                active = false,
                source = source.label,
                detail = "Recovery service stop requested through ${source.label}"
            )

            val serviceIntent = Intent(context, RecoveryForegroundService::class.java).apply {
                action = RecoveryForegroundService.ACTION_STOP
            }

            context.startService(serviceIntent)

            CommandResult(
                success = true,
                command = AuralisCommand.STOP,
                source = source,
                publicMessage = "AURALIS: STOP accepted. Recovery mode is stopping.",
                detail = "Recovery service stop and ring cleanup requested through ${source.label}"
            )
        } catch (error: Exception) {
            CommandResult(
                success = false,
                command = AuralisCommand.STOP,
                source = source,
                publicMessage = "AURALIS: STOP failed. Recovery service could not be contacted.",
                detail = error.message ?: "Recovery service stop failed"
            )
        }
    }

    private fun ring(
        context: Context,
        source: CommandSource
    ): CommandResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            activeRingtone?.stop()
            activeRingtone = RingtoneManager.getRingtone(context.applicationContext, alarmUri)
            activeRingtone?.play()
            RingStateStore.setActive(context, true)
            RecoveryStateStore.markRing(
                context = context,
                active = true,
                source = source.label,
                detail = "Ring command triggered through ${source.label}"
            )

            CommandResult(
                success = true,
                command = AuralisCommand.RING,
                source = source,
                publicMessage = "AURALIS: RING accepted. Phone should ring.",
                detail = "Ring command triggered through ${source.label}"
            )
        } catch (error: Exception) {
            RingStateStore.setActive(context, false)
            RecoveryStateStore.markRing(
                context = context,
                active = false,
                source = source.label,
                detail = error.message ?: "Ring command could not start"
            )

            CommandResult(
                success = false,
                command = AuralisCommand.RING,
                source = source,
                publicMessage = "AURALIS: RING failed.",
                detail = error.message ?: "Ring command could not start"
            )
        }
    }

    private fun ringStop(
        context: Context,
        source: CommandSource
    ): CommandResult {
        return try {
            activeRingtone?.stop()
            activeRingtone = null
            RingStateStore.setActive(context, false)
            RecoveryStateStore.markRing(
                context = context,
                active = false,
                source = source.label,
                detail = "Ring stopped through ${source.label}"
            )

            CommandResult(
                success = true,
                command = AuralisCommand.RING_STOP,
                source = source,
                publicMessage = "AURALIS: RING stopped.",
                detail = "Ring stopped through ${source.label}"
            )
        } catch (error: Exception) {
            CommandResult(
                success = false,
                command = AuralisCommand.RING_STOP,
                source = source,
                publicMessage = "AURALIS: RING STOP failed.",
                detail = error.message ?: "Ring stop command failed"
            )
        }
    }

    private fun stopRingtoneSilently(context: Context) {
        try {
            activeRingtone?.stop()
            activeRingtone = null
        } catch (_: Exception) {
            // Ignore cleanup failure.
        }
        RingStateStore.setActive(context, false)
        RecoveryStateStore.markRing(
            context = context,
            active = false,
            source = "SYSTEM",
            detail = "Ring state cleared during recovery stop"
        )
    }

    private fun status(
        context: Context,
        source: CommandSource
    ): CommandResult {
        return CommandResult(
            success = true,
            command = AuralisCommand.STATUS,
            source = source,
            publicMessage = statusText(context, trailSource = "${source.label}_STATUS"),
            detail = "Status requested through ${source.label}"
        )
    }

    private fun snapshot(
        context: Context,
        source: CommandSource
    ): CommandResult {
        return CommandResult(
            success = true,
            command = AuralisCommand.SNAPSHOT,
            source = source,
            publicMessage = snapshotText(context, trailSource = "SNAPSHOT"),
            detail = "Recovery snapshot generated through ${source.label}"
        )
    }

    private fun report(
        context: Context,
        source: CommandSource
    ): CommandResult {
        return CommandResult(
            success = true,
            command = AuralisCommand.REPORT,
            source = source,
            publicMessage = evidenceReportText(context),
            detail = "Evidence report generated through ${source.label}"
        )
    }

    private fun ping(
        context: Context,
        source: CommandSource
    ): CommandResult {
        val ip = DeviceAddressReader.primaryAddress()

        return CommandResult(
            success = true,
            command = AuralisCommand.PING,
            source = source,
            publicMessage = "AURALIS LOCAL READY\nIP: $ip",
            detail = "Ping requested through ${source.label}"
        )
    }
}
