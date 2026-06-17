package ayola.literacyapp.game.ui.game

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ayola.literacyapp.game.ui.theme.Good

/**
 * A friendly emoji "reading buddy" that reacts live to the child's progress:
 *   😄 doing great (≥70%) · 😊 getting close (≥50%) · 👂 listening · 🙂 idle.
 * It bobs gently while listening and pops bigger when the child is reading well.
 * (Placeholder for a real animated character — swap the emoji for a Lottie/PNG set later.)
 */
@Composable
fun ReadingBuddy(
    confidence: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier,
) {
    val emoji = when {
        confidence >= 0.70f -> "😄"
        confidence >= 0.50f -> "😊"
        isListening -> "👂"
        else -> "🙂"
    }

    val infinite = rememberInfiniteTransition(label = "buddy")
    val bob by infinite.animateFloat(
        initialValue = 0f,
        targetValue = if (isListening) -12f else -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )
    val pop by animateFloatAsState(
        targetValue = if (confidence >= 0.70f) 1.2f else 1f,
        animationSpec = spring(),
        label = "pop"
    )

    val tint = when {
        confidence >= 0.70f -> Good.copy(alpha = 0.18f)
        isListening -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier = modifier
            .size(72.dp)
            .graphicsLayer {
                translationY = bob
                scaleX = pop
                scaleY = pop
            }
            .clip(CircleShape)
            .background(tint),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = 38.sp)
    }
}

/**
 * Returns a function that plays a short success chime, using the system ToneGenerator
 * (no sound asset required). Released automatically when the composable leaves.
 */
@Composable
fun rememberDing(): () -> Unit {
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }
    DisposableEffect(Unit) {
        onDispose { tone.release() }
    }
    return remember(tone) { { tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 160) } }
}
