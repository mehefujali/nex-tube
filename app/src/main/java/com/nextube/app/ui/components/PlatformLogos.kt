package com.nextube.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Crisp, vector-drawn brand logos for popular platforms supported by yt-dlp. */
@Composable
fun PlatformLogo(id: String, size: Dp = 42.dp) {
    val shape = RoundedCornerShape(12.dp)
    val bgModifier = when (id) {
        "instagram" -> Modifier.background(
            Brush.linearGradient(
                listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976), Color(0xFF962FBF), Color(0xFF4F5BD5))
            )
        )
        "facebook" -> Modifier.background(Color(0xFF1877F2))
        "x" -> Modifier
            .background(Color(0xFF111111))
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
        "tiktok" -> Modifier
            .background(Color(0xFF111111))
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
        "soundcloud" -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFFFF7700), Color(0xFFFF3300))))
        "reddit" -> Modifier.background(Color(0xFFFF4500))
        "vimeo" -> Modifier.background(Color(0xFF1AB7EA))
        "pinterest" -> Modifier.background(Color(0xFFE60023))
        else -> Modifier.background(Color(0xFF4B5563))
    }

    Box(
        modifier = Modifier.size(size).clip(shape).then(bgModifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.62f)) {
            val w = this.size.width
            val h = this.size.height
            when (id) {
                "instagram" -> {
                    val stroke = Stroke(width = w * 0.095f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(w * 0.08f, h * 0.08f),
                        size = Size(w * 0.84f, h * 0.84f),
                        cornerRadius = CornerRadius(w * 0.24f, h * 0.24f),
                        style = stroke,
                    )
                    drawCircle(
                        color = Color.White,
                        radius = w * 0.20f,
                        center = Offset(w * 0.50f, h * 0.50f),
                        style = stroke,
                    )
                    drawCircle(
                        color = Color.White,
                        radius = w * 0.06f,
                        center = Offset(w * 0.73f, h * 0.27f),
                    )
                }

                "facebook" -> {
                    val p = Path().apply {
                        moveTo(w * 0.58f, h * 0.96f)
                        lineTo(w * 0.58f, h * 0.55f)
                        lineTo(w * 0.73f, h * 0.55f)
                        lineTo(w * 0.76f, h * 0.38f)
                        lineTo(w * 0.58f, h * 0.38f)
                        lineTo(w * 0.58f, h * 0.29f)
                        cubicTo(w * 0.58f, h * 0.22f, w * 0.61f, h * 0.19f, w * 0.69f, h * 0.19f)
                        lineTo(w * 0.77f, h * 0.19f)
                        lineTo(w * 0.77f, h * 0.04f)
                        cubicTo(w * 0.72f, h * 0.03f, w * 0.66f, h * 0.03f, w * 0.61f, h * 0.03f)
                        cubicTo(w * 0.46f, h * 0.03f, w * 0.38f, h * 0.12f, w * 0.38f, h * 0.27f)
                        lineTo(w * 0.38f, h * 0.38f)
                        lineTo(w * 0.24f, h * 0.38f)
                        lineTo(w * 0.24f, h * 0.55f)
                        lineTo(w * 0.38f, h * 0.55f)
                        lineTo(w * 0.38f, h * 0.96f)
                        close()
                    }
                    drawPath(p, Color.White)
                }

                "x" -> {
                    val diag1 = Path().apply {
                        moveTo(w * 0.10f, h * 0.10f)
                        lineTo(w * 0.30f, h * 0.10f)
                        lineTo(w * 0.90f, h * 0.90f)
                        lineTo(w * 0.70f, h * 0.90f)
                        close()
                    }
                    drawPath(diag1, Color.White)
                    drawLine(
                        color = Color.White,
                        start = Offset(w * 0.85f, h * 0.12f),
                        end = Offset(w * 0.15f, h * 0.88f),
                        strokeWidth = w * 0.11f,
                        cap = StrokeCap.Square,
                    )
                }

                "tiktok" -> {
                    fun drawNote(dx: Float, dy: Float, color: Color) {
                        val s = Stroke(width = w * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        val p = Path().apply {
                            moveTo(w * 0.56f + dx, h * 0.14f + dy)
                            lineTo(w * 0.56f + dx, h * 0.66f + dy)
                            cubicTo(
                                w * 0.56f + dx, h * 0.82f + dy,
                                w * 0.42f + dx, h * 0.88f + dy,
                                w * 0.30f + dx, h * 0.84f + dy,
                            )
                            cubicTo(
                                w * 0.19f + dx, h * 0.80f + dy,
                                w * 0.18f + dx, h * 0.64f + dy,
                                w * 0.29f + dx, h * 0.58f + dy,
                            )
                            cubicTo(
                                w * 0.36f + dx, h * 0.54f + dy,
                                w * 0.44f + dx, h * 0.56f + dy,
                                w * 0.48f + dx, h * 0.60f + dy,
                            )
                        }
                        drawPath(p, color, style = s)
                        val flag = Path().apply {
                            moveTo(w * 0.56f + dx, h * 0.16f + dy)
                            cubicTo(
                                w * 0.60f + dx, h * 0.30f + dy,
                                w * 0.70f + dx, h * 0.35f + dy,
                                w * 0.82f + dx, h * 0.35f + dy,
                            )
                        }
                        drawPath(flag, color, style = s)
                    }
                    drawNote(-w * 0.04f, -h * 0.03f, Color(0xFF25F4EE))
                    drawNote(w * 0.04f, h * 0.03f, Color(0xFFFE2C55))
                    drawNote(0f, 0f, Color.White)
                }

                "soundcloud" -> {
                    // Left waveform bars
                    val barW = w * 0.07f
                    listOf(
                        0.10f to 0.58f,
                        0.21f to 0.44f,
                        0.32f to 0.48f,
                        0.43f to 0.34f,
                    ).forEach { (x, topY) ->
                        drawLine(
                            color = Color.White,
                            start = Offset(w * x, h * topY),
                            end = Offset(w * x, h * 0.76f),
                            strokeWidth = barW,
                            cap = StrokeCap.Round,
                        )
                    }
                    // Right cloud shape
                    drawCircle(Color.White, radius = w * 0.22f, center = Offset(w * 0.66f, h * 0.52f))
                    drawCircle(Color.White, radius = w * 0.15f, center = Offset(w * 0.82f, h * 0.61f))
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(w * 0.50f, h * 0.54f),
                        size = Size(w * 0.44f, h * 0.23f),
                        cornerRadius = CornerRadius(w * 0.10f, h * 0.10f),
                    )
                }

                "reddit" -> {
                    // Antenna
                    drawLine(
                        Color.White,
                        Offset(w * 0.50f, h * 0.36f),
                        Offset(w * 0.58f, h * 0.16f),
                        strokeWidth = w * 0.07f,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        Color.White,
                        Offset(w * 0.58f, h * 0.16f),
                        Offset(w * 0.74f, h * 0.20f),
                        strokeWidth = w * 0.07f,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(Color.White, w * 0.07f, Offset(w * 0.76f, h * 0.20f))
                    // Ears
                    drawCircle(Color.White, w * 0.11f, Offset(w * 0.16f, h * 0.56f))
                    drawCircle(Color.White, w * 0.11f, Offset(w * 0.84f, h * 0.56f))
                    // Head oval
                    drawOval(Color.White, topLeft = Offset(w * 0.15f, h * 0.34f), size = Size(w * 0.70f, h * 0.52f))
                    // Eyes
                    drawCircle(Color(0xFFFF4500), w * 0.07f, Offset(w * 0.38f, h * 0.56f))
                    drawCircle(Color(0xFFFF4500), w * 0.07f, Offset(w * 0.62f, h * 0.56f))
                    // Smile
                    val smile = Path().apply {
                        moveTo(w * 0.36f, h * 0.71f)
                        quadraticTo(w * 0.50f, h * 0.80f, w * 0.64f, h * 0.71f)
                    }
                    drawPath(smile, Color(0xFFFF4500), style = Stroke(w * 0.055f, cap = StrokeCap.Round))
                }

                "vimeo" -> {
                    val p = Path().apply {
                        moveTo(w * 0.12f, h * 0.34f)
                        cubicTo(w * 0.24f, h * 0.22f, w * 0.35f, h * 0.16f, w * 0.40f, h * 0.26f)
                        lineTo(w * 0.49f, h * 0.60f)
                        cubicTo(w * 0.56f, h * 0.50f, w * 0.65f, h * 0.36f, w * 0.65f, h * 0.28f)
                        cubicTo(w * 0.65f, h * 0.22f, w * 0.60f, h * 0.20f, w * 0.55f, h * 0.22f)
                        cubicTo(w * 0.62f, h * 0.12f, w * 0.88f, h * 0.10f, w * 0.88f, h * 0.28f)
                        cubicTo(w * 0.88f, h * 0.46f, w * 0.62f, h * 0.84f, w * 0.46f, h * 0.84f)
                        cubicTo(w * 0.34f, h * 0.84f, w * 0.30f, h * 0.68f, w * 0.25f, h * 0.46f)
                        cubicTo(w * 0.22f, h * 0.38f, w * 0.18f, h * 0.38f, w * 0.14f, h * 0.41f)
                        close()
                    }
                    drawPath(p, Color.White)
                }

                "pinterest" -> {
                    val s = Stroke(width = w * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val p = Path().apply {
                        moveTo(w * 0.44f, h * 0.88f)
                        lineTo(w * 0.52f, h * 0.42f)
                    }
                    drawPath(p, Color.White, style = s)
                    val loop = Path().apply {
                        moveTo(w * 0.32f, h * 0.58f)
                        cubicTo(w * 0.22f, h * 0.44f, w * 0.28f, h * 0.16f, w * 0.54f, h * 0.16f)
                        cubicTo(w * 0.76f, h * 0.16f, w * 0.82f, h * 0.38f, w * 0.74f, h * 0.54f)
                        cubicTo(w * 0.67f, h * 0.68f, w * 0.52f, h * 0.66f, w * 0.48f, h * 0.58f)
                    }
                    drawPath(loop, Color.White, style = s)
                }
            }
        }
    }
}
