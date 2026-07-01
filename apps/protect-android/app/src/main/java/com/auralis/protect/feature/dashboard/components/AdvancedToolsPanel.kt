package com.auralis.protect.feature.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auralis.protect.data.evidence.EvidenceTimelineSnapshot
import com.auralis.protect.data.localcontrol.LocalControlStore
import com.auralis.protect.data.location.LocationSnapshot
import com.auralis.protect.data.location.LocationTrailStore
import com.auralis.protect.data.logs.EventLogStore
import com.auralis.protect.data.recovery.RecoveryStateStore
import com.auralis.protect.data.sms.SmsCommandStore
import com.auralis.protect.design.components.AuralisActionButton
import com.auralis.protect.design.components.AuralisButtonVariant
import com.auralis.protect.design.theme.AuralisColors
import com.auralis.protect.domain.model.CommandChannelStatus

@Composable
fun AdvancedToolsPanel(
    localControlActive: Boolean,
    primaryIpAddress: String,
    localToken: String,
    draftLocalToken: String,
    onDraftLocalTokenChanged: (String) -> Unit,
    onSaveLocalToken: () -> Unit,
    onResetLocalToken: () -> Unit,
    recoveryActive: Boolean,
    locationSnapshot: LocationSnapshot,
    eventCount: Int,
    channels: List<CommandChannelStatus>,
    evidenceSnapshot: EvidenceTimelineSnapshot,
    onCopyEvidenceReport: () -> Unit,
    onShareControllerLink: () -> Unit,
    onShareLiveBeaconLink: () -> Unit,
    onShareRecoverySnapshot: () -> Unit,
    onShareEvidenceReport: () -> Unit
) {
    var advancedOpen by remember { mutableStateOf(false) }

    AuralisCard {
        BigTitle("Advanced tools")
        BodyText("Token settings, share tools, live view, and diagnostics.")

        AuralisActionButton(
            text = if (advancedOpen) "Hide advanced tools" else "Show advanced tools",
            onClick = { advancedOpen = !advancedOpen },
            variant = AuralisButtonVariant.Neutral,
            modifier = Modifier.fillMaxWidth()
        )

        if (advancedOpen) {
            DividerLine()

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Local token")
                AuralisInputField(
                    label = "Local control token",
                    value = draftLocalToken,
                    onValueChange = onDraftLocalTokenChanged
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AuralisActionButton(
                        text = "Save token",
                        onClick = onSaveLocalToken,
                        variant = AuralisButtonVariant.Secondary,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                    AuralisActionButton(
                        text = "Reset token",
                        onClick = onResetLocalToken,
                        variant = AuralisButtonVariant.Neutral,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            DividerLine()

            val hasNetworkAddress = primaryIpAddress != "unavailable"
            val controllerReady = localControlActive && hasNetworkAddress

            SoftPanel {
                SectionLabel("Share local controller")
                Text(
                    text = if (controllerReady) "Ready" else if (hasNetworkAddress) "Start local control first" else "Network needed",
                    color = if (controllerReady) AuralisColors.Success else AuralisColors.Warning,
                    fontWeight = FontWeight.Bold
                )
                AuralisActionButton(
                    text = "Share controller link",
                    onClick = onShareControllerLink,
                    enabled = hasNetworkAddress,
                    variant = AuralisButtonVariant.Secondary,
                    compact = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SoftPanel {
                SectionLabel("Live local view")
                Text(
                    text = if (controllerReady) "Ready" else if (hasNetworkAddress) "Start local control first" else "Network needed",
                    color = if (controllerReady) AuralisColors.Success else AuralisColors.Warning,
                    fontWeight = FontWeight.Bold
                )
                AuralisActionButton(
                    text = "Share live view",
                    onClick = onShareLiveBeaconLink,
                    enabled = controllerReady,
                    variant = AuralisButtonVariant.Secondary,
                    compact = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val snapshotReady = locationSnapshot.hasCoordinates

            SoftPanel {
                SectionLabel("Recovery snapshot")
                Text(
                    text = if (snapshotReady) "Map link ready" else if (recoveryActive) "Waiting for location" else "Start recovery first",
                    color = if (snapshotReady) AuralisColors.Success else AuralisColors.Warning,
                    fontWeight = FontWeight.Bold
                )
                AuralisActionButton(
                    text = "Share snapshot",
                    onClick = onShareRecoverySnapshot,
                    enabled = recoveryActive || snapshotReady,
                    variant = AuralisButtonVariant.Secondary,
                    compact = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SoftPanel {
                SectionLabel("Evidence report")
                Text(
                    text = "${evidenceSnapshot.timeline.size} timeline entries",
                    color = if (evidenceSnapshot.timeline.isNotEmpty()) AuralisColors.Success else AuralisColors.Warning,
                    fontWeight = FontWeight.Bold
                )
                BodyText("Generate a local plain-text recovery evidence report. Nothing is uploaded unless you share it.")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AuralisActionButton(
                        text = "Generate + copy",
                        onClick = onCopyEvidenceReport,
                        variant = AuralisButtonVariant.Secondary,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                    AuralisActionButton(
                        text = "Generate + share",
                        onClick = onShareEvidenceReport,
                        variant = AuralisButtonVariant.Secondary,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                MicroText(
                    text = "$eventCount event log entries available",
                    color = AuralisColors.TextMuted
                )
            }

            EvidenceTimelineSection(evidenceSnapshot)

            SoftPanel {
                SectionLabel("Channel diagnostics")
                channels.forEach { channel ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = channel.type.title,
                            color = AuralisColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = channel.state,
                            color = if (channel.active) AuralisColors.Success else AuralisColors.TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Cloud dashboard",
                        color = AuralisColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Future",
                        color = AuralisColors.TextDim,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun EvidenceTimelineSection(snapshot: EvidenceTimelineSnapshot) {
    SoftPanel {
        SectionLabel("Evidence timeline")
        Text(
            text = "Generated ${snapshot.generatedAt}",
            color = AuralisColors.TextPrimary,
            fontWeight = FontWeight.Bold
        )
        MicroText(
            text = "Recovery ${if (snapshot.recoveryState.recoveryActive) "active" else "inactive"} - Ring ${if (snapshot.ringActive) "active" else "off"} - Local ${if (snapshot.localControlStatus.active) "online" else "offline"}",
            color = AuralisColors.CyanSoft
        )
        MicroText(
            text = "State: ${snapshot.recoveryState.lastCommand} - ${RecoveryStateStore.ageText(snapshot.recoveryState)}",
            color = AuralisColors.TextMuted
        )
        MicroText(
            text = "Local: ${snapshot.localControlStatus.lastCommand} - ${LocalControlStore.ageText(snapshot.localControlStatus)}",
            color = AuralisColors.TextMuted
        )
        MicroText(
            text = "SMS: ${snapshot.smsStatus.lastCommand} - ${SmsCommandStore.ageText(snapshot.smsStatus)} - sender ${snapshot.trustedSenderMasked}",
            color = AuralisColors.TextMuted
        )

        DividerLine()

        SectionLabel("Recent trail points")
        if (snapshot.trailPoints.isEmpty()) {
            MicroText(
                text = "No local recovery trail points yet.",
                color = AuralisColors.TextMuted
            )
        } else {
            snapshot.trailPoints.take(3).forEach { point ->
                MicroText(
                    text = "${LocationTrailStore.ageText(point.timestamp)} - ${point.source} - ${LocationTrailStore.formatPoint(point)}",
                    color = AuralisColors.TextSecondary
                )
            }
        }

        SectionLabel("Recent events")
        if (snapshot.eventLogs.isEmpty()) {
            MicroText(
                text = "No event log entries yet.",
                color = AuralisColors.TextMuted
            )
        } else {
            snapshot.eventLogs.take(3).forEach { entry ->
                MicroText(
                    text = "${EventLogStore.ageText(entry)} - ${entry.channel} - ${entry.command}",
                    color = AuralisColors.TextSecondary
                )
            }
        }
    }
}
