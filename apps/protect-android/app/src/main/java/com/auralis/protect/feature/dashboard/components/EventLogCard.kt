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
import com.auralis.protect.data.logs.EventLogEntry
import com.auralis.protect.data.logs.EventLogStore
import com.auralis.protect.design.components.AuralisActionButton
import com.auralis.protect.design.components.AuralisButtonVariant
import com.auralis.protect.design.theme.AuralisColors

@Composable
fun EventLogCard(
    events: List<EventLogEntry>,
    onClearLogs: () -> Unit
) {
    AuralisCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                BigTitle("Activity log")
                BodyText(if (events.isEmpty()) "No command events yet" else "${events.size} recent events")
            }

            AuralisActionButton(
                text = "Clear",
                onClick = onClearLogs,
                enabled = events.isNotEmpty(),
                variant = AuralisButtonVariant.Neutral,
                compact = true
            )
        }

        if (events.isEmpty()) {
            BodyText("Manual controls, emergency SMS, and local commands appear here.")
        } else {
            events.take(5).forEach { entry ->
                EventLogRow(entry)
            }
        }
    }
}

@Composable
private fun EventLogRow(entry: EventLogEntry) {
    SoftPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = entry.channel,
                color = AuralisColors.CyanSoft,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            MicroText(text = EventLogStore.ageText(entry), color = AuralisColors.TextMuted)
        }

        Text(
            text = entry.command,
            color = AuralisColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        MicroText(text = entry.detail, color = AuralisColors.TextSecondary)
    }
}
