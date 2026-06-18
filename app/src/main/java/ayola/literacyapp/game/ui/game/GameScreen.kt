package ayola.literacyapp.game.ui.game

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ayola.literacyapp.game.models.getSentences
import ayola.literacyapp.game.ui.theme.Bad
import ayola.literacyapp.game.ui.theme.BadDark
import ayola.literacyapp.game.ui.theme.BadLight
import ayola.literacyapp.game.ui.theme.Good
import ayola.literacyapp.game.ui.theme.GoodDark
import ayola.literacyapp.game.ui.theme.GoodLight
import ayola.literacyapp.game.ui.theme.Warn
import ayola.literacyapp.game.ui.theme.WarnDark
import ayola.literacyapp.game.ui.theme.WarnLight

private const val PASS_THRESHOLD = 0.70f

private fun confidenceColor(c: Float): Color = when {
    c >= 0.70f -> Good
    c >= 0.50f -> Warn
    else -> Bad
}

@OptIn(ExperimentalMaterial3Api::class)
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

    // Success chime the first time confidence crosses the pass threshold on each sentence.
    val ding = rememberDing()
    var dinged by remember(state.currentSentenceIndex) { mutableStateOf(false) }
    LaunchedEffect(state.matchConfidence, state.currentSentenceIndex) {
        if (state.matchConfidence >= PASS_THRESHOLD && !dinged) {
            dinged = true
            ding()
        }
    }

    // Gentle pulse on the mic while listening.
    val micInfinite = rememberInfiniteTransition(label = "mic")
    val micPulse by micInfinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "micPulse"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📖 Story Flow", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        val story = state.story
        val sentences = story?.getSentences().orEmpty()
        val totalSentences = sentences.size.coerceAtLeast(1)

        when {
            state.error != null -> CenterMessage(state.error!!, MaterialTheme.colorScheme.error, innerPadding)
            story == null -> CenterLoading("Loading story…", innerPadding)
            !state.isSpeechEngineReady -> CenterLoading("Loading Speech Engine…", innerPadding)
            else -> {
                val currentNumber = (state.currentSentenceIndex + 1).coerceAtMost(totalSentences)
                val progress = (currentNumber.toFloat() / totalSentences).coerceIn(0f, 1f)

                Column(
                    Modifier
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    // Progress
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        "Sentence $currentNumber of $totalSentences",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Spacer(Modifier.height(20.dp))

                    // "Read this aloud" — reactive buddy beside the sentence
                    val heardWords = heardWordSet(state.heardText)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ReadingBuddy(
                                confidence = state.matchConfidence,
                                isListening = state.isListening,
                                isStruggling = state.isStruggling,
                                characterName = state.characterName
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                state.characterName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(
                            Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
                                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                                .padding(20.dp)
                        ) {
                            Text(
                                "Read this aloud:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.height(10.dp))
                            // Swoosh between sentences; words light up green as they're recognized.
                            AnimatedContent(
                                targetState = state.currentSentenceIndex,
                                transitionSpec = {
                                    (slideInHorizontally { it / 2 } + fadeIn()) togetherWith
                                            (slideOutHorizontally { -it / 2 } + fadeOut())
                                },
                                label = "sentence"
                            ) { idx ->
                                Text(
                                    text = highlightSentence(sentences.getOrNull(idx).orEmpty(), heardWords),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    // Hearing + confidence card
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (state.isListening) Bad else MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (state.isListening) "🎤 Listening…" else "Ready to listen",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            state.heardText.ifBlank { "Tap the mic and read the sentence" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(14.dp))
                        ConfidenceMeter(state.matchConfidence)
                    }
                    Spacer(Modifier.height(16.dp))

                    // Feedback banner (tinted by confidence) — only while listening or after a try
                    if (state.isListening || state.heardText.isNotBlank()) {
                        val fc = confidenceColor(state.matchConfidence)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(fc.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                .border(2.dp, fc, RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                feedbackFor(state.matchConfidence, state.isListening),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = fc
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                    }

                    // Mic button (turns red while listening)
                    Button(
                        onClick = {
                            if (!hasAudioPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            else viewModel.onMicPressed()
                        },
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isListening) Bad else MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val s = if (state.isListening) micPulse else 1f
                                scaleX = s
                                scaleY = s
                            }
                    ) {
                        Text(
                            when {
                                !hasAudioPermission -> "🎤 Allow microphone"
                                state.isListening -> "⏹ Stop Listening"
                                else -> "🎤 Start Reading"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    // Manual Next (early readers set their own pace)
                    OutlinedButton(
                        onClick = { viewModel.advanceSentence() },
                        shape = RoundedCornerShape(28.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (state.matchConfidence >= PASS_THRESHOLD) "Next  ✅" else "Next  →",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(20.dp))

                    // Stats card
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Stat("${state.currentSentenceIndex}", "Done")
                        Stat("${(state.matchConfidence * 100).toInt()}%", "Match")
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ConfidenceMeter(confidence: Float) {
    val c = confidence.coerceIn(0f, 1f)
    val state = confidenceColor(c)
    val ramp = when {
        c >= 0.70f -> listOf(GoodLight, GoodDark)
        c >= 0.50f -> listOf(WarnLight, WarnDark)
        else -> listOf(BadLight, BadDark)
    }
    val animated by animateFloatAsState(c, tween(350), label = "conf")

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Match Confidence",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${(c * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = state
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(Brush.horizontalGradient(ramp))
            )
            // 70% pass-threshold marker
            Box(Modifier.fillMaxWidth(PASS_THRESHOLD).fillMaxHeight()) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(GoodDark)
                )
            }
        }
    }
}

private fun heardWordSet(heard: String): Set<String> =
    heard.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.toSet()

/** Highlights (green + bold) each expected word that has been recognized so far. */
private fun highlightSentence(sentence: String, heard: Set<String>): AnnotatedString =
    buildAnnotatedString {
        val tokens = sentence.split(" ")
        tokens.forEachIndexed { i, tok ->
            val norm = tok.lowercase().filter { it.isLetterOrDigit() }
            if (norm.isNotEmpty() && norm in heard) {
                withStyle(SpanStyle(color = Good, fontWeight = FontWeight.Bold)) { append(tok) }
            } else {
                append(tok)
            }
            if (i != tokens.lastIndex) append(" ")
        }
    }

private fun feedbackFor(confidence: Float, isListening: Boolean): String = when {
    confidence >= 0.70f -> "✅ Great reading! Tap Next"
    confidence >= 0.50f -> "⚠️ Getting closer — keep going"
    isListening -> "🎤 Listening… read the sentence"
    else -> "❌ Let's try that one again"
}

@Composable
private fun CenterLoading(message: String, innerPadding: PaddingValues) {
    Box(
        Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun CenterMessage(message: String, color: Color, innerPadding: PaddingValues) {
    Box(
        Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = color, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}
