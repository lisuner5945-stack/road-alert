package ru.example.roadalert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.example.roadalert.util.AppLog

class AppLogTest {

    @Before
    fun setUp() = AppLog.clear()

    @Test
    fun `ring buffer не растёт бесконечно`() {
        repeat(AppLog.MAX_EVENTS + 250) { AppLog.event("EVENT", "i" to it) }
        assertEquals(AppLog.MAX_EVENTS, AppLog.snapshot().size)
    }

    @Test
    fun `старые события вытесняются новыми`() {
        repeat(AppLog.MAX_EVENTS + 5) { AppLog.event("EVENT", "i" to it) }
        val messages = AppLog.snapshot().map { it.message }
        assertTrue(messages.last().endsWith("i=${AppLog.MAX_EVENTS + 4}"))
        assertTrue(messages.none { it.endsWith("i=0") })
    }
}
