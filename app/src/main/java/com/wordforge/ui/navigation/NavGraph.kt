package com.wordforge.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.wordforge.ui.screens.AddWordScreen
import com.wordforge.ui.screens.EditWordScreen
import com.wordforge.ui.screens.ExerciseSessionScreen
import com.wordforge.ui.screens.ExerciseSetupScreen
import com.wordforge.ui.screens.HowItWorksScreen
import com.wordforge.ui.screens.LlmSettingsScreen
import com.wordforge.ui.screens.OverdueReviewScreen
import com.wordforge.ui.screens.QuizScreen
import com.wordforge.ui.screens.WordDetailScreen
import com.wordforge.ui.screens.WordListScreen
import com.wordforge.ui.theme.ThemeMode
import com.wordforge.viewmodel.WordViewModel
import com.wordforge.viewmodel.ExerciseGenerationState
import com.wordforge.viewmodel.ExerciseSessionViewModel
import com.wordforge.data.LearningItemType
import com.wordforge.data.ReminderFrequency

@Composable
fun NavGraph(
    navController: NavHostController,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    reminderFrequency: ReminderFrequency,
    onReminderFrequencyChange: (ReminderFrequency) -> Unit,
    notificationsGranted: Boolean,
    shouldOfferNotifications: Boolean,
    onNotificationEducationShown: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    viewModel: WordViewModel = composeViewModel(),
    exerciseViewModel: ExerciseSessionViewModel = composeViewModel(),
) {
    val exerciseSettings by exerciseViewModel.settings.collectAsStateWithLifecycle()
    val generationState by exerciseViewModel.generationState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = Screen.WordList.route
    ) {
        composable(Screen.WordList.route) {
            WordListScreen(
                viewModel = viewModel,
                onNavigateToAddWord = {
                    navController.navigate(Screen.AddWord.route)
                },
                onNavigateToDetail = { wordId ->
                    navController.navigate(Screen.WordDetail.createRoute(wordId))
                },
                onNavigateToHowItWorks = {
                    navController.navigate(Screen.HowItWorks.route)
                },
                onNavigateToOverdueReview = {
                    navController.navigate(Screen.OverdueReview.route)
                },
                onNavigateToExerciseSetup = {
                    exerciseViewModel.clearSession()
                    navController.navigate(Screen.ExerciseSetup.route)
                },
                onNavigateToLlmSettings = {
                    navController.navigate(Screen.LlmSettings.route)
                },
                aiProviderLabel = exerciseSettings.provider.displayName
                    .takeIf { exerciseSettings.isConnected },
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                reminderFrequency = reminderFrequency,
                onReminderFrequencyChange = onReminderFrequencyChange,
                notificationsGranted = notificationsGranted,
                onRequestNotificationPermission = onRequestNotificationPermission,
            )
        }

        composable(Screen.OverdueReview.route) {
            OverdueReviewScreen(
                viewModel = viewModel,
                onFinished = { navController.popBackStack() }
            )
        }

        composable(Screen.HowItWorks.route) {
            HowItWorksScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.LlmSettings.route) {
            LlmSettingsScreen(
                persistedProvider = exerciseSettings.provider,
                persistedModel = exerciseSettings.model,
                isConnected = exerciseSettings.isConnected,
                errorMessage = exerciseSettings.errorMessage,
                onSave = exerciseViewModel::saveConnection,
                onDisconnect = exerciseViewModel::disconnect,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Screen.ExerciseSetup.route) {
            val words by viewModel.allWords.collectAsStateWithLifecycle()
            val generation = generationState

            LaunchedEffect(generation) {
                if (generation is ExerciseGenerationState.Ready) {
                    navController.navigate(Screen.ExerciseSession.route) {
                        launchSingleTop = true
                    }
                }
            }

            ExerciseSetupScreen(
                initialConfig = exerciseSettings.exerciseConfig,
                provider = exerciseSettings.provider,
                isConnected = exerciseSettings.isConnected,
                totalWordCount = words.size,
                simpleWordCount = words.count {
                    it.itemType == LearningItemType.SIMPLE_WORD
                },
                isGenerating = generation is ExerciseGenerationState.Loading,
                errorMessage = (generation as? ExerciseGenerationState.Error)?.message,
                onStart = exerciseViewModel::generate,
                onCancel = exerciseViewModel::cancelGeneration,
                onOpenSettings = { navController.navigate(Screen.LlmSettings.route) },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Screen.ExerciseSession.route) {
            val ready = generationState as? ExerciseGenerationState.Ready
            if (ready == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                ExerciseSessionScreen(
                    title = ready.pack.title,
                    sessionJson = ready.sessionJson,
                    answerStateJson = ready.answerStateJson,
                    onAnswerStateChange = exerciseViewModel::updateAnswerState,
                    onLeave = {
                        exerciseViewModel.clearSession()
                        navController.popBackStack()
                    },
                )
            }
        }

        composable(Screen.AddWord.route) {
            val words by viewModel.allWords.collectAsStateWithLifecycle()
            AddWordScreen(
                onAddItem = viewModel::addItem,
                onNavigateBack = {
                    navController.popBackStack()
                },
                existingItems = words,
                shouldOfferNotifications = shouldOfferNotifications,
                onNotificationEducationShown = onNotificationEducationShown,
                onRequestNotificationPermission = onRequestNotificationPermission,
            )
        }

        composable(
            route = Screen.Quiz.route,
            arguments = listOf(navArgument("wordId") { type = NavType.StringType })
        ) { backStackEntry ->
            val wordId = backStackEntry.arguments?.getString("wordId") ?: return@composable
            QuizScreen(
                wordId = wordId,
                viewModel = viewModel,
                onFinished = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.WordDetail.route,
            arguments = listOf(navArgument("wordId") { type = NavType.StringType })
        ) { backStackEntry ->
            val wordId = backStackEntry.arguments?.getString("wordId") ?: return@composable
            WordDetailScreen(
                wordId = wordId,
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToQuiz = { id ->
                    navController.navigate(Screen.Quiz.createRoute(id))
                },
                onNavigateToEdit = { id ->
                    navController.navigate(Screen.EditWord.createRoute(id))
                }
            )
        }

        composable(
            route = Screen.EditWord.route,
            arguments = listOf(navArgument("wordId") { type = NavType.StringType })
        ) { backStackEntry ->
            val wordId = backStackEntry.arguments?.getString("wordId") ?: return@composable
            EditWordScreen(
                wordId = wordId,
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
