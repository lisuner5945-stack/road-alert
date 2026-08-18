package ru.example.roadalert.util

import android.util.Log
import java.util.ArrayDeque

/**
 * Ограниченный локальный ring buffer технических событий (ТЗ §44).
 *
 * Никуда не отправляется. В release не хранит координаты — вызывающий код
 * обязан передавать уже обезличенные значения.
 */
object AppLog {

    const val MAX_EVENTS = 700

    private const val TAG = "RoadAlert"

    data class Entry(val timestampMs: Long, val message: String)

    private val buffer = ArrayDeque<Entry>(MAX_EVENTS)

    @Synchronized
    fun event(event: String, vararg params: Pair<String, Any?>) {
        val message = if (params.isEmpty()) {
            event
        } else {
            event + params.joinToString(prefix = " ", separator = " ") { "${it.first}=${it.second}" }
        }
        if (buffer.size >= MAX_EVENTS) buffer.removeFirst()
        buffer.addLast(Entry(System.currentTimeMillis(), message))
        Log.d(TAG, message)
    }

    @Synchronized
    fun snapshot(): List<Entry> = buffer.toList()

    @Synchronized
    fun clear() = buffer.clear()
}
