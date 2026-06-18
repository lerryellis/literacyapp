package ayola.literacyapp.game.ui.lesson

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.sin
import kotlin.random.Random

private data class Confetto(
    val xFrac: Float,
    val phase: Float,
    val sway: Float,
    val sizePx: Float,
    val color: Color,
    val speed: Float,
)

/** Gentle, looping confetti that falls behind the celebration content. Asset-free (Canvas). */
@Composable
fun ConfettiBurst(modifier: Modifier = Modifier) {
    val colors = listOf(
        Color(0xFFFB7A23), Color(0xFF12A594), Color(0xFF7B4DD8),
        Color(0xFF2E9E5B), Color(0xFFE0457B), Color(0xFFEAA300),
    )
    val pieces = remember {
        val r = Random(42)
        List(48) {
            Confetto(
                xFrac = r.nextFloat(),
                phase = r.nextFloat(),
                sway = 12f + r.nextFloat() * 30f,
                sizePx = 10f + r.nextFloat() * 14f,
                color = colors[r.nextInt(colors.size)],
                speed = 0.6f + r.nextFloat() * 0.8f,
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "confetti")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "t"
    )

    Canvas(modifier) {
        pieces.forEach { p ->
            val prog = ((t * p.speed) + p.phase) % 1f
            val y = prog * size.height
            val x = p.xFrac * size.width + sin((prog + p.phase) * 6.2832f) * p.sway
            drawRect(
                color = p.color.copy(alpha = 0.85f),
                topLeft = Offset(x, y),
                size = Size(p.sizePx, p.sizePx * 0.6f)
            )
        }
    }
}
