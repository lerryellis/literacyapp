package ayola.literacyapp.game.ui.lesson

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ayola.literacyapp.game.ui.theme.skillVisual
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val Gold = Color(0xFFFFC107)

private fun starCount(accuracy: Float): Int = when {
    accuracy >= 80f -> 3
    accuracy >= 50f -> 2
    else -> 1   // finishing the story always earns at least one star
}

@Composable
fun LessonScreen(
    viewModel: LessonViewModel,
    storyId: Int,
    accuracy: Float,
    onBackToStories: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(storyId) { viewModel.loadStory(storyId, accuracy) }

    Box(Modifier.fillMaxSize()) {
        if (!state.isLoading && state.error == null) {
            ConfettiBurst(Modifier.fillMaxSize())
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()

                state.error != null -> {
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onBackToStories) { Text("Back to Stories") }
                }

                else -> {
                    val story = state.story
                    Text(
                        text = "🎉 Great reading!",
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "You finished \"${story?.title.orEmpty()}\"",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))

                    StarRow(starCount(state.accuracy))
                    Spacer(Modifier.height(20.dp))

                    Text(
                        text = "Reading Accuracy: ${state.accuracy.roundToInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.height(24.dp))

                    val sv = skillVisual(story?.lifeSkill.orEmpty())
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.elevatedCardElevation(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            story?.lifeSkill?.takeIf { it.isNotBlank() }?.let { skill ->
                                Text(
                                    text = "${sv.emoji}  Today's skill: $skill",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(
                                text = story?.lifeSkillLesson.orEmpty(),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(Modifier.height(36.dp))
                    Button(
                        onClick = onBackToStories,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back to Stories", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun StarRow(filled: Int) {
    // Stars pop in one-by-one.
    var shown by remember(filled) { mutableStateOf(0) }
    LaunchedEffect(filled) {
        shown = 0
        repeat(filled) {
            delay(240)
            shown++
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            val isFilled = i < filled
            val appeared = i < shown
            val scale by animateFloatAsState(
                targetValue = if (appeared) 1f else 0.4f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "star$i"
            )
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = if (isFilled) Gold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        val s = if (isFilled) scale else 1f
                        scaleX = s
                        scaleY = s
                    }
            )
        }
    }
}
