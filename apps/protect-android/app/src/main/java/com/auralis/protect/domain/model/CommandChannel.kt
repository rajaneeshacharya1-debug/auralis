package com.auralis.protect.domain.model

enum class CommandChannelType(
    val title: String
) {
    SMS("SMS Ignition"),
    WIFI("Dashboard Bridge"),
    BLUETOOTH("Nearby Fallback"),
    HOTSPOT("Hotspot Fallback")
}

data class CommandChannelStatus(
    val type: CommandChannelType,
    val state: String,
    val detail: String,
    val active: Boolean
)
