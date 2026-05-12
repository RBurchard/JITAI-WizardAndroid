package com.BWPStudio.JITAIWizard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.BWPStudio.JITAIWizard.ui.debugview.DebugViewScreen
import com.BWPStudio.JITAIWizard.ui.userview.UserViewScreen

private object Routes {
    const val USER = "user"
    const val DEBUG = "debug"
}

class MainActivity : ComponentActivity() {
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
            UserViewScreen(onDebugUnlocked = { navController.navigate(Routes.DEBUG) })
        }
        composable(Routes.DEBUG) {
            DebugViewScreen(onBack = { navController.popBackStack() })
        }
    }
}
