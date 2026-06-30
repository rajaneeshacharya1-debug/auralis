package com.auralis.protect.domain.model

object CommandChannelDefaults {
    fun statuses(
        smsReady: Boolean,
        localControlReady: Boolean
    ): List<CommandChannelStatus> {
        return listOf(
            CommandChannelStatus(
                type = CommandChannelType.WIFI,
                state = if (localControlReady) "Online" else "Prototype",
                detail = "Same-network web controller now; cloud dashboard should replace this for the final product.",
                active = localControlReady
            ),
            CommandChannelStatus(
                type = CommandChannelType.SMS,
                state = if (smsReady) "Armed" else "Needs setup",
                detail = "Emergency boot / stop only, not the normal control plane.",
                active = smsReady
            ),
            CommandChannelStatus(
                type = CommandChannelType.HOTSPOT,
                state = if (localControlReady) "Available" else "Later",
                detail = "Direct rescue link when both devices are on the protected phone hotspot.",
                active = localControlReady
            ),
            CommandChannelStatus(
                type = CommandChannelType.BLUETOOTH,
                state = "Later",
                detail = "Nearby trusted-device fallback after the main online dashboard is stable.",
                active = false
            )
        )
    }
}
