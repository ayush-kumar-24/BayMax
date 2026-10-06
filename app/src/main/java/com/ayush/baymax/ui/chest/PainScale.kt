package com.ayush.baymax.ui.chest

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayush.baymax.ui.theme.BaymaxTheme

/** Hue for a pain level: 1 is green, 10 is red. Shared with the health log badges. */
fun painColor(level: Int, lightness: Float = 0.52f): Color =
    Color.hsl(hue = (130f - (level - 1) * 14.4f).coerceIn(0f, 130f), saturation = 0.70f, lightness = lightness)

/** 1–10 pain scale with faces (FR-8). Each face is a ≥48dp tap target. */
@Composable
fun PainScale(selected: Int?, onSelect: (Int) -> Unit) {
    val c = BaymaxTheme.colors
    Column(Modifier.fillMaxSize()) {
        ChestHeader("Pain scale") {
            Text("1 – 10", style = MaterialTheme.typography.labelSmall, color = c.cyan)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            for (row in 0 until 2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (col in 1..5) {
                        val n = row * 5 + col
                        PainFace(n, n == selected, Modifier.weight(1f)) { onSelect(n) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("No hurt", color = c.glassMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("Worst hurt", color = c.glassMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PainFace(level: Int, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = BaymaxTheme.colors
    val scale by animateFloatAsState(if (isSelected) 1.12f else 1f, spring(dampingRatio = 0.5f), label = "pfScale")
    val bg by animateColorAsState(if (isSelected) c.cyan.copy(alpha = 0.22f) else Color.Transparent, label = "pfBg")
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "Pain $level of 10"
                this.selected = isSelected
            }
            .heightIn(min = 64.dp)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FaceIcon(level, Modifier.size(40.dp))
        Spacer(Modifier.size(3.dp))
        Text("$level", color = c.glassText, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

/** Smiley that gets sadder from 1 to 10; tears at 9–10. Drawn on a 40-unit grid. */
@Composable
fun FaceIcon(level: Int, modifier: Modifier = Modifier) {
    val fill = painColor(level)
    val ink = Color(0xFF112222)
    Canvas(modifier) {
        val k = size.minDimension / 40f
        drawCircle(fill, radius = 18f * k, center = Offset(20f * k, 20f * k))
        drawCircle(ink, radius = 2.4f * k, center = Offset(14f * k, 16f * k))
        drawCircle(ink, radius = 2.4f * k, center = Offset(26f * k, 16f * k))
        val curve = 10f - (level - 1) * 2.2f
        val mouth = Path().apply {
            moveTo(12f * k, (27f - curve / 2) * k)
            quadraticBezierTo(20f * k, (27f + curve) * k, 28f * k, (27f - curve / 2) * k)
        }
        drawPath(mouth, ink, style = Stroke(width = 2.4f * k, cap = StrokeCap.Round))
        if (level >= 9) {
            for (x in floatArrayOf(13f, 27f)) {
                val tear = Path().apply {
                    moveTo(x * k, 21f * k)
                    quadraticBezierTo((x - 2.5f) * k, 26f * k, x * k, 28f * k)
                    quadraticBezierTo((x + 2.5f) * k, 26f * k, x * k, 21f * k)
                    close()
                }
                drawPath(tear, Color(0xFF7FD3FF))
            }
        }
    }
}
