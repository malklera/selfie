package ar.mimbi.Selfie

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ar.mimbi.Selfie.data.AppConfig
import ar.mimbi.Selfie.data.ConfigDataStore
import ar.mimbi.Selfie.ui.screens.CaptureScreen
import ar.mimbi.Selfie.ui.screens.ConfigurationScreen
import ar.mimbi.Selfie.ui.screens.MainScreen
import ar.mimbi.Selfie.ui.theme.SelfieTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var configDataStore: ConfigDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        configDataStore = ConfigDataStore(applicationContext)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Fullscreen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        enableEdgeToEdge()
        
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            // Handle permissions
        }
        
        requestPermissionLauncher.launch(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ))

        setContent {
            SelfieTheme(darkTheme = true) { // Force Dark Theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SelfieApp(configDataStore)
                }
            }
        }
    }
}

@Composable
fun SelfieApp(configDataStore: ConfigDataStore) {
    val navController = rememberNavController()
    val config by configDataStore.appConfigFlow.collectAsState(initial = AppConfig())
    val scope = rememberCoroutineScope()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                config = config,
                onNavigateToCapture = { navController.navigate("capture") },
                onNavigateToConfig = { navController.navigate("config") }
            )
        }
        composable("config") {
            ConfigurationScreen(
                initialConfig = config,
                onSave = { newConfig ->
                    scope.launch {
                        configDataStore.saveConfig(newConfig)
                    }
                },
                onClose = { navController.popBackStack() }
            )
        }
        composable("capture") {
            CaptureScreen(
                config = config,
                onNavigateToMain = { navController.navigate("main") {
                    popUpTo("main") { inclusive = true }
                } },
                onNavigateToConfig = { navController.navigate("config") }
            )
        }
    }
}
