package ayola.literacyapp.game.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// A single, consistent playful light scheme — no dynamic color / dark mode, so the
// kid-friendly palette looks the same on every device.
private val LiteracyColorScheme = lightColorScheme(
    primary = SunnyOrange,
    onPrimary = OnSunnyOrange,
    primaryContainer = PeachContainer,
    onPrimaryContainer = OnPeachContainer,

    secondary = Teal,
    onSecondary = OnTeal,
    secondaryContainer = MintContainer,
    onSecondaryContainer = OnMintContainer,

    tertiary = Grape,
    onTertiary = OnGrape,
    tertiaryContainer = GrapeContainer,
    onTertiaryContainer = OnGrapeContainer,

    background = Cream,
    onBackground = WarmInk,
    surface = CreamSurface,
    onSurface = WarmInk,
    surfaceVariant = Sand,
    onSurfaceVariant = OnSand,
    outline = SoftOutline,

    error = ErrorRed,
    onError = OnError,
)

@Composable
fun LiteracyAppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LiteracyColorScheme,
        typography = Typography,
        content = content
    )
}
