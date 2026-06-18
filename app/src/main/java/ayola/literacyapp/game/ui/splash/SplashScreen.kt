package ayola.literacyapp.game.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import ayola.literacyapp.game.R
import kotlinx.coroutines.delay

/** Full-bleed splash; auto-advances to onboarding after a short beat. */
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1600)
        onTimeout()
    }
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.splash_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
