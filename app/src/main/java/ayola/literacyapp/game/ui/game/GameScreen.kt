package ayola.literacyapp.game.ui.game

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
    onFinished: (storyId: Int, accuracy: Float) -> Unit
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
    LaunchedEffect(state.isFinished) { if (state.isFinished) onFinished(storyId, state.finalAccuracy) }
    LaunchedEffect(Unit) {
        if (!hasAudioPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val micEnabled = hasAudioPermission && state.isSpeechEngineReady

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when {
                        !hasAudioPermission -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        state.isSpeechEngineReady -> viewModel.onMicPressed()
                    }
                },
                containerColor = when {
                    !micEnabled -> MaterialTheme.colorScheme.surfaceVariant
                    state.isListening -> MaterialTheme.colorScheme.error      // recording
                    else -> MaterialTheme.colorScheme.primary
                }
            ) {
                val icon = when {
                    !hasAudioPermission -> Icons.Filled.MicOff
                    state.isListening -> Icons.Filled.Stop
                    else -> Icons.Filled.Mic
                }
                Icon(icon, contentDescription = "Read this sentence", modifier = Modifier.size(36.dp))
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        val story = state.story
        val sentences = story?.getSentences().orEmpty()
        val totalSentences = sentences.size.coerceAtLeast(1)
        val showProgress = story != null && state.isSpeechEngineReady && state.error == null

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showProgress) {
                val currentNumber = (state.currentSentenceIndex + 1).coerceAtMost(totalSentences)
                val progress = (currentNumber.toFloat() / totalSentences).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
                Text(
                    text = "Sentence $currentNumber of $totalSentences",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    state.error != null -> Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.error
                    )

                    story == null -> CircularProgressIndicator()

                    !state.isSpeechEngineReady -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Loading Speech Engine…", style = MaterialTheme.typography.bodyLarge)
                    }

                    else -> {
                        val sentence = sentences.getOrNull(state.currentSentenceIndex).orEmpty()
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = sentence,
                                style = MaterialTheme.typography.headlineLarge,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(24.dp))
                            // Live transcription so the child sees what the app is hearing.
                            Text(
                                text = state.partialText,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(32.dp))
                            // Child taps Next when ready — no rushing early readers.
                            Button(onClick = { viewModel.advanceSentence() }) {
                                Text("Next")
                                Spacer(Modifier.size(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}
