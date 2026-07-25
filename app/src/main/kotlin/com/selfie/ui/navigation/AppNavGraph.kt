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

import android.net.Uri
import com.selfie.ui.preview.PreviewScreen
import com.selfie.ui.preview.PreviewViewModel

import com.selfie.ui.gallery.GalleryDetailScreen
import com.selfie.ui.gallery.GalleryScreen
import com.selfie.ui.gallery.GalleryViewModel

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
        ) { backStackEntry ->
            val isFront = backStackEntry.arguments?.getBoolean("isFront") ?: true
            val viewModel: PreviewViewModel = viewModel(factory = factory)
            PreviewScreen(
                viewModel = viewModel,
                isFront = isFront,
                onPhotoCaptured = { uri ->
                    val encodedUri = Uri.encode(uri.toString())
                    navController.navigate(Route.CaptureResult.createRoute(encodedUri)) {
                        popUpTo(Route.Main.path)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Route.CaptureResult.path,
            arguments = listOf(navArgument("photoUri") { type = NavType.StringType })
        ) { backStackEntry ->
            val photoUriString = backStackEntry.arguments?.getString("photoUri")
            val photoUri = Uri.parse(photoUriString)
            val viewModel: CaptureResultViewModel = viewModel(factory = factory)
            
            CaptureResultScreen(
                viewModel = viewModel,
                photoUri = photoUri,
                onNavigateToMain = {
                    navController.navigate(Route.Main.path) {
                        popUpTo(Route.Main.path) { inclusive = true }
                    }
                },
                onNavigateToPreview = {
                    navController.navigate(Route.Main.path) {
                        popUpTo(Route.Main.path) { inclusive = true }
                    }
                },
                onNavigateToGallery = {
                    navController.navigate(Route.Gallery.path)
                }
            )
        }
        composable(Route.Gallery.path) {
            val viewModel: GalleryViewModel = viewModel(factory = factory)
            GalleryScreen(
                viewModel = viewModel,
                onPhotoClick = { index ->
                    navController.navigate(Route.GalleryDetail.createRoute(index))
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Route.GalleryDetail.path,
            arguments = listOf(navArgument("index") { type = NavType.IntType })
        ) { backStackEntry ->
            val index = backStackEntry.arguments?.getInt("index") ?: 0
            val viewModel: GalleryViewModel = viewModel(factory = factory)
            GalleryDetailScreen(
                viewModel = viewModel,
                initialIndex = index,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Route.Config.path) {
            Text("Config Screen")
        }
    }
}
