package ayola.literacyapp.game.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ayola.literacyapp.game.models.getSentences

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    storyId: Int,
    onFinished: (storyId: Int) -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(storyId) { viewModel.loadStory(storyId) }
    LaunchedEffect(state.isFinished) { if (state.isFinished) onFinished(storyId) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.advanceSentence() }) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Read this sentence",
                    modifier = Modifier.size(36.dp)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            val story = state.story
            when {
                state.error != null -> Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error
                )

                story == null -> CircularProgressIndicator()

                else -> {
                    val sentences = story.getSentences()
                    val sentence = sentences.getOrNull(state.currentSentenceIndex).orEmpty()
                    Text(
                        text = sentence,
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
