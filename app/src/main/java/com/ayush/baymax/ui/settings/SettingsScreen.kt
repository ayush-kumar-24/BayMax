package com.ayush.baymax.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ayush.baymax.agent.ReminderParser
import com.ayush.baymax.data.AppSettings
import com.ayush.baymax.data.ContactApp
import com.ayush.baymax.data.MemoryFact
import com.ayush.baymax.data.Mode
import com.ayush.baymax.data.Reminder
import com.ayush.baymax.data.ThemeMode
import com.ayush.baymax.data.TrustedContact
import com.ayush.baymax.ui.common.Avatar
import com.ayush.baymax.ui.common.BigButton
import com.ayush.baymax.ui.common.ButtonStyle
import com.ayush.baymax.ui.common.Group
import com.ayush.baymax.ui.common.GroupRow
import com.ayush.baymax.ui.common.Page
import com.ayush.baymax.ui.common.Pill
import com.ayush.baymax.ui.common.SectionLabel
import com.ayush.baymax.ui.theme.BaymaxTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

enum class HealthConnectStatus(val label: String) {
    Unavailable("Health Connect is not installed on this phone"),
    NeedsPermission("Not connected"),
    Connected("Connected: steps, sleep, heart rate"),
}

@Immutable
data class SettingsActions(
    val update: ((AppSettings) -> AppSettings) -> Unit = {},
    val addContact: (TrustedContact) -> Unit = {},
    val deleteContact: (Long) -> Unit = {},
    val deleteReminder: (Long) -> Unit = {},
    val deleteMemory: (Long) -> Unit = {},
    val connectHealth: () -> Unit = {},
    val forgetEverything: () -> Unit = {},
    val showNotice: () -> Unit = {},
)

private val AVATAR_COLORS = listOf(Color(0xFFE8434A), Color(0xFF3B82C4), Color(0xFF2FA86B), Color(0xFF8A7FD1), Color(0xFFE3A21A))

/** Settings (UI spec 4.1): voice, care, contacts, health data, reminders, memory, modes, theme, privacy. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    contacts: List<TrustedContact>,
    reminders: List<Reminder>,
    memories: List<MemoryFact>,
    healthStatus: HealthConnectStatus,
    brainStatus: String,
    actions: SettingsActions,
    onBack: () -> Unit,
) {
    val c = BaymaxTheme.colors
    var confirmForget by remember { mutableStateOf(false) }
    var addingContact by remember { mutableStateOf(false) }

    Page("Settings", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 40.dp)) {

            SectionLabel("Voice")
            Group {
                GroupRow(divider = false) {
                    Text("Speak replies aloud", Modifier.weight(1f), color = c.ink)
                    Switch(
                        checked = settings.voiceOn,
                        onCheckedChange = { on -> actions.update { it.copy(voiceOn = on) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = c.green),
                    )
                }
                SliderRow("Speech rate", settings.speechRate, 0.6f..1.2f) { v -> actions.update { it.copy(speechRate = v) } }
                SliderRow("Pitch", settings.pitch, 0.5f..1.3f) { v -> actions.update { it.copy(pitch = v) } }
            }

            SectionLabel("Care")
            Group {
                GroupRow(divider = false) {
                    Column(Modifier.weight(1f)) {
                        Text("Distress words", color = c.ink)
                        Text("Saying one of these wakes Baymax", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            settings.distressWords.sorted().forEach { w ->
                                Pill("$w  ✕", selected = false) {
                                    if (settings.distressWords.size > 1) actions.update { it.copy(distressWords = it.distressWords - w) }
                                }
                            }
                        }
                        var newWord by remember { mutableStateOf("") }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newWord,
                                onValueChange = { newWord = it.lowercase().filter { ch -> ch.isLetter() || ch == '\'' }.take(20) },
                                label = { Text("Add a word") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).padding(top = 8.dp),
                            )
                            TextButton(onClick = {
                                if (newWord.isNotBlank()) actions.update { it.copy(distressWords = it.distressWords + newWord.trim()) }
                                newWord = ""
                            }) { Text("Add", color = c.ink) }
                        }
                    }
                }
                GroupRow {
                    Column(Modifier.weight(1f)) {
                        Text("Emergency number", color = c.ink)
                        Text("Used by the call button", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    var number by remember(settings.emergencyNumber) { mutableStateOf(settings.emergencyNumber) }
                    OutlinedTextField(
                        value = number,
                        onValueChange = { v ->
                            number = v.filter { it.isDigit() || it == '+' }.take(15)
                            if (number.isNotEmpty()) actions.update { it.copy(emergencyNumber = number) }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.width(110.dp),
                    )
                }
            }

            SectionLabel("Trusted contacts")
            Group {
                contacts.forEachIndexed { i, contact ->
                    GroupRow(divider = i > 0) {
                        Avatar(contact.name, AVATAR_COLORS[(contact.id % AVATAR_COLORS.size).toInt()])
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(contact.name, color = c.ink, style = MaterialTheme.typography.titleMedium)
                            Text("${contact.preferredApp.label} · ${contact.phone}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                        IconButton(onClick = { actions.deleteContact(contact.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove ${contact.name}", tint = c.muted)
                        }
                    }
                }
                if (addingContact) {
                    AddContactForm(divider = contacts.isNotEmpty(), onAdd = { actions.addContact(it); addingContact = false }, onCancel = { addingContact = false })
                } else {
                    GroupRow(divider = contacts.isNotEmpty(), onClick = { addingContact = true }) {
                        Text("+ Add contact", color = c.ink, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            SectionLabel("Health data")
            Group {
                GroupRow(divider = false, onClick = if (healthStatus == HealthConnectStatus.Connected) null else actions.connectHealth) {
                    Column(Modifier.weight(1f)) {
                        Text("Health Connect", color = c.ink)
                        Text(healthStatus.label, style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    if (healthStatus == HealthConnectStatus.NeedsPermission) Text("Connect", color = c.red, style = MaterialTheme.typography.labelLarge)
                }
            }

            SectionLabel("Reminders")
            Group {
                if (reminders.isEmpty()) {
                    GroupRow(divider = false) {
                        Text("Ask Baymax: “Remind me to drink water every two hours.”", color = c.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                reminders.forEachIndexed { i, r ->
                    GroupRow(divider = i > 0) {
                        Column(Modifier.weight(1f)) {
                            Text(r.text.replaceFirstChar { it.uppercase() }, color = c.ink)
                            Text(reminderSchedule(r), style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                        IconButton(onClick = { actions.deleteReminder(r.id) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Delete reminder", tint = c.muted)
                        }
                    }
                }
            }

            SectionLabel("What Baymax remembers")
            Group {
                if (memories.isEmpty()) {
                    GroupRow(divider = false) {
                        Text("Nothing yet. Tell Baymax your name or anything he should know.", color = c.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                memories.forEachIndexed { i, m ->
                    GroupRow(divider = i > 0) {
                        Text(m.text, Modifier.weight(1f), color = c.ink)
                        IconButton(onClick = { actions.deleteMemory(m.id) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Forget this", tint = c.muted)
                        }
                    }
                }
            }

            SectionLabel("Modes")
            Group {
                GroupRow(divider = false) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Care · always on", selected = true, enabled = false) {}
                        Mode.entries.forEach { m ->
                            val on = m in settings.enabledChips
                            Pill(m.label, on) {
                                actions.update { it.copy(enabledChips = if (on) it.enabledChips - m else it.enabledChips + m) }
                            }
                        }
                    }
                }
            }

            SectionLabel("Appearance")
            Group {
                GroupRow(divider = false) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { t ->
                            Pill(t.name, t == settings.theme) { actions.update { it.copy(theme = t) } }
                        }
                    }
                }
            }

            SectionLabel("Brain")
            Group {
                GroupRow(divider = false) {
                    Column {
                        Text("Language model", color = c.ink)
                        Text(brainStatus, style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
            }

            SectionLabel("Privacy")
            Group {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Your health log, memories and contacts stay on this phone. Only your messages to Baymax, " +
                            "the last ten turns and up to five memories are sent to the language model.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.muted,
                    )
                    Spacer(Modifier.height(12.dp))
                    BigButton("Forget everything", style = ButtonStyle.DangerSoft) { confirmForget = true }
                }
            }

            Spacer(Modifier.height(18.dp))
            TextButton(onClick = actions.showNotice) {
                Text(
                    "Baymax is not a medical device. He does not diagnose or prescribe. In an emergency, call your local emergency number.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                )
            }
        }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget everything?") },
            text = { Text("This erases your health log, conversation, memories, reminders, contacts and settings from this phone. It cannot be undone.") },
            confirmButton = { TextButton(onClick = { actions.forgetEverything(); confirmForget = false }) { Text("Forget everything", color = c.red) } },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel", color = c.ink) } },
        )
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val c = BaymaxTheme.colors
    var local by remember(value) { mutableStateOf(value) }
    GroupRow {
        Text(label, Modifier.width(110.dp), color = c.ink)
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local) },
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = c.red, activeTrackColor = c.red),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AddContactForm(divider: Boolean, onAdd: (TrustedContact) -> Unit, onCancel: () -> Unit) {
    val c = BaymaxTheme.colors
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var app by remember { mutableStateOf(ContactApp.WhatsApp) }
    GroupRow(divider = divider) {
        Column(Modifier.fillMaxWidth()) {
            OutlinedTextField(name, { name = it.take(30) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                phone,
                { phone = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' }.take(18) },
                label = { Text("Phone, with country code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ContactApp.entries.forEach { a -> Pill(a.label, a == app) { app = a } }
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("Save", Modifier.weight(1f)) {
                    if (name.isNotBlank() && phone.count { it.isDigit() } >= 6) onAdd(TrustedContact(name = name.trim(), phone = phone.trim(), preferredApp = app))
                }
                BigButton("Cancel", Modifier.weight(1f), style = ButtonStyle.Secondary, onClick = onCancel)
            }
            Text("Baymax only drafts messages. You always confirm before anything is sent.", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private fun reminderSchedule(r: Reminder): String {
    val time = Instant.ofEpochMilli(r.firstTime).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
    return r.repeatIntervalMinutes?.let { "Every ${ReminderParser.describe(it).removePrefix("one ")} · from $time" } ?: "Once · $time"
}
