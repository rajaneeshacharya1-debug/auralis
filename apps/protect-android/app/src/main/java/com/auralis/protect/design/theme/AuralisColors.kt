package com.auralis.protect.design.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object AuralisColors {
    val DeepBlack = Color(0xFF02070B)
    val SurfaceBlack = Color(0xFF061017)

    val Card = Color(0xFF0B131A)
    val CardRaised = Color(0xFF111C25)
    val CardSoft = Color(0xFF121D25)
    val Metric = Color(0xFF121D25)
    val HeaderMetric = Color(0xFF061017)
    val FieldFill = Color(0xFF061017)

    val Stroke = Color(0x264FCFE1)
    val StrokeSoft = Color(0x1F4FCFE1)
    val StrokeStrong = Color(0x804FCFE1)
    val FieldStroke = Color(0x4D4FCFE1)
    val Divider = Color(0x1A4FCFE1)
    val TabBarBg = Color(0xFF061017)

    val Cyan = Color(0xFF5DEAF7)
    val CyanSoft = Color(0xFFA7F7FF)
    val CyanDeep = Color(0xFF0B3540)

    val Success = Color(0xFF78F2A8)
    val SuccessDeep = Color(0xFF123A29)

    val Warning = Color(0xFFFFCF66)
    val WarningDeep = Color(0xFF4A320C)

    val Danger = Color(0xFFFF7C72)
    val DangerSoft = Color(0xFFFFC4BF)
    val DangerDeep = Color(0xFF481917)

    val SecondaryButton = Color(0xFF18313C)
    val Neutral = Color(0xFF25333D)
    val DisabledButton = Color(0xFF303D46)
    val ButtonDisabledText = Color(0xFFC3CFD6)
    val ButtonDisabledBorder = Color(0xFF667683)

    // Compatibility aliases
    val ButtonSecondary = SecondaryButton
    val ButtonNeutral = Neutral
    val CardAccent = Color(0xFF142838)

    val TextPrimary = Color(0xFFFFF8EC)
    val TextSecondary = Color(0xFFD7E3EA)
    val TextMuted = Color(0xFF91A8B5)
    val TextDim = Color(0xFF6F8490)
    val TextOnLight = Color(0xFF031018)

    val Background = Color(0xFF031018)

    val BackgroundGradient = Brush.verticalGradient(
        colors = listOf(Background, Background, Background)
    )
}
