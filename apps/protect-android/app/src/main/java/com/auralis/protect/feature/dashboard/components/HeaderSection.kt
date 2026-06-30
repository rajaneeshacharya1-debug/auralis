package com.auralis.protect.feature.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.auralis.protect.design.theme.AuralisColors

@Composable
fun HeaderSection(
    recoveryActive: Boolean,
    ringActive: Boolean,
    batteryPercent: Int,
    smsReady: Boolean = false
) {
    val chipText = when {
        ringActive -> "Ringing"
        recoveryActive -> "Active"
        else -> "Ready"
    }

    val chipColor: Color = when {
        ringActive -> AuralisColors.Warning
        recoveryActive -> AuralisColors.Success
        else -> AuralisColors.Cyan
    }

    val detail = when {
        ringActive -> "Audible alert is playing. Stop ring when the device is found."
        recoveryActive -> "Recovery service running. Keep the notification visible."
        else -> "Standing by for manual, nearby, or emergency ignition."
    }

    AuralisCard(raised = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auralis Protect",
                    color = AuralisColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp
                )
                Text(
                    text = "Personal device recovery agent",
                    color = AuralisColors.Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 14.sp
                )
            }

            StateChip(text = chipText, color = chipColor)
        }

        BodyText(detail)

        TwoColumnRow(
            first = {
                HeaderMetricPanel {
                    SectionLabel("Battery")
                    Text(
                        text = "$batteryPercent%",
                        color = AuralisColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp
                    )
                }
            },
            second = {
                HeaderMetricPanel {
                    SectionLabel("SMS")
                    Text(
                        text = if (smsReady) "Armed" else "Emergency only",
                        color = if (smsReady) AuralisColors.Success else AuralisColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 16.sp
                    )
                }
            }
        )
    }
}
