package com.ayush.baymax.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ayush.baymax.ui.theme.BaymaxTheme

/** Full-screen page with a back arrow and a big title (Health log, Settings). */
@Composable
fun Page(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = BaymaxTheme.colors
    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.ink) }
            Text(title, style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.semantics { heading() })
        }
        content()
    }
}

/** Uppercase section label. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = BaymaxTheme.colors.muted,
        modifier = modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp).semantics { heading() },
    )
}

/** Rounded white group of rows. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = BaymaxTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(22.dp)),
        content = content,
    )
}

/** One ≥56dp row inside a [Group]. */
@Composable
fun GroupRow(
    modifier: Modifier = Modifier,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val c = BaymaxTheme.colors
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Pill chip used for filters, modes and tags. */
@Composable
fun Pill(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val c = BaymaxTheme.colors
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) c.ink else c.surface2)
            .border(1.5.dp, if (selected) c.ink else c.line, RoundedCornerShape(50))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) c.surface else c.ink, style = MaterialTheme.typography.labelLarge)
    }
}

/** Big primary / secondary / danger button. */
@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, style: ButtonStyle = ButtonStyle.Primary, onClick: () -> Unit) {
    val c = BaymaxTheme.colors
    val (bg, fg) = when (style) {
        ButtonStyle.Primary -> c.ink to c.surface
        ButtonStyle.Secondary -> c.surface2 to c.ink
        ButtonStyle.Danger -> c.red to Color.White
        ButtonStyle.DangerSoft -> c.red.copy(alpha = 0.1f) to c.red
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .then(if (style == ButtonStyle.Secondary) Modifier.border(1.5.dp, c.line, RoundedCornerShape(18.dp)) else Modifier)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.titleMedium)
    }
}

enum class ButtonStyle { Primary, Secondary, Danger, DangerSoft }

/** Bottom sheet over a dim scrim, sliding up with a soft spring. */
@Composable
fun BottomSheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = BaymaxTheme.colors
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(250)), exit = fadeOut(tween(250))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 400f)) { it },
            exit = slideOutVertically(tween(220)) { it },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(c.surface)
                    // Swallow taps so they do not dismiss.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding()
                    .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 22.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 5.dp).background(c.line, RoundedCornerShape(3.dp)))
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}

/** Small round coloured avatar with an initial. */
@Composable
fun Avatar(name: String, color: Color) {
    Box(Modifier.size(40.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

