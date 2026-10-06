package com.ayush.baymax.ui.chest

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ayush.baymax.ui.home.ChestContent
import com.ayush.baymax.ui.theme.BaymaxTheme

private val PanelShape = RoundedCornerShape(28.dp)

/**
 * Glowing screen on Baymax's chest. Shows the pain scale, the scan or the emergency call
 * panel, and animates in with a soft pop.
 */
@Composable
fun ChestPanel(
    content: ChestContent,
    onPainSelected: (Int) -> Unit,
    onCall: () -> Unit,
    onMessageFriend: () -> Unit,
    onImOkay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep the last real content so the exit animation still has something to draw.
    var shown by remember { mutableStateOf<ChestContent>(ChestContent.None) }
    if (content != ChestContent.None) shown = content

    AnimatedVisibility(
        visible = content != ChestContent.None,
        enter = fadeIn(tween(350)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = 300f), initialScale = 0.88f),
        exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.92f),
        modifier = modifier,
    ) {
        val c = BaymaxTheme.colors
        Box(
            Modifier
                .fillMaxSize()
                // White vinyl rim around the screen
                .border(6.dp, Color.White.copy(alpha = 0.55f), PanelShape)
                .padding(6.dp)
                .shadow(20.dp, PanelShape, spotColor = Color(0x400A141E))
                .clip(PanelShape)
                .background(c.glass)
                .border(1.dp, c.cyanDim, PanelShape)
                .scanlines(c.cyan.copy(alpha = 0.035f))
                .padding(16.dp),
        ) {
            when (val s = shown) {
                is ChestContent.PainScale -> PainScale(s.selected, onPainSelected)
                is ChestContent.Scan -> ScanView(s.progress, s.readings)
                is ChestContent.Emergency -> EmergencyPanel(s.number, s.contactName, onCall, onMessageFriend, onImOkay)
                ChestContent.None -> Unit
            }
        }
    }
}

/** Header row used by every chest view: cyan uppercase title left, detail right. */
@Composable
internal fun ChestHeader(title: String, detail: @Composable () -> Unit = {}) {
    val c = BaymaxTheme.colors
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = c.cyan, modifier = Modifier.weight(1f))
        detail()
    }
}

/** Faint CRT lines over the glass. */
private fun Modifier.scanlines(color: Color) = drawWithContent {
    drawContent()
    var y = 0f
    val step = 4.dp.toPx()
    val h = 2.dp.toPx()
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = h)
        y += step
    }
}

