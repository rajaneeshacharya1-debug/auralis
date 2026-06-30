package com.auralis.protect.domain.model

enum class AuralisCommand(
    val label: String
) {
    BOOT("BOOT"),
    STOP("STOP"),
    RING("RING"),
    RING_STOP("RING STOP"),
    STATUS("STATUS"),
    SNAPSHOT("SNAPSHOT"),
    REPORT("REPORT"),
    PING("PING")
}

enum class CommandSource(
    val label: String
) {
    MANUAL("MANUAL"),
    SMS("SMS"),
    LOCAL("LOCAL"),
    SYSTEM("SYSTEM")
}

data class CommandResult(
    val success: Boolean,
    val command: AuralisCommand,
    val source: CommandSource,
    val publicMessage: String,
    val detail: String
)
