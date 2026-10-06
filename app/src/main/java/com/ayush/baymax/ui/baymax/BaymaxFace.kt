package com.ayush.baymax.ui.baymax

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/** Face geometry in a 204 x 136 design box (same as the HTML demo). */
private const val BOX_W = 204f
private const val EYE_Y = 70f
private const val LEFT_X = 62f
private const val RIGHT_X = 142f
private const val EYE_RX = 11f
private const val EYE_RY = 11.5f
private const val LINE_W = 3.4f

/**
 * The face: two dots joined by a line (FR-17).
 *
 * @param eyeScaleY 1 = open, ~0.06 = closed. Blinks and the low-battery squint drive this.
 * @param lookX horizontal glance offset in design units.
 * @param lookY vertical glance offset in design units (positive looks down, e.g. at the input).
 */
@Composable
fun BaymaxFace(
    eyeScaleY: Float,
    lookX: Float,
    lookY: Float,
    modifier: Modifier = Modifier,
    ink: Color = Color(0xFF111317),
) {
    Canvas(modifier) {
        val k = size.width / BOX_W
        translate(lookX * k, lookY * k) {
            scale(scaleX = 1f, scaleY = eyeScaleY.coerceAtLeast(0.04f), pivot = Offset(size.width / 2, EYE_Y * k)) {
                drawLine(
                    color = ink,
                    start = Offset(LEFT_X * k, EYE_Y * k),
                    end = Offset(RIGHT_X * k, EYE_Y * k),
                    strokeWidth = LINE_W * k,
                    cap = StrokeCap.Round,
                )
                for (x in floatArrayOf(LEFT_X, RIGHT_X)) {
                    drawOval(
                        color = ink,
                        topLeft = Offset((x - EYE_RX) * k, (EYE_Y - EYE_RY) * k),
                        size = Size(EYE_RX * 2 * k, EYE_RY * 2 * k),
                    )
                }
            }
        }
    }
}
