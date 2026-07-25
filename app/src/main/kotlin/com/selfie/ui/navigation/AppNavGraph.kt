package com.selfie.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.material3.Text

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.selfie.SelfieApp
import com.selfie.ui.ViewModelFactory
import com.selfie.ui.main.MainScreen
import com.selfie.ui.main.MainViewModel

@Composable
fun AppNavGraph(navController: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as SelfieApp
    val factory = remember { 
        ViewModelFactory(app.preferencesRepository, app.galleryRepository) 
    }

    NavHost(
        navController = navController,
        startDestination = Route.Main.path
    ) {
        composable(Route.Main.path) {
            val viewModel: MainViewModel = viewModel(factory = factory)
            MainScreen(
                viewModel = viewModel,
                onNavigateToPreview = { isFront ->
                    navController.navigate(Route.Preview.createRoute(isFront))
                },
                onNavigateToConfig = {
                    navController.navigate(Route.Config.path)
                }
            )
        }
        composable(
            route = Route.Preview.path,
            arguments = listOf(navArgument("isFront") { type = NavType.BoolType })
        ) {
            Text("Preview Screen")
        }
        composable(Route.CaptureResult.path) {
            Text("Capture Result Screen")
        }
        composable(Route.Gallery.path) {
            Text("Gallery Screen")
        }
        composable(Route.GalleryDetail.path) {
            Text("Gallery Detail Screen")
        }
        composable(Route.Config.path) {
            Text("Config Screen")
        }
    }
}
