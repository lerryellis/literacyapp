package ayola.literacyapp.game.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ayola.literacyapp.game.ui.onboarding.OnboardingScreen
import ayola.literacyapp.game.ui.onboarding.OnboardingViewModel
import ayola.literacyapp.game.ui.game.GameScreen
import ayola.literacyapp.game.ui.game.GameViewModel
import ayola.literacyapp.game.ui.storylist.StoryListScreen
import ayola.literacyapp.game.ui.storylist.StoryListViewModel
import ayola.literacyapp.game.utils.SessionManager

object Routes {
    const val ONBOARDING = "onboarding"
    const val STORY_LIST = "story_list"
    const val GAME = "game/{storyId}"
    const val LESSON = "lesson/{storyId}"
    fun game(storyId: Int) = "game/$storyId"
    fun lesson(storyId: Int) = "lesson/$storyId"
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
            val context = LocalContext.current
            val viewModel: StoryListViewModel = viewModel(
                factory = StoryListViewModel.factory(SessionManager(context))
            )
            StoryListScreen(
                viewModel = viewModel,
                onStoryClick = { storyId -> navController.navigate(Routes.game(storyId)) }
            )
        }

        composable(
            route = Routes.GAME,
            arguments = listOf(navArgument("storyId") { type = NavType.IntType })
        ) { backStackEntry ->
            val storyId = backStackEntry.arguments?.getInt("storyId") ?: -1
            val context = LocalContext.current
            val viewModel: GameViewModel = viewModel(
                factory = GameViewModel.factory(SessionManager(context))
            )
            GameScreen(
                viewModel = viewModel,
                storyId = storyId,
                onFinished = { finishedId ->
                    navController.navigate(Routes.lesson(finishedId)) {
                        // Don't return to the game when leaving the lesson.
                        popUpTo(Routes.GAME) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.LESSON,
            arguments = listOf(navArgument("storyId") { type = NavType.IntType })
        ) { backStackEntry ->
            val storyId = backStackEntry.arguments?.getInt("storyId") ?: -1
            LessonPlaceholder(storyId)
        }
    }
}

/** Temporary destination until the life-skill lesson screen is built. */
@Composable
private fun LessonPlaceholder(storyId: Int) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Lesson for story #$storyId — coming soon")
    }
}
