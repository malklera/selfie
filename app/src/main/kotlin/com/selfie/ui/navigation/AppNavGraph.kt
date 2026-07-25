package com.selfie.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.material3.Text

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Route.Main.path
    ) {
        composable(Route.Main.path) {
            Text("Main Screen")
        }
        composable(Route.Preview.path) {
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
