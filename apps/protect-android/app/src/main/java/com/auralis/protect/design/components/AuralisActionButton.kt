package com.auralis.protect.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auralis.protect.design.theme.AuralisColors

enum class AuralisButtonVariant {
    Primary,
    Danger,
    Warning,
    Secondary,
    Neutral,
    Success
}

// Compatibility name kept so older component files still compile if they remain in the project.
enum class AuralisButtonStyle {
    Primary,
    Secondary,
    Success,
    Warning,
    Danger,
    Neutral
}

private fun AuralisButtonVariant.toStyle(): AuralisButtonStyle = when (this) {
    AuralisButtonVariant.Primary -> AuralisButtonStyle.Primary
    AuralisButtonVariant.Danger -> AuralisButtonStyle.Danger
    AuralisButtonVariant.Warning -> AuralisButtonStyle.Warning
    AuralisButtonVariant.Secondary -> AuralisButtonStyle.Secondary
    AuralisButtonVariant.Neutral -> AuralisButtonStyle.Neutral
    AuralisButtonVariant.Success -> AuralisButtonStyle.Success
}

@Composable
fun AuralisActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: AuralisButtonVariant? = null,
    style: AuralisButtonStyle? = null,
    compact: Boolean = false
) {
    val resolvedStyle = style ?: variant?.toStyle() ?: AuralisButtonStyle.Primary

    val containerColor: Color = when (resolvedStyle) {
        AuralisButtonStyle.Primary -> AuralisColors.Cyan
        AuralisButtonStyle.Success -> AuralisColors.Success
        AuralisButtonStyle.Warning -> AuralisColors.Warning
        AuralisButtonStyle.Danger -> AuralisColors.Danger
        AuralisButtonStyle.Secondary -> AuralisColors.SecondaryButton
        AuralisButtonStyle.Neutral -> AuralisColors.Neutral
    }

    val contentColor: Color = when (resolvedStyle) {
        AuralisButtonStyle.Primary,
        AuralisButtonStyle.Success,
        AuralisButtonStyle.Warning,
        AuralisButtonStyle.Danger -> AuralisColors.TextOnLight
        AuralisButtonStyle.Secondary -> AuralisColors.CyanSoft
        AuralisButtonStyle.Neutral -> AuralisColors.TextPrimary
    }

    val borderColor: Color = when (resolvedStyle) {
        AuralisButtonStyle.Primary -> AuralisColors.CyanSoft
        AuralisButtonStyle.Success -> AuralisColors.Success.copy(alpha = 0.7f)
        AuralisButtonStyle.Warning -> AuralisColors.Warning.copy(alpha = 0.7f)
        AuralisButtonStyle.Danger -> AuralisColors.DangerSoft
        AuralisButtonStyle.Secondary -> AuralisColors.CyanSoft.copy(alpha = 0.36f)
        AuralisButtonStyle.Neutral -> AuralisColors.ButtonDisabledBorder
    }

    val disabledContainerColor: Color = when (resolvedStyle) {
        AuralisButtonStyle.Danger -> AuralisColors.DangerDeep.copy(alpha = 0.55f)
        AuralisButtonStyle.Warning -> AuralisColors.WarningDeep.copy(alpha = 0.55f)
        AuralisButtonStyle.Primary -> AuralisColors.CyanDeep.copy(alpha = 0.55f)
        AuralisButtonStyle.Success -> AuralisColors.SuccessDeep.copy(alpha = 0.55f)
        AuralisButtonStyle.Secondary,
        AuralisButtonStyle.Neutral -> AuralisColors.DisabledButton.copy(alpha = 0.65f)
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = if (compact) 40.dp else 48.dp),
        shape = RoundedCornerShape(if (compact) 14.dp else 16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) borderColor else AuralisColors.ButtonDisabledBorder.copy(alpha = 0.55f)
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = AuralisColors.ButtonDisabledText
        ),
        contentPadding = PaddingValues(
            horizontal = if (compact) 12.dp else 16.dp,
            vertical = if (compact) 9.dp else 13.dp
        )
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.1.sp,
            maxLines = 1
        )
    }
}
