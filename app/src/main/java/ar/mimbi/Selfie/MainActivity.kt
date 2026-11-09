package ar.mimbi.Selfie

import android.Manifest
import android.os.Bundle
import androidx.core.view.ViewCompat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import android.os.Build
import android.graphics.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import ar.mimbi.Selfie.data.AppOrientation
import ar.mimbi.Selfie.data.ConfigDataStore
import ar.mimbi.Selfie.data.ErrorLogger
import ar.mimbi.Selfie.data.UserActionTracker
import ar.mimbi.Selfie.data.db.AppDatabase
import ar.mimbi.Selfie.printing.printer.PrinterManager
import ar.mimbi.Selfie.printing.queue.PrintQueueManager
import ar.mimbi.Selfie.printing.queue.PrintQueueRepository
import ar.mimbi.Selfie.printing.ui.owner.PrinterConfigScreen
import ar.mimbi.Selfie.printing.ui.owner.PrintingScreen
import ar.mimbi.Selfie.printing.ui.owner.QueueManagementScreen
import ar.mimbi.Selfie.printing.ui.owner.TemplateConfigScreen
import ar.mimbi.Selfie.ui.components.EnforceAppOrientation
import ar.mimbi.Selfie.ui.screens.CaptureScreen
import ar.mimbi.Selfie.ui.screens.ConfigurationScreen
import ar.mimbi.Selfie.ui.screens.ErrorHistoryScreen
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

        // Enable edge-to-edge and immersive mode
        enableEdgeToEdge()

        // Hide system bars
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        // Set system gesture exclusion rects to prevent transient bars from peeking during scrolling
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, windowInsets ->
                val navBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
                val statusBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())

                if (view.width > 0 && view.height > 0) {
                    val exclusionRects = listOf(
                        Rect(0, 0, view.width, statusBarInsets.top.coerceAtLeast(1)),
                        Rect(0, view.height - navBarInsets.bottom.coerceAtLeast(1), view.width, view.height)
                    )
                    view.systemGestureExclusionRects = exclusionRects
                }
                windowInsets
            }
        }

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ -> }

        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )

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

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun SelfieApp(configDataStore: ConfigDataStore) {
    val navController = rememberNavController()
    val configState = configDataStore.appConfigFlow.collectAsState(initial = null)
    val config = configState.value
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    EnforceAppOrientation(config?.appOrientation)

    val printQueueManager = remember(context) {
        val database = AppDatabase.getDatabase(context)
        val repository = PrintQueueRepository(database)
        val printerManager = PrinterManager()
        PrintQueueManager(repository, printerManager)
    }

    // Listen to navigation changes to track screen
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            val route = entry.destination.route ?: "Unknown"
            UserActionTracker.updateScreen(route)
        }
    }

    // State to track if the main screen content (image) is ready
    var isMainContentReady by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (config != null) {
            NavHost(
                navController = navController,
                startDestination = "main",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("main") {
                    CaptureScreen(
                        config = config,
                        onNavigateToConfig = { navController.navigate("config") },
                        onImageReady = { isMainContentReady = true },
                        onPrint = { photoUri, copies ->
                            scope.launch {
                                printQueueManager.enqueuePhoto(context, photoUri, copies)
                                configDataStore.incrementPrintCount(copies)
                            }
                        }
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
                        onNavigateToErrorHistory = { navController.navigate("error_history") },
                        onNavigateToPrinting = { navController.navigate("printing") }
                    )
                }
                composable("printing") {
                    PrintingScreen(
                        initialConfig = config,
                        onSave = { newConfig ->
                            scope.launch {
                                configDataStore.saveConfig(newConfig)
                            }
                        },
                        onBack = { navController.popBackStack() },
                        onNavigateToPrinterConfig = {
                            navController.navigate("printer_config")
                        },
                        onNavigateToTemplateConfig = {
                            navController.navigate("template_config")
                        },
                        onNavigateToQueueManagement = {
                            navController.navigate("queue_management")
                        },
                        queueManager = printQueueManager
                    )
                }
                composable("queue_management") {
                    QueueManagementScreen(
                        queueManager = printQueueManager,
                        appOrientation = config.appOrientation,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("template_config") {
                    TemplateConfigScreen(
                        queueManager = printQueueManager,
                        appOrientation = config.appOrientation,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("printer_config") {
                    PrinterConfigScreen(
                        appOrientation = config.appOrientation,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("error_history") {
                    ErrorHistoryScreen(
                        appOrientation = config.appOrientation,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = config == null || !isMainContentReady,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SplashScreen()
        }
    }
}
