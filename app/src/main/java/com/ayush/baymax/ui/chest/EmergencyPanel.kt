package com.ayush.baymax.ui.chest

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayush.baymax.ui.theme.BaymaxTheme

/** Emergency call panel (FR-15). No network needed: it only opens the dialer. */
@Composable
fun EmergencyPanel(
    number: String,
    contactName: String?,
    onCall: () -> Unit,
    onMessageFriend: () -> Unit,
    onImOkay: () -> Unit,
) {
    val c = BaymaxTheme.colors
    val ring = rememberInfiniteTransition(label = "ring")
    val r by ring.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "r")
    val shape = RoundedCornerShape(22.dp)

    Column(Modifier.fillMaxSize()) {
        ChestHeader("Emergency") {
            Text(
                "WORKS OFFLINE",
                color = c.glassText,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .background(c.cyan.copy(alpha = 0.15f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .drawBehind {
                    // Expanding ring around the call button
                    val grow = 18.dp.toPx() * r
                    drawRoundRect(
                        color = Color(0xFFEF3B43).copy(alpha = 0.6f * (1f - r)),
                        topLeft = androidx.compose.ui.geometry.Offset(-grow, -grow),
                        size = androidx.compose.ui.geometry.Size(size.width + grow * 2, size.height + grow * 2),
                        cornerRadius = CornerRadius(22.dp.toPx() + grow),
                    )
                }
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFFEF3B43), Color(0xFFB5161E))))
                .clickable(role = Role.Button, onClickLabel = "Call $number", onClick = onCall),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                Text("Call $number", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Text("Tap to open dialer", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(if (contactName != null) "Message $contactName" else "Message a friend", Modifier.weight(1f), onMessageFriend)
            GlassButton("I am okay now", Modifier.weight(1f), onImOkay)
        }
    }
}

@Composable
private fun GlassButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .height(50.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
