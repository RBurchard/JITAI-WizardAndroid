package com.BWPStudio.JITAIWizard

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.BWPStudio.JITAIWizard.ui.debugview.DebugViewScreen
import com.BWPStudio.JITAIWizard.ui.settings.GameSettingsScreen
import com.BWPStudio.JITAIWizard.ui.settings.SettingsScreen
import com.BWPStudio.JITAIWizard.ui.theme.JitaiTheme
import com.BWPStudio.JITAIWizard.ui.userview.UserViewScreen
import com.example.jitaicompanion.convention.locale.LocaleController

private object Routes {
    const val USER = "user"
    const val DEBUG = "debug"
    const val SETTINGS = "settings"
    const val GAME_SETTINGS = "game_settings"
}

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleController.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pinned to the dark style instead of enableEdgeToEdge()'s auto mode: the app paints its
        // own dark surfaces on every screen, so on a phone set to light mode auto would hand the
        // status bar dark icons to sit on top of them.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { JitaiTheme { AppNavHost() } }
    }
}

@Composable
private fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.USER) {
        composable(Routes.USER) {
            UserViewScreen(
                onDebugUnlocked = { navController.navigate(Routes.DEBUG) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.DEBUG) {
            DebugViewScreen(
                onBack = { navController.popBackStack() },
                onGameSettings = { navController.navigate(Routes.GAME_SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onGameSettings = { navController.navigate(Routes.GAME_SETTINGS) }
            )
        }
        composable(Routes.GAME_SETTINGS) {
            GameSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
