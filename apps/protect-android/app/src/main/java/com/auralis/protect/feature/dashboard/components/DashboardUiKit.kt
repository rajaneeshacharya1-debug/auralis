package com.auralis.protect.feature.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auralis.protect.design.theme.AuralisColors

@Composable
fun AuralisCard(
    modifier: Modifier = Modifier,
    raised: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (raised) AuralisColors.CardRaised else AuralisColors.Card
        ),
        shape = RoundedCornerShape(if (raised) 26.dp else 24.dp),
        border = BorderStroke(
            width = 0.5.dp,
            color = if (raised) AuralisColors.StrokeStrong.copy(alpha = 0.32f) else AuralisColors.Stroke
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun SoftPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AuralisColors.CardSoft),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.5.dp, AuralisColors.StrokeSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

@Composable
fun MetricPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AuralisColors.Metric),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, AuralisColors.StrokeSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            content = content
        )
    }
}

@Composable
fun HeaderMetricPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AuralisColors.HeaderMetric),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.5.dp, AuralisColors.StrokeSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            content = content
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = AuralisColors.TextDim,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun BigTitle(text: String) {
    Text(
        text = text,
        color = AuralisColors.TextPrimary,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 23.sp
    )
}

@Composable
fun MediumTitle(text: String) {
    Text(
        text = text,
        color = AuralisColors.TextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 19.sp
    )
}

@Composable
fun BodyText(
    text: String,
    color: Color = AuralisColors.TextSecondary
) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        lineHeight = 18.sp
    )
}

@Composable
fun MicroText(
    text: String,
    color: Color = AuralisColors.TextMuted,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        fontWeight = fontWeight
    )
}

@Composable
fun StateChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        maxLines = 1,
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(AuralisColors.Divider)
    )
}

@Composable
fun MetricTile(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AuralisColors.TextPrimary
) {
    MetricPanel(modifier = modifier) {
        SectionLabel(title)
        Text(
            text = value,
            color = valueColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 19.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        MicroText(text = subtitle, color = AuralisColors.CyanSoft)
    }
}

@Composable
fun DashboardTabs(
    selected: DashboardTab,
    onSelected: (DashboardTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AuralisColors.TabBarBg, RoundedCornerShape(18.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DashboardTab.values().forEach { tab ->
            val active = tab == selected
            Text(
                text = tab.label,
                color = if (active) AuralisColors.CyanSoft else AuralisColors.TextDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = if (active) AuralisColors.Card else Color.Transparent,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .then(
                        if (active) Modifier.border(0.5.dp, AuralisColors.Stroke, RoundedCornerShape(14.dp))
                        else Modifier
                    )
                    .clickable { onSelected(tab) }
                    .padding(vertical = 8.dp)
            )
        }
    }
}

enum class DashboardTab(val label: String) {
    Controls("Controls"),
    Status("Status"),
    Sms("SMS"),
    Log("Log"),
    Advanced("Advanced")
}

@Composable
fun TwoColumnRow(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) { first() }
        Column(modifier = Modifier.weight(1f)) { second() }
    }
}

@Composable
fun AuralisInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AuralisColors.FieldFill.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .border(1.dp, AuralisColors.FieldStroke, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label.uppercase(),
            color = AuralisColors.Cyan,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = AuralisColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(AuralisColors.Cyan),
            decorationBox = { innerTextField ->
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        color = AuralisColors.TextDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                innerTextField()
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
