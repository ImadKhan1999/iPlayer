package com.imadlabs.iplayer.startup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Opens the official ImadLabs Telegram channel: Telegram app first, then a browser. */
object TelegramLauncher {
    const val CHANNEL = "ImadLabs"
    const val CHANNEL_URL = "https://t.me/ImadLabs"

    fun open(context: Context): Boolean {
        val attempts = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$CHANNEL")),
            Intent(Intent.ACTION_VIEW, Uri.parse(CHANNEL_URL)).addCategory(Intent.CATEGORY_BROWSABLE),
        )
        for (intent in attempts) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        Toast.makeText(context, "No app can open links. Visit $CHANNEL_URL in a browser.", Toast.LENGTH_LONG).show()
        return false
    }
}
