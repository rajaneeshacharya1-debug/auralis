package com.auralis.protect.data.logs

import android.content.Context

data class EventLogEntry(
    val timestamp: Long,
    val channel: String,
    val command: String,
    val detail: String
)

object EventLogStore {
    private const val PREFS_NAME = "auralis_event_log_store"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 25

    fun append(
        context: Context,
        channel: String,
        command: String,
        detail: String
    ) {
        val current = readEvents(context).toMutableList()

        current.add(
            0,
            EventLogEntry(
                timestamp = System.currentTimeMillis(),
                channel = sanitize(channel),
                command = sanitize(command),
                detail = sanitize(detail)
            )
        )

        val trimmed = current.take(MAX_EVENTS)
        saveEvents(context, trimmed)
    }

    fun readEvents(context: Context): List<EventLogEntry> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EVENTS, "")
            .orEmpty()

        if (raw.isBlank()) return emptyList()

        return raw
            .lineSequence()
            .mapNotNull { line ->
                val parts = line.split("|", limit = 4)
                if (parts.size != 4) return@mapNotNull null

                EventLogEntry(
                    timestamp = parts[0].toLongOrNull() ?: 0L,
                    channel = parts[1],
                    command = parts[2],
                    detail = parts[3]
                )
            }
            .filter { it.timestamp > 0L }
            .toList()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_EVENTS)
            .apply()
    }

    fun ageText(entry: EventLogEntry): String {
        val seconds = ((System.currentTimeMillis() - entry.timestamp) / 1000L).coerceAtLeast(0L)

        return when {
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86400 -> "${seconds / 3600}h ago"
            else -> "${seconds / 86400}d ago"
        }
    }

    private fun saveEvents(
        context: Context,
        events: List<EventLogEntry>
    ) {
        val raw = events.joinToString(separator = "\n") { entry ->
            "${entry.timestamp}|${entry.channel}|${entry.command}|${entry.detail}"
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EVENTS, raw)
            .apply()
    }

    private fun sanitize(value: String): String {
        return value
            .replace("|", "/")
            .replace("\n", " ")
            .replace("\r", " ")
            .trim()
    }
}
