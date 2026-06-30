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
import com.auralis.protect.data.localcontrol.LocalControlStatus
import com.auralis.protect.data.localcontrol.LocalControlStore
import com.auralis.protect.design.components.AuralisActionButton
import com.auralis.protect.design.components.AuralisButtonVariant
import com.auralis.protect.design.theme.AuralisColors

@Composable
fun LocalControlCard(
    localStatus: LocalControlStatus,
    primaryIpAddress: String,
    localToken: String,
    onStartLocalControl: () -> Unit,
    onStopLocalControl: () -> Unit
) {
    val baseAddress = if (primaryIpAddress == "unavailable") {
        "Connect to Wi-Fi or hotspot"
    } else {
        "http://$primaryIpAddress:${LocalControlStore.PORT}"
    }

    AuralisCard {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MediumTitle("Local control")
            Text(
                text = if (localStatus.active) "Online" else "Offline",
                color = if (localStatus.active) AuralisColors.Success else AuralisColors.Warning,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 17.sp
            )
            BodyText("Use when another trusted device is on the same network.")
        }

        SoftPanel {
            SectionLabel("Panel URL")
            Text(
                text = baseAddress,
                color = AuralisColors.CyanSoft,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 17.sp
            )
            MicroText(text = "Token: $localToken", color = AuralisColors.TextDim)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AuralisActionButton(
                text = "Start local",
                onClick = onStartLocalControl,
                enabled = !localStatus.active,
                variant = AuralisButtonVariant.Primary,
                modifier = Modifier.weight(1f)
            )

            AuralisActionButton(
                text = "Stop local",
                onClick = onStopLocalControl,
                enabled = localStatus.active,
                variant = AuralisButtonVariant.Danger,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
