package com.kroombox.termx.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kroombox.termx.ui.theme.Palette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Logo yang mengorbit radar: id drawable + warna merek. */
data class OrbitLogo(val res: Int, val tint: Color)

/**
 * Radar pemindai server: sapuan berputar di atas cincin konsentris, logo di orbit menyala saat
 * tersapu. Untuk menunggu yang tidak tentu lamanya (mencari agent, memuat daftar model, dst.).
 */
@Composable
fun RadarLoader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    logos: List<OrbitLogo> = emptyList(),
    center: ImageVector = Icons.Rounded.Dns,
    color: Color = Palette.Accent,
    size: Dp = 184.dp,
) {
    val t = rememberInfiniteTransition(label = "radar")
    val sweep by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "sweep")
    val breathe by t.animateFloat(0.94f, 1.04f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val ripple by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "ripple")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = this.size.minDimension / 2
                val c = Offset(this.size.width / 2, this.size.height / 2)
                // cincin konsentris
                listOf(0.42f, 0.70f, 0.98f).forEachIndexed { i, f ->
                    drawCircle(color.copy(alpha = 0.10f + 0.04f * i), r * f, c, style = Stroke(1.dp.toPx()))
                }
                // gelombang yang memancar dari tengah
                drawCircle(color.copy(alpha = 0.22f * (1 - ripple)), r * (0.30f + 0.68f * ripple), c, style = Stroke(1.5.dp.toPx()))
                // sapuan radar: ekor memudar di belakang garis terang
                rotate(sweep, c) {
                    drawCircle(
                        Brush.sweepGradient(
                            0f to Color.Transparent, 0.70f to Color.Transparent,
                            0.97f to color.copy(alpha = 0.30f), 1f to color.copy(alpha = 0.0f), center = c,
                        ),
                        r * 0.98f, c,
                    )
                    drawLine(color.copy(alpha = 0.85f), c, Offset(c.x + r * 0.98f, c.y), 1.6.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            // logo di orbit, menyala saat sapuan lewat
            val n = logos.size
            logos.forEachIndexed { i, logo ->
                val ang = (i * 360f / n.coerceAtLeast(1)) + 18f
                val ringF = if (i % 2 == 0) 0.98f else 0.70f
                val rad = Math.toRadians(ang.toDouble())
                val half = size.value / 2
                val dx = (cos(rad) * half * ringF).toFloat()
                val dy = (sin(rad) * half * ringF).toFloat()
                Box(
                    Modifier
                        .offset { IntOffset((dx * density).roundToInt(), (dy * density).roundToInt()) }
                        .size(30.dp)
                        .graphicsLayer {
                            // jarak sudut sapuan ke logo (0..180); makin dekat makin terang
                            val d = abs(((sweep - ang) % 360 + 360) % 360)
                            val glow = if (d < 70f) 1f - d / 70f else 0f
                            alpha = 0.35f + 0.65f * glow
                            scaleX = 0.92f + 0.14f * glow
                            scaleY = scaleX
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .background(Palette.SurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(logo.res), null, tint = logo.tint, modifier = Modifier.size(17.dp))
                }
            }
            // server di tengah
            Box(
                Modifier.size(56.dp)
                    .graphicsLayer { scaleX = breathe; scaleY = breathe }
                    .drawBehind { drawCircle(color.copy(alpha = 0.14f), this.size.minDimension * 0.62f) }
                    .clip(RoundedCornerShape(18.dp))
                    .background(Palette.SurfaceHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(center, null, tint = color, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Palette.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(Modifier.height(18.dp))
        FlowBar(Modifier.width(132.dp), color)
    }
}

/** Bar tak tentu: kilau yang mengalir di atas jalur tipis (pengganti LinearProgressIndicator). */
@Composable
fun FlowBar(modifier: Modifier = Modifier.fillMaxWidth(), color: Color = Palette.Accent, height: Dp = 3.dp) {
    val t = rememberInfiniteTransition(label = "flow")
    val x by t.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing)), label = "x")
    Box(
        modifier.height(height).clip(CircleShape).background(color.copy(alpha = 0.14f)).drawBehind {
            val w = this.size.width
            val seg = w * 0.38f
            val start = x * w - seg / 2
            drawRect(
                Brush.horizontalGradient(
                    0f to Color.Transparent, 0.5f to color, 1f to Color.Transparent,
                    startX = start, endX = start + seg,
                ),
            )
        },
    )
}

/** Tiga titik yang bergantian naik-turun: indikator kecil yang tenang untuk baris & tombol. */
@Composable
fun WaveDots(color: Color = Palette.Accent, dot: Dp = 6.dp) {
    val t = rememberInfiniteTransition(label = "dots")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "p")
    Row(horizontalArrangement = Arrangement.spacedBy(dot * 0.6f), verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(dot * 2.4f)) {
        repeat(3) { i ->
            Box(
                Modifier.size(dot).graphicsLayer {
                    val ph = ((p - i * 0.16f) % 1f + 1f) % 1f
                    val k = if (ph < 0.5f) sin(ph * 2 * Math.PI).toFloat() else 0f
                    translationY = -k * dot.toPx() * 0.7f
                    alpha = 0.4f + 0.6f * k
                }.clip(CircleShape).background(color),
            )
        }
    }
}
