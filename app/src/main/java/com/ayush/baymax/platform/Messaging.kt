package com.ayush.baymax.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ayush.baymax.data.ContactApp
import com.ayush.baymax.data.TrustedContact

/**
 * Opens SMS or WhatsApp with the message filled in (FR-26). The user presses send in that app,
 * so nothing is ever sent silently.
 */
object Messaging {
    fun open(context: Context, contact: TrustedContact, text: String, app: ContactApp): Boolean {
        val digits = contact.phone.filter { it.isDigit() || it == '+' }
        val intent = when (app) {
            ContactApp.Sms -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$digits")).putExtra("sms_body", text)
            ContactApp.WhatsApp -> Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${digits.removePrefix("+")}?text=${Uri.encode(text)}"))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
