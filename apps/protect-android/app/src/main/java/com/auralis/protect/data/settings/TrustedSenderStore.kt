package com.auralis.protect.data.settings

import android.content.Context

object TrustedSenderStore {
    private const val PREFS_NAME = "auralis_trusted_sender_store"
    private const val KEY_TRUSTED_SENDER = "trusted_sender"

    fun saveTrustedSender(
        context: Context,
        number: String
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TRUSTED_SENDER, number.trim())
            .apply()
    }

    fun clearTrustedSender(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TRUSTED_SENDER)
            .apply()
    }

    fun readTrustedSender(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TRUSTED_SENDER, "")
            .orEmpty()
    }

    fun hasTrustedSender(context: Context): Boolean {
        return normalize(readTrustedSender(context)).length >= 8
    }

    fun isSenderTrusted(
        context: Context,
        incomingSender: String?
    ): Boolean {
        val trusted = normalize(readTrustedSender(context))
        val incoming = normalize(incomingSender.orEmpty())

        if (trusted.length < 8 || incoming.length < 8) return false

        return trusted == incoming ||
            trusted.endsWith(incoming) ||
            incoming.endsWith(trusted) ||
            trusted.takeLast(10) == incoming.takeLast(10)
    }

    fun normalize(number: String): String {
        return number.filter { it.isDigit() }
    }

    fun mask(number: String?): String {
        val digits = normalize(number.orEmpty())
        if (digits.isBlank()) return "unknown"

        return when {
            digits.length <= 4 -> "****"
            else -> "**** ${digits.takeLast(4)}"
        }
    }
}
