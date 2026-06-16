package ayola.literacyapp.game.ui.game

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ayola.literacyapp.game.models.getSentences

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    storyId: Int,
    onFinished: (storyId: Int) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPermission = granted }

    LaunchedEffect(storyId) { viewModel.loadStory(storyId) }
    LaunchedEffect(state.isFinished) { if (state.isFinished) onFinished(storyId) }
    // Ask for the mic up front so the button is ready when the child is.
    LaunchedEffect(Unit) {
        if (!hasAudioPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (hasAudioPermission) {
                        // TODO(Part 2 wiring): start Vosk listening; for now advance the sentence.
                        viewModel.advanceSentence()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                containerColor = if (hasAudioPermission) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Icon(
                    imageVector = if (hasAudioPermission) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = if (hasAudioPermission) "Read this sentence" else "Microphone permission needed",
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
