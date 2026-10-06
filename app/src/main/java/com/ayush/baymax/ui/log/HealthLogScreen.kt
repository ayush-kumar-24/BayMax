package com.ayush.baymax.ui.log

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ayush.baymax.data.HealthEntry
import com.ayush.baymax.ui.chest.painColor
import com.ayush.baymax.ui.common.Page
import com.ayush.baymax.ui.common.Pill
import com.ayush.baymax.ui.theme.BaymaxTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

enum class LogFilter(val label: String, val days: Long?) { Today("Today", 0), Week("7 days", 7), Month("30 days", 30), All("All", null) }

/** Entries inside [filter], newest first. Pure so it can be unit tested (T-7). */
fun filterEntries(entries: List<HealthEntry>, filter: LogFilter, today: LocalDate, zone: ZoneId): List<HealthEntry> {
    val days = filter.days ?: return entries.sortedByDescending { it.timestamp }
    val from = today.minusDays(days).atStartOfDay(zone).toInstant().toEpochMilli()
    return entries.filter { it.timestamp >= from }.sortedByDescending { it.timestamp }
}

private val MOODS = listOf("", "😞", "🙁", "😐", "🙂", "😄")

/** Health log (FR-21): view, filter by date, expand, delete. */
@Composable
fun HealthLogScreen(
    entries: List<HealthEntry>,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val c = BaymaxTheme.colors
    var filter by rememberSaveable { mutableStateOf(LogFilter.All) }
    var confirmDelete by remember { mutableStateOf<HealthEntry?>(null) }
    val today = LocalDate.now(zone)
    val shown = filterEntries(entries, filter, today, zone)
    val grouped = shown.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }

    Page("Health log", onBack) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(LogFilter.entries) { f -> Pill(f.label, f == filter) { filter = f } }
        }
        if (shown.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No entries yet.", style = MaterialTheme.typography.titleMedium, color = c.ink)
                Spacer(Modifier.height(6.dp))
                Text("Baymax saves each care session here.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 30.dp)) {
                grouped.forEach { (day, list) ->
                    item(key = "d$day") {
                        Text(
                            dayLabel(day, today).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = c.muted,
                            modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
                        )
                    }
                    items(list, key = { it.id }) { entry -> EntryCard(entry, zone) { confirmDelete = entry } }
                }
            }
        }
    }

    confirmDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this entry?") },
            text = { Text("It will be removed from this phone.") },
            confirmButton = { TextButton(onClick = { onDelete(e.id); confirmDelete = null }) { Text("Delete", color = c.red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = c.ink) } },
        )
    }
}

private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> day.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))
}

@Composable
private fun EntryCard(e: HealthEntry, zone: ZoneId, onDelete: () -> Unit) {
    val c = BaymaxTheme.colors
    var expanded by rememberSaveable(e.id) { mutableStateOf(false) }
    val time = Instant.ofEpochMilli(e.timestamp).atZone(zone).toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    val title = when {
        e.redFlag -> "Emergency guidance"
        e.painLevel != null -> "Pain ${e.painLevel}" + (e.symptoms.split(" · ").firstOrNull()?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
        else -> "Mood check"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse" else "Expand") { expanded = !expanded }
            .padding(14.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.Top,
    ) {
        val badge = when {
            e.redFlag -> c.red
            e.painLevel != null -> painColor(e.painLevel, 0.45f)
            else -> Color(0xFF8A7FD1)
        }
        Box(
            Modifier.size(46.dp).background(badge, RoundedCornerShape(16.dp)).semantics {
                contentDescription = e.painLevel?.let { "Pain $it" } ?: "Mood ${e.mood ?: ""}"
            },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when {
                    e.redFlag -> "!"
                    e.painLevel != null -> "${e.painLevel}"
                    else -> MOODS.getOrElse(e.mood ?: 0) { "" }
                },
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink)
            Text(
                time + (e.mood?.let { " · mood ${MOODS.getOrElse(it) { "" }}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
            )
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                if (e.symptoms.isNotBlank()) Detail("Answers", e.symptoms)
                if (e.note.isNotBlank()) Detail("Care given", e.note)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete entry", tint = c.muted)
        }
    }
}

@Composable
private fun Detail(label: String, text: String) {
    val c = BaymaxTheme.colors
    Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
    Text(text, style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.padding(bottom = 6.dp))
}
