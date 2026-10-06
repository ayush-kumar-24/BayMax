package com.ayush.baymax.agent

import com.ayush.baymax.data.HealthEntry
import com.ayush.baymax.ui.log.LogFilter
import com.ayush.baymax.ui.log.filterEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ReminderParserTest {

    @Test fun `common phrasings are understood`() {
        assertEquals(ToolAction.CreateReminder("drink water", 120, 120), ReminderParser.parse("Remind me to drink water every 2 hours"))
        assertEquals(ToolAction.CreateReminder("stretch", 20, null), ReminderParser.parse("remind me in 20 minutes to stretch"))
        assertEquals(ToolAction.CreateReminder("take a break", 60, 60), ReminderParser.parse("Remind me to take a break every hour."))
        assertEquals(ToolAction.CreateReminder("call mom", 30, null), ReminderParser.parse("remind me to call mom in half an hour"))
        assertEquals(ToolAction.CreateReminder("check in with dad", 1440, 1440), ReminderParser.parse("remind me to check in with dad every day"))
        assertEquals(ToolAction.CreateReminder("drink water", 180, 180), ReminderParser.parse("remind me to drink water every three hours"))
        assertNull(ReminderParser.parse("what time is it"))
    }

    @Test fun `confirmations read naturally`() {
        assertEquals("Reminder set. I will remind you to drink water every two hours.", ReminderParser.confirmation(ToolAction.CreateReminder("drink water", 120, 120)))
        assertEquals("Reminder set. I will remind you to take a break every hour.", ReminderParser.confirmation(ToolAction.CreateReminder("take a break", 60, 60)))
        assertEquals("Reminder set. I will remind you to stretch in 20 minutes.", ReminderParser.confirmation(ToolAction.CreateReminder("stretch", 20, null)))
    }

    @Test fun `T-7 health log filters by date`() {
        val zone = ZoneOffset.UTC
        val today = LocalDate.of(2026, 10, 6)
        fun at(daysAgo: Long) = today.minusDays(daysAgo).atTime(10, 0).toInstant(zone).toEpochMilli()
        val entries = listOf(0L, 1L, 5L, 12L, 40L).mapIndexed { i, d -> HealthEntry(i.toLong(), at(d), 3, null, "", "", null) }
        assertEquals(1, filterEntries(entries, LogFilter.Today, today, zone).size)
        assertEquals(3, filterEntries(entries, LogFilter.Week, today, zone).size)
        assertEquals(4, filterEntries(entries, LogFilter.Month, today, zone).size)
        assertEquals(5, filterEntries(entries, LogFilter.All, today, zone).size)
        assertEquals(0L, filterEntries(entries, LogFilter.All, today, zone).first().id)
    }
}
