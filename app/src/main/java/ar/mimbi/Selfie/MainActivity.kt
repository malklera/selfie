package ar.mimbi.Selfie

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ar.mimbi.Selfie.data.ConfigDataStore
import ar.mimbi.Selfie.data.ErrorLogger
import ar.mimbi.Selfie.data.UserActionTracker
import ar.mimbi.Selfie.ui.screens.CaptureScreen
import ar.mimbi.Selfie.ui.screens.ConfigurationScreen
import ar.mimbi.Selfie.ui.screens.ErrorHistoryScreen
import ar.mimbi.Selfie.ui.screens.MainScreen
import ar.mimbi.Selfie.ui.screens.SplashScreen
import ar.mimbi.Selfie.ui.theme.SelfieTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var configDataStore: ConfigDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().apply {
            setKeepOnScreenCondition { false }
        }
        super.onCreate(savedInstanceState)
        
        ErrorLogger.init(applicationContext)
        configDataStore = ConfigDataStore(applicationContext)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // COMPLETELY DISABLE ALL FULLSCREEN/IMMERSIVE MODES FOR DEBUGGING
        // Standard window behavior ruled out to find the touch issue source
        
        val requestPermissionLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { _ -> }
        
        requestPermissionLauncher.launch(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ))

        setContent {
            SelfieTheme(darkTheme = true) {
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
    val configState = configDataStore.appConfigFlow.collectAsState(initial = null)
    val config = configState.value
    val scope = rememberCoroutineScope()
    
    // Listen to navigation changes to track screen
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            val route = entry.destination.route ?: "Unknown"
            UserActionTracker.updateScreen(route)
        }
    }

    // State to track if the main screen content (image) is ready
    var isMainContentReady by remember { mutableStateOf(false) }

    Crossfade(targetState = config == null || !isMainContentReady, label = "splashTransition") { showSplash ->
        if (showSplash) {
            SplashScreen()
        } else {
            if (config != null) {
                NavHost(
                    navController = navController,
                    startDestination = "main",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("main") {
                        MainScreen(
                            config = config,
                            onNavigateToCapture = { navController.navigate("capture") },
                            onNavigateToConfig = { navController.navigate("config") },
                            onImageReady = { isMainContentReady = true }
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
                            onClose = { navController.popBackStack() },
                            onNavigateToErrorHistory = { navController.navigate("error_history") }
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
                    composable("error_history") {
                        ErrorHistoryScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
    
    // Trigger image load in background if not already started
    if (config != null && !isMainContentReady) {
        Box(modifier = Modifier.fillMaxSize().alpha(0f)) {
            MainScreen(
                config = config,
                onNavigateToCapture = {},
                onNavigateToConfig = {},
                onImageReady = { isMainContentReady = true }
            )
        }
    }
}
