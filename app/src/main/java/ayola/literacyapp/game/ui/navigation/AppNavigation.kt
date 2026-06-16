package ayola.literacyapp.game.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ayola.literacyapp.game.ui.onboarding.OnboardingScreen
import ayola.literacyapp.game.ui.onboarding.OnboardingViewModel
import ayola.literacyapp.game.utils.SessionManager

object Routes {
    const val ONBOARDING = "onboarding"
    const val STORY_LIST = "story_list"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.ONBOARDING
    ) {
        composable(Routes.ONBOARDING) {
            val context = LocalContext.current
            val viewModel: OnboardingViewModel = viewModel(
                factory = OnboardingViewModel.factory(SessionManager(context))
            )
            OnboardingScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate(Routes.STORY_LIST) {
                        // Don't let the child navigate back into onboarding.
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.STORY_LIST) {
            StoryListPlaceholder()
        }
    }
}

/** Temporary destination until the real story list is built. */
@Composable
private fun StoryListPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Story List — coming soon")
    }
}
