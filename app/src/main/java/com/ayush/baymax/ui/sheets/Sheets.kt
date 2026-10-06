package com.ayush.baymax.ui.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.ayush.baymax.data.ChatMessage
import com.ayush.baymax.data.ContactApp
import com.ayush.baymax.ui.baymax.HeadShape
import com.ayush.baymax.ui.baymax.vinyl
import com.ayush.baymax.ui.common.BigButton
import com.ayush.baymax.ui.common.BottomSheet
import com.ayush.baymax.ui.common.ButtonStyle
import com.ayush.baymax.ui.home.MessageDraft
import com.ayush.baymax.ui.theme.BaymaxTheme

/** Full conversation (FR-5), last 30 days. */
@Composable
fun ConversationSheet(visible: Boolean, messages: List<ChatMessage>, onDismiss: () -> Unit) {
    val c = BaymaxTheme.colors
    BottomSheet(visible, onDismiss) {
        Text("Conversation", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(bottom = 8.dp))
        val list = rememberLazyListState()
        LaunchedEffect(messages.size, visible) { if (messages.isNotEmpty()) list.scrollToItem(messages.lastIndex) }
        if (messages.isEmpty()) {
            Text("No conversation yet.", color = c.muted, modifier = Modifier.padding(vertical = 30.dp).align(Alignment.CenterHorizontally))
        }
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            items(messages, key = { it.id }) { m ->
                when (m.role) {
                    ChatMessage.Role.Tool -> Text(
                        m.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                    else -> {
                        val user = m.role == ChatMessage.Role.User
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                            Text(
                                m.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (user) c.surface else c.ink,
                                modifier = Modifier
                                    .widthIn(max = 290.dp)
                                    .background(
                                        if (user) c.ink else c.surface2,
                                        RoundedCornerShape(20.dp, 20.dp, if (user) 6.dp else 20.dp, if (user) 20.dp else 6.dp),
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** FR-26: Baymax drafts, the user edits and confirms. Nothing is sent without this tap. */
@Composable
fun DraftSheet(draft: MessageDraft?, onSend: (String, ContactApp) -> Unit, onCancel: () -> Unit) {
    val c = BaymaxTheme.colors
    var text by remember(draft) { mutableStateOf(draft?.text.orEmpty()) }
    BottomSheet(draft != null, onCancel) {
        val d = draft ?: return@BottomSheet
        Text("Message ${d.contact.name}?", style = MaterialTheme.typography.headlineSmall, color = c.ink)
        Text("Baymax drafted this. Nothing is sent until you confirm.", style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Spacer(Modifier.height(14.dp))
        val first = d.contact.preferredApp
        val second = ContactApp.entries.first { it != first }
        BigButton("Send via ${first.label}") { onSend(text, first) }
        Spacer(Modifier.height(10.dp))
        BigButton("Send via ${second.label}", style = ButtonStyle.Secondary) { onSend(text, second) }
        Spacer(Modifier.height(10.dp))
        BigButton("Cancel", style = ButtonStyle.Secondary, onClick = onCancel)
    }
}

/** First-launch "not medical advice" notice (NFR-18). */
@Composable
fun NoticeSheet(visible: Boolean, onAccept: () -> Unit) {
    val c = BaymaxTheme.colors
    BottomSheet(visible, onDismiss = {}) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(110.dp, 74.dp).vinyl(HeadShape)) {
            Canvas(Modifier.size(110.dp, 74.dp)) {
                val y = size.height * 0.52f
                drawLine(c.face, Offset(size.width * 0.3f, y), Offset(size.width * 0.7f, y), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(c.face, 7.dp.toPx(), Offset(size.width * 0.3f, y))
                drawCircle(c.face, 7.dp.toPx(), Offset(size.width * 0.7f, y))
            }
        }
        Text("Hello. I am Baymax.", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
        Text(
            "I can comfort you, check on you and give general self-care tips. I am not a doctor. I do not diagnose or prescribe. " +
                "In an emergency, call your local emergency number.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.muted,
        )
        Spacer(Modifier.height(18.dp))
        BigButton("I understand", onClick = onAccept)
    }
}
