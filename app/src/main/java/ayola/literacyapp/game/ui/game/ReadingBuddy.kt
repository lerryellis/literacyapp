package ayola.literacyapp.game.ui.game

import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ayola.literacyapp.game.ui.theme.Good
import ayola.literacyapp.game.ui.theme.Warn
import java.util.Locale

/**
 * A reactive reading buddy that responds to the child's live progress.
 * Renders the age-mapped character art (`<character>_<happy|struggling|excited>.png` in res/drawable)
 * when available; falls back to an emoji face for characters whose art hasn't been added yet.
 * Bobs gently while reading and pops bigger when doing well.
 */
@Composable
fun ReadingBuddy(
    confidence: Float,
    isListening: Boolean,
    isStruggling: Boolean = false,
    characterName: String = "Kofi",
    modifier: Modifier = Modifier,
) {
    val expression = when {
        confidence >= 0.70f -> "excited"
        isStruggling -> "struggling"
        else -> "happy"
    }

    // Resolve "<character>_<expression>" -> drawable id at runtime (0 if that art isn't present).
    val context = LocalContext.current
    val resId = remember(characterName, expression) {
        context.resources.getIdentifier(
            "${characterName.lowercase()}_$expression", "drawable", context.packageName
        )
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

    val animatedModifier = modifier
        .size(76.dp)
        .graphicsLayer {
            translationY = bob
            scaleX = pop
            scaleY = pop
        }

    if (resId != 0) {
        Image(
            painter = painterResource(resId),
            contentDescription = "$characterName, $expression",
            contentScale = ContentScale.Fit,
            modifier = animatedModifier
        )
    } else {
        // Emoji fallback (e.g. Esi / Musa until their art is added).
        val emoji = when (expression) {
            "excited" -> "😄"
            "struggling" -> "🤔"
            else -> if (isListening) "👂" else "🙂"
        }
        val tint = when {
            confidence >= 0.70f -> Good.copy(alpha = 0.18f)
            isStruggling -> Warn.copy(alpha = 0.18f)
            isListening -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        Box(
            modifier = animatedModifier.clip(CircleShape).background(tint),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 38.sp)
        }
    }
}

/**
 * Returns a function that plays a short success chime via the system ToneGenerator (no audio asset).
 * Released automatically when the composable leaves.
 */
@Composable
fun rememberDing(): () -> Unit {
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }
    DisposableEffect(Unit) {
        onDispose { tone.release() }
    }
    return remember(tone) { { tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 160) } }
}

/**
 * Returns a function that speaks a word aloud using the device's offline Text-to-Speech engine
 * (US English, slightly slowed for young readers). Used so a child can tap a word to hear how it's
 * pronounced. The engine is initialized once and shut down when the composable leaves.
 */
@Composable
fun rememberWordSpeaker(): (String) -> Unit {
    val context = LocalContext.current
    val engineHolder = remember { arrayOfNulls<TextToSpeech>(1) }

    DisposableEffect(Unit) {
        val tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engineHolder[0]?.apply {
                    language = Locale.US
                    setSpeechRate(0.85f) // a touch slower for clarity
                }
            }
        }
        engineHolder[0] = tts
        onDispose {
            tts.stop()
            tts.shutdown()
            engineHolder[0] = null
        }
    }

    return remember {
        { word: String ->
            val clean = word.trim().trim('.', ',', '!', '?', ';', ':', '"', '\'')
            if (clean.isNotEmpty()) {
                engineHolder[0]?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "lit-word")
            }
        }
    }
}
