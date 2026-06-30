package com.auralis.protect.feature.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auralis.protect.data.location.LocationSnapshot
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
    onRequestPermission: () -> Unit = {},
    onRefreshLocation: () -> Unit
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
    }
}
