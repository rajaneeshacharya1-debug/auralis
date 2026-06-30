package com.auralis.protect.feature.dashboard.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.auralis.protect.design.components.AuralisActionButton
import com.auralis.protect.design.components.AuralisButtonVariant
import com.auralis.protect.design.theme.AuralisColors

@Composable
fun NotificationPermissionCard(
    onAllowNotifications: () -> Unit
) {
    SoftPanel {
        SectionLabel("Setup required")
        BodyText(
            text = "Allow notifications so Android can keep the recovery service visible while tracking is active.",
            color = AuralisColors.TextSecondary
        )
        AuralisActionButton(
            text = "Allow notifications",
            onClick = onAllowNotifications,
            variant = AuralisButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
