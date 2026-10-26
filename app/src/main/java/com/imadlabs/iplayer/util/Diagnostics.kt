package com.imadlabs.iplayer.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * In-memory diagnostic log for advanced users (Settings > Advanced > Diagnostics).
 * Every entry is passed through [Redactor]; nothing is persisted or sent anywhere.
 */
object Diagnostics {
    private const val MAX = 300
    private val entries = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(tag: String, message: String, error: Throwable? = null) {
        val clean = Redactor.redact(message)
        val errText = error?.let { " — ${it.javaClass.simpleName}: ${Redactor.redact(it.message.orEmpty())}" }.orEmpty()
        entries.addLast("${time.format(Date())} [$tag] $clean$errText")
        while (entries.size > MAX) entries.removeFirst()
        Log.i("iPlayer/$tag", clean + errText)
    }

    @Synchronized
    fun snapshot(): List<String> = entries.toList()

    @Synchronized
    fun clear() = entries.clear()
}
