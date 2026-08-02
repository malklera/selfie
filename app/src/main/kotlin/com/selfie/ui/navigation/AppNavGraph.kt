package com.selfie.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.selfie.SelfieApp
import com.selfie.ui.ViewModelFactory
import com.selfie.ui.capture.CaptureScreen
import com.selfie.ui.capture.CaptureViewModel
import com.selfie.ui.config.ConfigScreen
import com.selfie.ui.config.ConfigViewModel
import com.selfie.ui.main.MainScreen
import com.selfie.ui.main.MainViewModel

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = Route.Main.route
) {
    val context = LocalContext.current.applicationContext as SelfieApp
    val factory = ViewModelFactory(context.preferencesRepository)

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Route.Main.route) {
            val mainViewModel: MainViewModel = viewModel(factory = factory)
            MainScreen(
                viewModel = mainViewModel,
                onNavigateToCapture = {
                    navController.navigate(Route.Capture.route)
                },
                onNavigateToConfig = {
                    navController.navigate(Route.Config.route)
                }
            )
        }

        composable(Route.Config.route) {
            val configViewModel: ConfigViewModel = viewModel(factory = factory)
            ConfigScreen(
                viewModel = configViewModel,
                onClose = {
                    navController.popBackStack()
                }
            )
        }

        composable(Route.Capture.route) {
            val captureViewModel: CaptureViewModel = viewModel(factory = factory)
            CaptureScreen(
                viewModel = captureViewModel,
                onNavigateToConfig = {
                    navController.navigate(Route.Config.route)
                },
                onNavigateToMain = {
                    navController.navigate(Route.Main.route) {
                        popUpTo(Route.Main.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
