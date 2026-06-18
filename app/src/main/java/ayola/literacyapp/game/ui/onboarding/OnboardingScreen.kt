package ayola.literacyapp.game.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ayola.literacyapp.game.R

// --- Tunable vertical layout over the background art (relative weights; bigger = more space) ---
// "Inputs" block sits after TOP_SPACE; the age band sits after MID_SPACE; button after BOTTOM_SPACE.
private const val TOP_SPACE = 44f      // space above the name/school inputs (pointing-tip area)
private const val MID_SPACE = 26f      // gap between inputs and the age band (white space)
private const val BOTTOM_SPACE = 16f   // gap between ages and the Start button

private val FieldShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onLoginSuccess: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    var name by remember { mutableStateOf("") }
    var school by remember { mutableStateOf("") }
    val ageGroups = listOf("5-7", "8-10", "11-13", "14-15", "16-18")
    var selectedAgeGroup by remember { mutableStateOf(ageGroups.first()) }

    LaunchedEffect(state.loginSuccess) {
        if (state.loginSuccess) {
            onLoginSuccess()
            viewModel.onLoginHandled()   // consume so Back doesn't bounce forward again
        }
    }

    // Readable, semi-opaque fields so typed text is legible over the artwork.
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.White.copy(alpha = 0.92f),
        unfocusedContainerColor = Color.White.copy(alpha = 0.92f)
    )

    Scaffold { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            // Full-bleed background art (no scrim — the art's own regions are used).
            Image(
                painter = painterResource(R.drawable.menu_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(TOP_SPACE))

                // Name + school — positioned around the pointing tip.
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    shape = FieldShape,
                    colors = fieldColors,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = school,
                    onValueChange = { school = it },
                    label = { Text("Your school") },
                    singleLine = true,
                    shape = FieldShape,
                    colors = fieldColors,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.weight(MID_SPACE))

                // Age band — arranged neatly in the white space; wraps if it runs out of width.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ageGroups.forEach { group ->
                        AgePill(
                            label = group,
                            selected = group == selectedAgeGroup,
                            onClick = { selectedAgeGroup = group }
                        )
                    }
                }

                state.error?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.weight(BOTTOM_SPACE))

                Button(
                    onClick = { viewModel.loginStudent(name, school, selectedAgeGroup) },
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Start Reading  🚀", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AgePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
