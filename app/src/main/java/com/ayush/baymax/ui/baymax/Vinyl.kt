package com.ayush.baymax.ui.baymax

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.max

/**
 * A rounded rectangle whose corners are ellipses sized as fractions of the shape,
 * like CSS `border-radius: a% / b%`. Lets us match the demo's balloon silhouettes.
 */
class EllipticalCornerShape(
    private val topRx: Float,
    private val topRy: Float,
    private val bottomRx: Float,
    private val bottomRy: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val top = CornerRadius(w * topRx, h * topRy)
        val bottom = CornerRadius(w * bottomRx, h * bottomRy)
        val path = Path().apply {
            addRoundRect(RoundRect(0f, 0f, w, h, top, top, bottom, bottom))
        }
        return Outline.Generic(path)
    }
}

/** Torso: wide shoulders, belly widening toward the bottom of the frame. */
val BodyShape = EllipticalCornerShape(0.48f, 0.30f, 0.40f, 0.70f)

/** Head: a soft, slightly flattened egg, wider than tall. */
val HeadShape = EllipticalCornerShape(0.50f, 0.54f, 0.47f, 0.46f)

/** Arms: plain capsules. */
val ArmShape = EllipticalCornerShape(0.50f, 0.18f, 0.50f, 0.18f)

/**
 * Paints white vinyl inside [shape]: a bright key light at the upper middle that falls off
 * to grey at the edges, plus a soft core shadow at the lower right and a highlight at the upper left.
 */
fun Modifier.vinyl(
    shape: Shape,
    light: Color = Color(0xFFFFFFFF),
    mid: Color = Color(0xFFF1F2F5),
    shade: Color = Color(0xFFDFE2E7),
    edge: Color = Color(0xFFCDD2D9),
    shadowStrength: Float = 0.20f,
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val radius = max(size.width, size.height) * 0.62f
    val base = Brush.radialGradient(
        0f to light, 0.38f to light, 0.62f to mid, 0.85f to shade, 1f to edge,
        center = Offset(size.width * 0.5f, size.height * 0.28f),
        radius = radius,
    )
    val core = Brush.radialGradient(
        0f to Color(0xFF7884A0).copy(alpha = shadowStrength), 1f to Color.Transparent,
        center = Offset(size.width * 0.92f, size.height * 0.95f),
        radius = max(size.width, size.height) * 0.6f,
    )
    val highlight = Brush.radialGradient(
        0f to Color.White.copy(alpha = 0.9f), 1f to Color.Transparent,
        center = Offset(size.width * 0.28f, size.height * 0.18f),
        radius = max(size.width, size.height) * 0.35f,
    )
    onDrawBehind {
        drawOutline(outline, base)
        drawOutline(outline, core)
        drawOutline(outline, highlight)
    }
}
