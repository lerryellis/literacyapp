package ayola.literacyapp.game.ui.navigation

import androidx.compose.runtime.Composable
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
import ayola.literacyapp.game.ui.lesson.LessonScreen
import ayola.literacyapp.game.ui.lesson.LessonViewModel
import ayola.literacyapp.game.ui.storylist.StoryListScreen
import ayola.literacyapp.game.ui.storylist.StoryListViewModel
import ayola.literacyapp.game.utils.SessionManager
import ayola.literacyapp.game.utils.SpeechRecognizerManager

object Routes {
    const val ONBOARDING = "onboarding"
    const val STORY_LIST = "story_list"
    const val GAME = "game/{storyId}"
    const val LESSON = "lesson/{storyId}/{accuracy}"
    fun game(storyId: Int) = "game/$storyId"
    fun lesson(storyId: Int, accuracy: Float) = "lesson/$storyId/$accuracy"
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
                // Keep onboarding on the back stack so the child can return to age selection.
                onLoginSuccess = { navController.navigate(Routes.STORY_LIST) }
            )
        }

        composable(Routes.STORY_LIST) {
            val context = LocalContext.current
            val viewModel: StoryListViewModel = viewModel(
                factory = StoryListViewModel.factory(SessionManager(context))
            )
            StoryListScreen(
                viewModel = viewModel,
                onStoryClick = { storyId -> navController.navigate(Routes.game(storyId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.GAME,
            arguments = listOf(navArgument("storyId") { type = NavType.IntType })
        ) { backStackEntry ->
            val storyId = backStackEntry.arguments?.getInt("storyId") ?: -1
            val context = LocalContext.current
            val viewModel: GameViewModel = viewModel(
                factory = GameViewModel.factory(
                    SessionManager(context),
                    SpeechRecognizerManager(context)
                )
            )
            GameScreen(
                viewModel = viewModel,
                storyId = storyId,
                onFinished = { finishedId, accuracy ->
                    navController.navigate(Routes.lesson(finishedId, accuracy)) {
                        // Don't return to the game when leaving the lesson.
                        popUpTo(Routes.GAME) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.LESSON,
            arguments = listOf(
                navArgument("storyId") { type = NavType.IntType },
                navArgument("accuracy") { type = NavType.FloatType }
            )
        ) { backStackEntry ->
            val storyId = backStackEntry.arguments?.getInt("storyId") ?: -1
            val accuracy = backStackEntry.arguments?.getFloat("accuracy") ?: 0f
            val context = LocalContext.current
            val viewModel: LessonViewModel = viewModel(
                factory = LessonViewModel.factory(SessionManager(context))
            )
            LessonScreen(
                viewModel = viewModel,
                storyId = storyId,
                accuracy = accuracy,
                onBackToStories = {
                    navController.navigate(Routes.STORY_LIST) {
                        // Clear the game/lesson from the backstack.
                        popUpTo(Routes.STORY_LIST) { inclusive = true }
                    }
                }
            )
        }
    }
}
