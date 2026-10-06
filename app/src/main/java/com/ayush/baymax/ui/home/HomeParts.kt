package com.ayush.baymax.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ayush.baymax.agent.AgentState
import com.ayush.baymax.ui.theme.BaymaxTheme
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------------------

@Composable
fun HomeTopBar(
    state: AgentState,
    online: Boolean,
    lowBattery: Boolean,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(start = 18.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusPill(state, online, lowBattery)
        Spacer(Modifier.weight(1f))
        val ink = BaymaxTheme.colors.ink
        IconButton(onClick = onToggleMute) {
            Icon(
                if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = if (muted) "Unmute voice" else "Mute voice",
                tint = ink,
            )
        }
        IconButton(onClick = onOpenLog) { Icon(Icons.Rounded.MonitorHeart, contentDescription = "Health log", tint = ink) }
        IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = ink) }
    }
}

/** "● Care · Online" (UI spec section 3 status column). */
@Composable
fun StatusPill(state: AgentState, online: Boolean, lowBattery: Boolean) {
    val c = BaymaxTheme.colors
    val label = if (lowBattery && state.isAwake && state != AgentState.Emergency) "Low battery" else state.label
    val dot by animateColorAsState(
        when {
            state == AgentState.Emergency -> c.red
            !state.isAwake -> Color(0xFF9AA0A8)
            lowBattery || !online -> c.amber
            else -> c.green
        },
        label = "dot",
    )
    val blink = rememberInfiniteTransition(label = "pill")
    val a by blink.animateFloat(1f, 0.4f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "a")
    Row(
        Modifier
            .shadow(6.dp, RoundedCornerShape(50), ambientColor = Color(0x1A14181E), spotColor = Color(0x1A14181E))
            .background(c.surface, RoundedCornerShape(50))
            .border(1.dp, c.line, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Baymax is $label, ${if (online) "online" else "offline"}"
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .graphicsLayer { alpha = if (state == AgentState.Emergency) a else 1f }
                .drawBehind {
                    if (state.isAwake) drawCircle(dot.copy(alpha = 0.18f), radius = size.minDimension / 2 + 4.dp.toPx())
                }
                .background(dot, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = c.ink)
        Text(if (online) " · Online" else " · Offline", style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}

// ---------------------------------------------------------------------------------------
// Caption
// ---------------------------------------------------------------------------------------

/** Pace at which Baymax's words appear, matching his slow speech. */
private const val WORD_MS = 160L

/**
 * Subtitle block: the user's last line, then Baymax's line revealed word by word.
 * Unrevealed words are laid out but transparent so the text never reflows.
 */
@Composable
fun CaptionBlock(
    caption: String,
    captionId: Int,
    userLine: String,
    lowBattery: Boolean,
    onOpenConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = BaymaxTheme.colors
    val words = remember(caption) { caption.split(' ').filter { it.isNotEmpty() } }
    var revealed by remember { mutableIntStateOf(words.size) }
    LaunchedEffect(captionId) {
        revealed = 0
        for (i in words.indices) {
            delay(WORD_MS)
            revealed = i + 1
        }
    }
    val text = remember(words, revealed, c.ink) {
        buildAnnotatedString {
            words.forEachIndexed { i, w ->
                withStyle(SpanStyle(color = if (i < revealed) c.ink else Color.Transparent)) { append(w) }
                if (i < words.lastIndex) append(' ')
            }
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = Color(0x1014181E), spotColor = Color(0x1014181E))
            .background(c.surface, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .clickable(onClickLabel = "Show conversation", onClick = onOpenConversation)
            .padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 4.dp)
            .heightIn(min = 110.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp).size(40.dp, 5.dp).background(c.line, RoundedCornerShape(3.dp)))
        Text(
            buildAnnotatedString {
                if (userLine.isNotEmpty()) {
                    append("You: ")
                    withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.Bold)) { append(userLine) }
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = c.muted,
            maxLines = 1,
            modifier = Modifier.heightIn(min = 18.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            fontStyle = if (lowBattery) FontStyle.Italic else FontStyle.Normal,
            modifier = Modifier
                .heightIn(min = 56.dp)
                .semantics {
                    contentDescription = caption
                    liveRegion = LiveRegionMode.Polite
                },
        )
    }
}

// ---------------------------------------------------------------------------------------
// Quick-reply chips
// ---------------------------------------------------------------------------------------

@Composable
fun QuickChips(chips: List<QuickChip>, onChip: (QuickChip) -> Unit, modifier: Modifier = Modifier) {
    val c = BaymaxTheme.colors
    LazyRow(
        modifier.fillMaxWidth().background(c.surface).heightIn(min = 52.dp),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(chips, key = { _, chip -> chip.text }) { i, chip ->
            // Staggered pop-in
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(i * 50L); shown = true }
            val p by animateFloatAsState(if (shown) 1f else 0f, tween(300), label = "chip")
            val (bg, fg, border) = when (chip.style) {
                QuickChip.Style.Primary -> Triple(c.ink, c.surface, c.ink)
                QuickChip.Style.Danger -> Triple(c.red, Color.White, c.red)
                QuickChip.Style.Normal -> Triple(c.surface2, c.ink, c.line)
            }
            Box(
                Modifier
                    .graphicsLayer { alpha = p; translationY = (1f - p) * 8.dp.toPx() }
                    .height(42.dp)
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .border(1.5.dp, border, RoundedCornerShape(50))
                    .clickable(role = Role.Button) { onChip(chip) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(chip.text, color = fg, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Input bar
// ---------------------------------------------------------------------------------------

@Composable
fun InputBar(
    listening: Boolean,
    onSend: (String) -> Unit,
    onMic: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = BaymaxTheme.colors
    var text by remember { mutableStateOf("") }
    val send = {
        if (text.isNotBlank()) {
            onSend(text.trim())
            text = ""
        }
    }
    Row(
        modifier.fillMaxWidth().background(c.surface).padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .background(c.surface2, RoundedCornerShape(26.dp))
                .border(1.5.dp, c.line, RoundedCornerShape(26.dp))
                .padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Text("Talk to Baymax…", style = MaterialTheme.typography.bodyLarge, color = c.muted)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink),
                    cursorBrush = SolidColor(c.red),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { onFocusChange(it.isFocused) }
                        .semantics { contentDescription = "Message to Baymax" },
                )
            }
            val sendScale by animateFloatAsState(if (text.isNotBlank()) 1f else 0f, spring(dampingRatio = 0.6f), label = "send")
            Box(
                Modifier
                    .size(40.dp)
                    .graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                    .clip(CircleShape)
                    .background(c.ink)
                    .clickable(enabled = text.isNotBlank(), role = Role.Button, onClickLabel = "Send", onClick = send),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ArrowUpward, contentDescription = "Send", tint = c.surface, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        MicButton(listening, onMic)
    }
}

@Composable
fun MicButton(listening: Boolean, onClick: () -> Unit) {
    val c = BaymaxTheme.colors
    val ripple = rememberInfiniteTransition(label = "mic")
    val r1 by ripple.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "r1")
    val bg by animateColorAsState(if (listening) c.red else c.surface, label = "micBg")
    val fg by animateColorAsState(if (listening) Color.White else c.ink, label = "micFg")
    Box(
        Modifier
            .size(60.dp)
            .drawBehind {
                if (listening) {
                    for (phase in floatArrayOf(r1, (r1 + 0.5f) % 1f)) {
                        drawCircle(
                            c.red.copy(alpha = 0.8f * (1f - phase)),
                            radius = size.minDimension / 2 * (1f + 0.6f * phase),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                        )
                    }
                }
            }
            .shadow(8.dp, CircleShape, ambientColor = Color(0x1A14181E), spotColor = Color(0x1A14181E))
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, if (listening) c.red else c.line, CircleShape)
            .clickable(role = Role.Button, onClickLabel = if (listening) "Stop listening" else "Speak", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Mic, contentDescription = if (listening) "Stop listening" else "Speak to Baymax", tint = fg, modifier = Modifier.size(26.dp))
    }
}

