package com.BWPStudio.JITAIWizard

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.BWPStudio.JITAIWizard.ui.debugview.DebugViewScreen
import com.BWPStudio.JITAIWizard.ui.settings.SettingsScreen
import com.BWPStudio.JITAIWizard.ui.userview.UserViewScreen
import com.example.jitaicompanion.convention.locale.LocaleController

private object Routes {
    const val USER = "user"
    const val DEBUG = "debug"
    const val SETTINGS = "settings"
}

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleController.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppNavHost() }
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
            DebugViewScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
