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
    val trustedConfigured = trustedSender.trim().isNotBlank()
    val permissionsReady = receivePermissionGranted && sendPermissionGranted
    val ready = permissionsReady && trustedConfigured

    val stateText = when {
        ready -> "Armed"
        !trustedConfigured -> "Trusted sender needed"
        permissionsReady -> "Permission ready"
        else -> "Permission needed"
    }

    AuralisCard {
        BigTitle("Emergency SMS")
        Text(
            text = stateText,
            color = if (ready) AuralisColors.Success else AuralisColors.Warning,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 18.sp
        )
        BodyText("Use only when normal or local control can't reach this phone.")

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
            Text(
                text = SmsCommandStore.BOOT_COMMAND,
                color = AuralisColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = SmsCommandStore.STOP_COMMAND,
                color = AuralisColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            MicroText(
                text = "Last: ${smsCommandStatus.lastCommand} \u00b7 ${SmsCommandStore.ageText(smsCommandStatus)}",
                color = AuralisColors.CyanSoft
            )
        }
    }
}
