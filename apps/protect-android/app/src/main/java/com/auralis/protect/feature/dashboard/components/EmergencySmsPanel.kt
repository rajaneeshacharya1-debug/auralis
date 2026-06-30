package com.auralis.protect.feature.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auralis.protect.data.settings.TrustedSenderStore
import com.auralis.protect.data.sms.SmsCommandStatus
import com.auralis.protect.data.sms.SmsCommandStore
import com.auralis.protect.design.components.AuralisActionButton
import com.auralis.protect.design.components.AuralisButtonVariant
import com.auralis.protect.design.theme.AuralisColors

@Composable
fun EmergencySmsPanel(
    receivePermissionGranted: Boolean,
    sendPermissionGranted: Boolean,
    trustedSender: String,
    draftTrustedSender: String,
    onDraftChanged: (String) -> Unit,
    onSaveTrustedSender: () -> Unit,
    onClearTrustedSender: () -> Unit,
    smsCommandStatus: SmsCommandStatus,
    onRequestSmsPermissions: () -> Unit
) {
    val trustedConfigured = TrustedSenderStore.normalize(trustedSender).length >= 8
    val permissionsReady = receivePermissionGranted && sendPermissionGranted
    val ready = permissionsReady && trustedConfigured
    val maskedTrustedSender = TrustedSenderStore.mask(trustedSender)

    val stateText = when {
        ready -> "Emergency armed"
        receivePermissionGranted && trustedConfigured -> "Can receive, replies blocked"
        trustedConfigured -> "SMS permission needed"
        !trustedConfigured -> "Trusted sender needed"
        else -> "Setup needed"
    }

    AuralisCard {
        BigTitle("Emergency SMS ignition")
        Text(
            text = stateText,
            color = if (ready) AuralisColors.Success else AuralisColors.Warning,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 18.sp
        )
        BodyText("Backup ignition channel for when the protected phone cannot be reached through the app or local Wi-Fi/hotspot panel.")

        TwoColumnRow(
            first = {
                MetricTile(
                    title = "SMS permission",
                    value = when {
                        permissionsReady -> "Receive + reply"
                        receivePermissionGranted -> "Receive only"
                        sendPermissionGranted -> "Reply only"
                        else -> "Not granted"
                    },
                    subtitle = "Depends on Android SMS permissions",
                    valueColor = if (permissionsReady) AuralisColors.Success else AuralisColors.Warning
                )
            },
            second = {
                MetricTile(
                    title = "Trusted sender",
                    value = if (trustedConfigured) maskedTrustedSender else "Not saved",
                    subtitle = if (trustedConfigured) "Only this controller is accepted" else "Required before arming",
                    valueColor = if (trustedConfigured) AuralisColors.Success else AuralisColors.Warning
                )
            }
        )

        AuralisInputField(
            label = "Trusted controller number",
            value = draftTrustedSender,
            onValueChange = onDraftChanged,
            placeholder = "+977 98XXXXXXXX"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AuralisActionButton(
                text = "Save sender",
                onClick = onSaveTrustedSender,
                variant = AuralisButtonVariant.Primary,
                modifier = Modifier.weight(1f)
            )

            AuralisActionButton(
                text = if (!permissionsReady) "Allow SMS" else "Check SMS",
                onClick = onRequestSmsPermissions,
                variant = if (!permissionsReady) AuralisButtonVariant.Secondary else AuralisButtonVariant.Neutral,
                modifier = Modifier.weight(1f)
            )
        }

        if (trustedConfigured) {
            AuralisActionButton(
                text = "Remove trusted sender",
                onClick = onClearTrustedSender,
                variant = AuralisButtonVariant.Danger,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SoftPanel {
            SectionLabel("Emergency commands")
            BodyText("Send exactly one of these from the trusted sender. BOOT starts recovery. STOP stops recovery.")
            Text(
                text = SmsCommandStore.BOOT_COMMAND,
                color = AuralisColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            MicroText(
                text = "Starts recovery mode from SMS.",
                color = AuralisColors.TextMuted
            )
            Text(
                text = SmsCommandStore.STOP_COMMAND,
                color = AuralisColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            MicroText(
                text = "Stops recovery mode from SMS.",
                color = AuralisColors.TextMuted
            )
        }

        SoftPanel {
            SectionLabel("Emergency readiness")
            StateChip(
                text = if (ready) "Ready" else "Not ready",
                color = if (ready) AuralisColors.Success else AuralisColors.Warning
            )
            MicroText(
                text = "Last SMS event: ${smsCommandStatus.lastCommand} - ${SmsCommandStore.ageText(smsCommandStatus)}",
                color = AuralisColors.CyanSoft
            )
            MicroText(
                text = smsCommandStatus.lastDetail,
                color = AuralisColors.TextMuted
            )
            BodyText("Delivery and replies can still depend on SIM state, mobile network reachability, carrier rules, and OEM battery restrictions.")
        }
    }
}
