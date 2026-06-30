package com.auralis.protect.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import com.auralis.protect.data.logs.EventLogStore
import com.auralis.protect.data.settings.TrustedSenderStore
import com.auralis.protect.data.sms.SmsCommandStore
import com.auralis.protect.data.sms.SmsReplySender
import com.auralis.protect.domain.model.AuralisCommand
import com.auralis.protect.domain.model.CommandSource
import com.auralis.protect.domain.usecase.CommandEngine

class SmsCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val body = messages.joinToString(separator = "\n") { message ->
            message.messageBody.orEmpty()
        }

        val sender = messages.firstOrNull()?.originatingAddress
        val subscriptionId = readSubscriptionId(intent)

        val command = when {
            body.contains(SmsCommandStore.BOOT_COMMAND, ignoreCase = false) -> AuralisCommand.BOOT
            body.contains(SmsCommandStore.STOP_COMMAND, ignoreCase = false) -> AuralisCommand.STOP
            else -> return
        }

        if (!TrustedSenderStore.isSenderTrusted(context, sender)) {
            val detail = "Ignored: sender ${TrustedSenderStore.mask(sender)} is not the trusted controller."

            SmsCommandStore.saveCommand(
                context = context,
                command = "${command.label} REJECTED",
                detail = detail
            )

            EventLogStore.append(
                context = context,
                channel = CommandSource.SMS.label,
                command = "${command.label} REJECTED",
                detail = detail
            )

            return
        }

        val result = CommandEngine.execute(
            context = context,
            source = CommandSource.SMS,
            command = command
        )

        val replySent = SmsReplySender.sendReply(
            context = context,
            phoneNumber = sender,
            message = result.publicMessage,
            subscriptionId = subscriptionId
        )

        val detail = buildString {
            append("${result.command.label} from trusted SMS sender ${TrustedSenderStore.mask(sender)}.")
            append(" ")
            append(if (replySent) "Reply sent." else "Reply not sent. SEND_SMS permission or SIM route may be unavailable.")
        }

        SmsCommandStore.saveCommand(
            context = context,
            command = result.command.label,
            detail = detail
        )
    }

    private fun readSubscriptionId(intent: Intent): Int {
        val fromOfficialExtra = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        )

        if (fromOfficialExtra != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return fromOfficialExtra
        }

        return intent.getIntExtra(
            "subscription",
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        )
    }
}
