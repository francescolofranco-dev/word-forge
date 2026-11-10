package com.wordforge

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.wordforge.data.ReminderFrequency
import com.wordforge.data.ThemePreferenceStore
import com.wordforge.data.NotificationPreferenceStore
import com.wordforge.notification.NotificationScheduler
import com.wordforge.notification.ReviewNotification
import com.wordforge.ui.screens.OnboardingDialog
import com.wordforge.ui.navigation.NavGraph
import com.wordforge.ui.navigation.Screen
import com.wordforge.ui.theme.WordForgeTheme

class MainActivity : ComponentActivity() {

    private var notificationsGranted by mutableStateOf(false)
    private var pendingWordId by mutableStateOf<String?>(null)
    private var pendingOverdueReview by mutableStateOf(false)

    // Launcher for requesting notification permission on Android 13+
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            notificationsGranted = isGranted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationsGranted = hasNotificationPermission()
        handleIntent(intent)

        setContent {
            val themePreferenceStore = remember { ThemePreferenceStore(applicationContext) }
            val notificationPreferenceStore = remember {
                NotificationPreferenceStore(applicationContext)
            }
            var onboardingComplete by remember {
                mutableStateOf(notificationPreferenceStore.hasCompletedOnboarding)
            }
            var themeMode by remember { mutableStateOf(themePreferenceStore.themeMode) }
            var hasShownNotificationEducation by remember {
                mutableStateOf(notificationPreferenceStore.hasShownEducation)
            }
            var reminderFrequency by remember {
                mutableStateOf(notificationPreferenceStore.reminderFrequency)
            }
            val darkTheme = themeMode.isDark(systemInDarkTheme = isSystemInDarkTheme())

            SideEffect {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }

            WordForgeTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val changeFrequency: (ReminderFrequency) -> Unit = { selected ->
                        reminderFrequency = selected
                        notificationPreferenceStore.reminderFrequency = selected
                        NotificationScheduler.reschedule(applicationContext)
                    }
                    NavGraph(
                        navController = navController,
                        themeMode = themeMode,
                        onThemeModeChange = { selectedThemeMode ->
                            themeMode = selectedThemeMode
                            themePreferenceStore.themeMode = selectedThemeMode
                        },
                        reminderFrequency = reminderFrequency,
                        onReminderFrequencyChange = changeFrequency,
                        notificationsGranted = notificationsGranted,
                        shouldOfferNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !notificationsGranted && !hasShownNotificationEducation,
                        onNotificationEducationShown = {
                            hasShownNotificationEducation = true
                            notificationPreferenceStore.hasShownEducation = true
                        },
                        onRequestNotificationPermission = ::requestNotificationPermission,
                    )

                    if (!onboardingComplete) {
                        OnboardingDialog(
                            reminderFrequency = reminderFrequency,
                            onReminderFrequencyChange = changeFrequency,
                            notificationsGranted = notificationsGranted,
                            onRequestNotificationPermission = ::requestNotificationPermission,
                            onComplete = {
                                notificationPreferenceStore.hasCompletedOnboarding = true
                                notificationPreferenceStore.hasShownEducation = true
                                hasShownNotificationEducation = true
                                onboardingComplete = true
                            },
                        )
                    }

                    LaunchedEffect(pendingWordId, pendingOverdueReview, onboardingComplete) {
                        if (!onboardingComplete) return@LaunchedEffect
                        val wordId = pendingWordId
                        when {
                            pendingOverdueReview -> {
                                pendingOverdueReview = false
                                navController.navigate(Screen.OverdueReview.route) {
                                    launchSingleTop = true
                                }
                            }
                            wordId != null -> {
                                pendingWordId = null
                                navController.navigate(Screen.Quiz.createRoute(wordId)) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        val runtimePermissionMissing =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        if (runtimePermissionMissing) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        notificationsGranted = hasNotificationPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        pendingWordId = intent.getStringExtra("wordId")
        pendingOverdueReview = intent.getBooleanExtra(ReviewNotification.EXTRA_OPEN_REVIEW, false)
        intent.removeExtra("wordId")
        intent.removeExtra(ReviewNotification.EXTRA_OPEN_REVIEW)
    }

    private fun hasNotificationPermission(): Boolean {
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        return runtimePermissionGranted &&
            NotificationManagerCompat.from(this).areNotificationsEnabled()
    }
}
