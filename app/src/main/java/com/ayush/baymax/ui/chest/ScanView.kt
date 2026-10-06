package com.ayush.baymax.ui.chest

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayush.baymax.ui.home.HealthReadings
import com.ayush.baymax.ui.theme.BaymaxTheme

/** Body scan: outline figure with a sweeping line, then readings from Health Connect. */
@Composable
fun ScanView(progress: Float, readings: HealthReadings?) {
    val c = BaymaxTheme.colors
    val done = progress >= 1f
    val sweep = rememberInfiniteTransition(label = "sweep")
    val y by sweep.animateFloat(0.04f, 0.94f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "y")
    val pulse by sweep.animateFloat(3f, 6f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "pulse")
    val readingsAlpha by animateFloatAsState(if (done) 1f else 0.25f, tween(400), label = "rd")

    Column(Modifier.fillMaxSize()) {
        ChestHeader(if (done) "Scan complete" else "Scanning") {
            Text(if (done) "✓" else "${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = c.cyan)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(Modifier.fillMaxSize()) {
                // Figure on a 100 x 200 grid, centred.
                val k = size.height / 200f
                val ox = (size.width - 100f * k) / 2f
                translate(ox, 0f) {
                    val stroke = Stroke(width = 1.6f * k, join = StrokeJoin.Round)
                    drawCircle(c.cyan, radius = 14f * k, center = Offset(50f * k, 24f * k), style = stroke)
                    drawPath(figurePath(k), c.cyan, style = stroke)
                    drawCircle(c.cyan.copy(alpha = 0.6f), radius = pulse * k, center = Offset(50f * k, 70f * k))
                }
                if (!done) {
                    val ly = size.height * y
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, c.cyan.copy(alpha = 0.35f), Color.Transparent),
                            startY = ly - 14.dp.toPx(), endY = ly + 14.dp.toPx(),
                        ),
                        topLeft = Offset(size.width * 0.08f, ly - 14.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.84f, 28.dp.toPx()),
                    )
                    drawLine(c.cyan, Offset(size.width * 0.08f, ly), Offset(size.width * 0.92f, ly), strokeWidth = 3.dp.toPx())
                }
            }
        }
        Column(
            Modifier.padding(top = 8.dp).graphicsLayer { alpha = readingsAlpha },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Reading("Heart rate", readings?.heartRateBpm?.let { "$it" } ?: "—", "bpm", Modifier.weight(1f))
                Reading("Steps today", readings?.stepsToday?.let { "%,d".format(it) } ?: "—", null, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Reading("Sleep", readings?.sleepMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "—", null, Modifier.weight(1f))
                Reading("Source", readings?.source ?: "Health Connect", null, Modifier.weight(1f), small = true)
            }
        }
    }
}

@Composable
private fun Reading(label: String, value: String, unit: String?, modifier: Modifier, small: Boolean = false) {
    val c = BaymaxTheme.colors
    Column(
        modifier
            .background(c.cyan.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .border(1.dp, c.cyanDim, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label.uppercase(), color = c.glassMuted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
        Row {
            Text(value, color = c.glassText, fontSize = if (small) 13.sp else 18.sp, fontWeight = FontWeight.ExtraBold)
            if (unit != null) Text(" $unit", color = c.glassText, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun figurePath(k: Float) = Path().apply {
    fun m(x: Float, y: Float) = moveTo(x * k, y * k)
    fun l(x: Float, y: Float) = lineTo(x * k, y * k)
    m(50f, 38f); l(50f, 44f)
    m(30f, 50f)
    quadraticBezierTo(50f * k, 42f * k, 70f * k, 50f * k)
    l(76f, 102f); l(68f, 104f); l(62f, 64f); l(62f, 122f); l(58f, 192f); l(50f, 192f)
    l(46f, 130f); l(42f, 192f); l(34f, 192f); l(30f, 122f); l(30f, 64f); l(24f, 104f); l(16f, 102f)
    close()
}
