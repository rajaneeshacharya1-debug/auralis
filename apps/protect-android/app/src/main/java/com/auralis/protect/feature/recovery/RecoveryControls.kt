package com.auralis.protect.feature.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.auralis.protect.design.components.AuralisActionButton
import com.auralis.protect.design.components.AuralisButtonStyle
import com.auralis.protect.feature.dashboard.components.AuralisCard
import com.auralis.protect.feature.dashboard.components.BigTitle
import com.auralis.protect.feature.dashboard.components.BodyText

@Composable
fun RecoveryControls(
    recoveryActive: Boolean,
    ringActive: Boolean,
    onStartRecovery: () -> Unit,
    onStopRecovery: () -> Unit,
    onStartRing: () -> Unit,
    onStopRing: () -> Unit
) {
    AuralisCard {
        BigTitle("Recovery")
        BodyText("Start to begin tracking. Ring sounds an audible alert.")

        AuralisActionButton(
            text = if (recoveryActive) "Stop recovery" else "Start recovery",
            onClick = if (recoveryActive) onStopRecovery else onStartRecovery,
            modifier = Modifier.fillMaxWidth(),
            style = if (recoveryActive) AuralisButtonStyle.Danger else AuralisButtonStyle.Primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AuralisActionButton(
                text = "Ring device",
                onClick = onStartRing,
                enabled = !ringActive,
                modifier = Modifier.weight(1f),
                style = AuralisButtonStyle.Warning
            )

            AuralisActionButton(
                text = "Stop ring",
                onClick = onStopRing,
                enabled = ringActive,
                modifier = Modifier.weight(1f),
                style = AuralisButtonStyle.Danger
            )
        }
    }
}
