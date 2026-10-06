package com.ayush.baymax.ui.baymax

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ayush.baymax.ui.theme.BaymaxTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.random.Random

/** Layout is designed on a 390dp-wide frame and scaled to the real width. */
private const val DESIGN_W = 390f

private val DeflateEasing = CubicBezierEasing(0.6f, 0f, 0.8f, 0.4f)
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * The whole top half of Home: backdrop, red charging case, Baymax himself, the chest panel
 * slot and the emergency banner.
 *
 * Animations follow design/UI_SPEC.md section 3: inflate on wake (body, arms, then head with
 * overshoot), deflate into the case, breathing, random blinks every 3–6 s, one blink per reply,
 * head tilt for questions, glance at the input, and a droopy sway on low battery.
 */
@Composable
fun BaymaxStage(
    awake: Boolean,
    emergency: Boolean,
    tilt: Boolean,
    lowBattery: Boolean,
    listening: Boolean,
    lookingAtInput: Boolean,
    blinkTick: Int,
    onCaseTap: () -> Unit,
    onHeadTap: () -> Unit,
    modifier: Modifier = Modifier,
    chest: @Composable (Modifier) -> Unit = {},
) {
    val c = BaymaxTheme.colors
    val stageCenter by animateColorAsState(if (emergency) c.emergencyStageCenter else c.stageCenter, tween(600), label = "stageC")
    val stageEdge by animateColorAsState(if (emergency) c.emergencyStageEdge else c.stageEdge, tween(600), label = "stageE")

    // --- inflate / deflate -------------------------------------------------------------
    val body = remember { Animatable(if (awake) 1f else 0f) }
    val head = remember { Animatable(if (awake) 1f else 0f) }
    LaunchedEffect(awake) {
        if (awake) {
            launch { body.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 90f)) }
            delay(450)
            head.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 140f))
        } else {
            launch { head.animateTo(0f, tween(1000, easing = DeflateEasing)) }
            delay(250)
            body.animateTo(0f, tween(1100, easing = DeflateEasing))
        }
    }

    // --- idle micro-motion -----------------------------------------------------------
    val loop = rememberInfiniteTransition(label = "loop")
    val breathe by loop.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(if (lowBattery) 2600 else 4200), RepeatMode.Reverse),
        label = "breathe",
    )
    val bob by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2500), RepeatMode.Reverse), label = "bob")
    val led by loop.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse), label = "led")

    // --- face ---------------------------------------------------------------------------
    val tiltDeg by animateFloatAsState(
        if (tilt) -9f else 0f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow),
        label = "tilt",
    )
    val droop by animateFloatAsState(if (lowBattery && awake) 1f else 0f, tween(700), label = "droop")
    val eyesOpen by animateFloatAsState(
        when {
            head.value < 0.85f -> 0.06f
            lowBattery -> 0.42f
            else -> 1f
        },
        spring(dampingRatio = 0.45f, stiffness = 300f),
        label = "eyes",
    )
    val look by animateFloatAsState(if (lookingAtInput) 5f else 0f, tween(500), label = "look")
    val blink = remember { Animatable(1f) }
    suspend fun doBlink() {
        try {
            blink.animateTo(0.06f, tween(85))
            blink.animateTo(1f, tween(85))
        } catch (e: CancellationException) {
            // Another blink took over the Animatable. Only rethrow if this coroutine itself is
            // being cancelled, otherwise the random-blink loop would silently die.
            if (!currentCoroutineContext().isActive) throw e
        }
    }
    LaunchedEffect(blinkTick) { if (awake && blinkTick > 0) doBlink() }
    LaunchedEffect(awake) {
        while (awake) {
            delay(3000L + Random.nextLong(3000L))
            doBlink()
        }
    }

    // --- case ---------------------------------------------------------------------------
    val caseAway by animateFloatAsState(if (awake) 1f else 0f, tween(800, easing = CubicBezierEasing(0.5f, 0f, 0.3f, 1f)), label = "case")

    BoxWithConstraints(
        modifier
            .clipToBounds()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(stageCenter, stageEdge),
                        center = Offset(size.width / 2, size.height * 0.35f),
                        radius = max(size.width, size.height) * 0.75f,
                    ),
                )
            },
    ) {
        val s = maxWidth.value / DESIGN_W
        fun u(v: Float): Dp = (v * s).dp
        val stageH = maxHeight
        val noRipple = remember { MutableInteractionSource() }

        // Idle hint
        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = stageH * 0.30f)
                .graphicsLayer { alpha = 1f - caseAway },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Baymax is resting.", style = MaterialTheme.typography.headlineSmall, color = c.ink)
            Spacer(Modifier.height(6.dp))
            Text("Say “ow” or tap the case.", style = MaterialTheme.typography.bodyLarge, color = c.muted, fontWeight = FontWeight.SemiBold)
        }

        // Charging case
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = u(34f))
                .size(u(216f), u(128f))
                .graphicsLayer {
                    translationY = u(190f).toPx() * caseAway
                    alpha = 1f - caseAway
                }
                .semantics { contentDescription = "Activate Baymax" }
                .clickable(enabled = !awake, role = Role.Button, interactionSource = noRipple, indication = null, onClick = onCaseTap),
        ) {
            ChargingCase(led = led, listening = listening)
        }

        val bp = body.value
        val hp = head.value
        val bodyAlpha = (bp * 8f).coerceIn(0f, 1f)
        val headAlpha = (hp * 5f).coerceIn(0f, 1f)

        if (bodyAlpha > 0f) {
            // Arms, behind the body
            for (side in listOf(-1f, 1f)) {
                Box(
                    Modifier
                        .offset(x = if (side < 0) u(-78f) else maxWidth - u(150f - 78f), y = u(250f))
                        .size(u(150f), u(420f))
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            translationY = (1f - bp) * u(400f).toPx()
                            rotationZ = -side * lerp(30f, 9f, bp.coerceAtMost(1f))
                            scaleX = lerp(0.4f, 1f, bp); scaleY = scaleX
                            alpha = bodyAlpha
                        }
                        .vinyl(ArmShape, shadowStrength = 0.24f),
                )
            }

            // Torso
            Box(
                Modifier
                    .offset(x = u(20f), y = u(128f))
                    .size(maxWidth - u(40f), u(560f))
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        translationY = (1f - bp) * u(420f).toPx()
                        scaleX = lerp(0.32f, 1f, bp) * (1f + 0.008f * breathe)
                        scaleY = lerp(0.06f, 1f, bp) * (1f + 0.014f * breathe)
                        alpha = bodyAlpha
                    },
            ) {
                Box(Modifier.fillMaxSize().vinyl(BodyShape))
                // Access port on the upper chest, as in the film.
                Box(
                    Modifier
                        .offset(u(58f), u(92f))
                        .size(u(26f))
                        .shadow(1.dp, CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFFF7F8FA), Color(0xFFDFE2E7))), CircleShape),
                )
            }
        }

        // Head
        if (headAlpha > 0f) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = u(26f))
                    .size(u(204f), u(136f))
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0.9f)
                        translationY = (1f - hp) * u(420f).toPx() + u(2f).toPx() * bob + u(6f).toPx() * droop
                        val sc = lerp(0.3f, 1f, hp)
                        scaleX = sc; scaleY = sc
                        rotationZ = tiltDeg + droop * (8f + 4f * breathe)
                        alpha = headAlpha
                    }
                    .shadow(10.dp, HeadShape, clip = false, ambientColor = Color(0x22283246), spotColor = Color(0x22283246))
                    .vinyl(HeadShape)
                    .semantics { contentDescription = "Baymax" }
                    .clickable(interactionSource = noRipple, indication = null, onClick = onHeadTap),
            ) {
                BaymaxFace(
                    eyeScaleY = eyesOpen * blink.value,
                    lookX = 0f,
                    lookY = look + droop * 3f,
                    modifier = Modifier.fillMaxSize(),
                    ink = c.face,
                )
            }
        }

        // Chest screen (pain scale, scan, emergency)
        chest(
            Modifier
                .padding(start = u(30f), end = u(30f), top = u(196f), bottom = u(16f))
                .fillMaxSize(),
        )

        // Emergency banner
        AnimatedVisibility(
            visible = emergency,
            enter = slideInVertically(spring(dampingRatio = 0.6f)) { -it * 2 } + fadeIn(),
            exit = slideOutVertically { -it * 2 } + fadeOut(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(18.dp), spotColor = c.red)
                    .background(c.red, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Possible emergency. Please call for help now.",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

/** Baymax's red charging case. [led] pulses 0.35–1, [listening] adds a red glow. */
@Composable
fun ChargingCase(led: Float, listening: Boolean, modifier: Modifier = Modifier) {
    val c = BaymaxTheme.colors
    val glow by animateFloatAsState(if (listening) 1f else 0f, tween(300), label = "glow")
    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    Box(modifier.fillMaxSize()) {
        // Floor shadow
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 12.dp)
                .fillMaxWidth(0.84f)
                .height(16.dp)
                .drawBehind {
                    drawOval(Color.Black.copy(alpha = 0.06f))
                    drawOval(
                        Color.Black.copy(alpha = 0.10f),
                        topLeft = Offset(size.width * 0.08f, size.height * 0.2f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.84f, size.height * 0.6f),
                    )
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    if (glow > 0f) {
                        drawCircle(
                            Brush.radialGradient(listOf(c.red.copy(alpha = 0.35f * glow), Color.Transparent)),
                            radius = size.width * 0.8f,
                        )
                    }
                }
                .shadow(18.dp, shape, spotColor = Color(0x80780A0F))
                .background(Brush.verticalGradient(listOf(c.redLight, c.red, c.redDeep)), shape),
        ) {
            // Top highlight
            Box(Modifier.fillMaxWidth().height(3.dp).padding(horizontal = 20.dp).background(Color(0x47FFFFFF), RoundedCornerShape(2.dp)))
            // Lid seam
            Box(
                Modifier
                    .padding(horizontal = 10.dp)
                    .offset(y = 26.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color(0x2E000000), RoundedCornerShape(1.dp)),
            )
            // Status LED
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(10.dp)
                    .graphicsLayer { alpha = if (listening) 1f else led }
                    .shadow(6.dp, CircleShape, spotColor = Color(0xFFFF9A9A), ambientColor = Color(0xFFFF9A9A))
                    .background(Color(0xFFFFD9D9), CircleShape),
            )
        }
    }
}

