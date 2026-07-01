package com.auralis.protect.feature.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auralis.protect.data.location.LocationSnapshot
import com.auralis.protect.data.location.LocationTrailPoint
import com.auralis.protect.data.location.LocationTrailStore
import com.auralis.protect.data.network.NetworkStatus
import com.auralis.protect.design.components.AuralisActionButton
import com.auralis.protect.design.components.AuralisButtonVariant
import com.auralis.protect.design.theme.AuralisColors

@Composable
fun DeviceStatusPanel(
    batteryPercent: Int,
    networkStatus: NetworkStatus,
    recoveryActive: Boolean,
    ringActive: Boolean,
    locationSnapshot: LocationSnapshot,
    trailPoints: List<LocationTrailPoint>,
    onRequestPermission: () -> Unit = {},
    onRefreshLocation: () -> Unit,
    onClearTrail: () -> Unit
) {
    val hasPermission = locationSnapshot.hasPermission
    val hasCoordinates = locationSnapshot.hasCoordinates

    AuralisCard {
        BigTitle("Device status")

        TwoColumnRow(
            first = {
                MetricTile(
                    title = "Battery",
                    value = "$batteryPercent%",
                    subtitle = if (batteryPercent >= 30) "Good" else "Low",
                    valueColor = AuralisColors.TextPrimary
                )
            },
            second = {
                MetricTile(
                    title = "Network",
                    value = networkStatus.value,
                    subtitle = networkStatus.detail.replaceFirstChar { it.uppercase() },
                    valueColor = AuralisColors.TextPrimary
                )
            }
        )

        TwoColumnRow(
            first = {
                MetricTile(
                    title = "Recovery",
                    value = if (recoveryActive) "Active" else "Standby",
                    subtitle = if (recoveryActive) "Running" else "Ready",
                    valueColor = if (recoveryActive) AuralisColors.Success else AuralisColors.CyanSoft
                )
            },
            second = {
                MetricTile(
                    title = "Ring",
                    value = if (ringActive) "Playing" else "Off",
                    subtitle = if (ringActive) "Audible alert" else "Ready",
                    valueColor = if (ringActive) AuralisColors.Warning else AuralisColors.CyanSoft
                )
            }
        )

        SoftPanel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    SectionLabel("Location")
                    Text(
                        text = locationSnapshot.value,
                        color = when {
                            !hasPermission -> AuralisColors.Warning
                            hasCoordinates -> AuralisColors.Success
                            else -> AuralisColors.CyanSoft
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp
                    )
                    MicroText(text = locationSnapshot.detail, color = AuralisColors.TextSecondary)
                }

                AuralisActionButton(
                    text = if (!hasPermission) "Allow" else "Refresh",
                    onClick = if (!hasPermission) onRequestPermission else onRefreshLocation,
                    variant = if (!hasPermission) AuralisButtonVariant.Primary else AuralisButtonVariant.Secondary,
                    compact = true,
                    modifier = Modifier.weight(0.45f)
                )
            }

            if (hasCoordinates) {
                MicroText(
                    text = "Lat ${locationSnapshot.latitude} \u00b7 Lng ${locationSnapshot.longitude} \u00b7 ${locationSnapshot.freshnessLabel}",
                    color = AuralisColors.CyanSoft
                )
            }
        }

        LocationTrailPanel(
            trailPoints = trailPoints,
            recoveryActive = recoveryActive,
            onClearTrail = onClearTrail
        )
    }
}

@Composable
private fun LocationTrailPanel(
    trailPoints: List<LocationTrailPoint>,
    recoveryActive: Boolean,
    onClearTrail: () -> Unit
) {
    val latest = trailPoints.firstOrNull()

    SoftPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SectionLabel("Recovery evidence trail")
                Text(
                    text = if (trailPoints.isEmpty()) "No trail yet" else "${trailPoints.size} points",
                    color = if (trailPoints.isEmpty()) AuralisColors.Warning else AuralisColors.Success,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp
                )
                MicroText(
                    text = if (recoveryActive) {
                        "Records local points only while recovery is active."
                    } else {
                        "Paused until recovery is active."
                    },
                    color = AuralisColors.TextSecondary
                )
            }

            AuralisActionButton(
                text = "Clear trail",
                onClick = onClearTrail,
                enabled = trailPoints.isNotEmpty(),
                variant = AuralisButtonVariant.Danger,
                compact = true,
                modifier = Modifier.weight(0.52f)
            )
        }

        if (latest != null) {
            MicroText(
                text = "Latest: ${LocationTrailStore.formatPoint(latest)}",
                color = AuralisColors.CyanSoft
            )
        }

        TrailPathPreview(trailPoints = trailPoints)

        trailPoints.take(3).forEach { point ->
            MicroText(
                text = "${LocationTrailStore.ageText(point.timestamp)} - ${point.source} - ${"%.5f".format(point.latitude)}, ${"%.5f".format(point.longitude)}",
                color = AuralisColors.TextMuted
            )
        }
    }
}

@Composable
private fun TrailPathPreview(trailPoints: List<LocationTrailPoint>) {
    val previewPoints = trailPoints.take(24).asReversed()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
    ) {
        if (previewPoints.size < 2) {
            drawLine(
                color = AuralisColors.StrokeSoft,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 2f
            )
            return@Canvas
        }

        val minLat = previewPoints.minOf { it.latitude }
        val maxLat = previewPoints.maxOf { it.latitude }
        val minLon = previewPoints.minOf { it.longitude }
        val maxLon = previewPoints.maxOf { it.longitude }
        val latSpan = (maxLat - minLat).takeIf { it > 0.000001 } ?: 0.000001
        val lonSpan = (maxLon - minLon).takeIf { it > 0.000001 } ?: 0.000001

        val offsets = previewPoints.map { point ->
            val x = (((point.longitude - minLon) / lonSpan) * size.width).toFloat()
            val y = (size.height - (((point.latitude - minLat) / latSpan) * size.height)).toFloat()
            Offset(
                x = x.coerceIn(6f, size.width - 6f),
                y = y.coerceIn(6f, size.height - 6f)
            )
        }

        offsets.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = AuralisColors.CyanSoft,
                start = start,
                end = end,
                strokeWidth = 4f
            )
        }

        offsets.firstOrNull()?.let { start ->
            drawCircle(color = AuralisColors.TextDim, radius = 5f, center = start)
        }

        offsets.lastOrNull()?.let { end ->
            drawCircle(color = AuralisColors.Success, radius = 7f, center = end)
        }
    }
}
