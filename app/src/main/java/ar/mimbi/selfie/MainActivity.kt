package ar.mimbi.selfie

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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalContext
import ar.mimbi.selfie.data.AppConfig
import ar.mimbi.selfie.data.CameraResolutionHelper
import ar.mimbi.selfie.data.ConfigDataStore
import ar.mimbi.selfie.data.ConfigSessionState
import ar.mimbi.selfie.data.ErrorLogger
import ar.mimbi.selfie.data.UserActionTracker
import ar.mimbi.selfie.data.db.AppDatabase
import ar.mimbi.selfie.printing.printer.PrinterManager
import ar.mimbi.selfie.printing.queue.PrintQueueManager
import ar.mimbi.selfie.printing.queue.PrintQueueRepository
import ar.mimbi.selfie.printing.template.DefaultTemplates
import ar.mimbi.selfie.printing.ui.owner.PrinterConfigScreen
import ar.mimbi.selfie.printing.ui.owner.PrintingScreen
import ar.mimbi.selfie.printing.ui.owner.QueueManagementScreen
import ar.mimbi.selfie.printing.ui.owner.TemplateConfigScreen
import ar.mimbi.selfie.ui.components.EnforceAppOrientation
import ar.mimbi.selfie.ui.screens.CaptureScreen
import ar.mimbi.selfie.ui.screens.ConfigurationScreen
import ar.mimbi.selfie.ui.screens.ErrorHistoryScreen
import ar.mimbi.selfie.ui.screens.SplashScreen
import ar.mimbi.selfie.ui.theme.SelfieTheme
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

        val permissionsToRequest = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }

        requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())

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

    val printQueueManager = remember(context, configDataStore) {
        val database = AppDatabase.getDatabase(context)
        val repository = PrintQueueRepository(database, configDataStore)
        val printerManager = PrinterManager()
        PrintQueueManager(repository, printerManager)
    }

    val activeTemplate by printQueueManager.activeTemplateFlow.collectAsState(initial = DefaultTemplates.TEMPLATE_FULL_WIDTH_SINGLE)

    val nativeSizes = remember(context) { CameraResolutionHelper.getNativeSizes(context) }
    val defaultResKey = remember(nativeSizes) { nativeSizes.sizes.firstOrNull()?.key }

    var configSession by remember { mutableStateOf<ConfigSessionState?>(null) }

    fun getOrCreateSession(cfg: AppConfig, tmplId: Int): ConfigSessionState {
        var sess = configSession
        if (sess == null || sess.isSaved) {
            sess = ConfigSessionState(cfg, tmplId, defaultResKey)
            configSession = sess
        }
        return sess
    }

    EnforceAppOrientation(configSession?.appOrientation ?: config?.appOrientation)

    // Listen to navigation changes to track screen
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            val route = entry.destination.route ?: "Unknown"
            UserActionTracker.updateScreen(route)
            if (route == "main") {
                configSession = null
            }
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
                        onNavigateToConfig = {
                            configSession = ConfigSessionState(config, activeTemplate.id, defaultResKey)
                            navController.navigate("config")
                        },
                        onImageReady = { isMainContentReady = true },
                        onPrint = { photoUri, copies ->
                            scope.launch {
                                printQueueManager.enqueuePhoto(photoUri, copies)
                                configDataStore.incrementPrintCount(copies)
                            }
                        }
                    )
                }
                composable("config") {
                    val session = getOrCreateSession(config, activeTemplate.id)
                    ConfigurationScreen(
                        initialConfig = config,
                        session = session,
                        onSave = { newConfig ->
                            scope.launch {
                                configDataStore.saveConfig(newConfig)
                                if (session.wasResetToZero) {
                                    printQueueManager.clearPrintedItems()
                                }
                                if (session.selectedTemplateId != session.initialTemplateId) {
                                    printQueueManager.setActiveTemplate(session.selectedTemplateId)
                                }
                            }
                            session.isSaved = true
                            configSession = null
                            navController.popBackStack("main", false)
                        },
                        onClose = {
                            session.isSaved = false
                            configSession = null
                            navController.popBackStack("main", false)
                        },
                        onNavigateToErrorHistory = { navController.navigate("error_history") },
                        onNavigateToPrinting = { navController.navigate("printing") }
                    )
                }
                composable("printing") {
                    val session = getOrCreateSession(config, activeTemplate.id)
                    PrintingScreen(
                        initialConfig = config,
                        session = session,
                        onBack = { navController.popBackStack() },
                        onNavigateToPrinterConfig = {
                            navController.navigate("printer_config")
                        },
                        onNavigateToTemplateConfig = {
                            navController.navigate("template_config")
                        },
                        onNavigateToQueueManagement = {
                            navController.navigate("queue_management")
                        }
                    )
                }
                composable("queue_management") {
                    val session = getOrCreateSession(config, activeTemplate.id)
                    QueueManagementScreen(
                        queueManager = printQueueManager,
                        appOrientation = session.appOrientation,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("template_config") {
                    val session = getOrCreateSession(config, activeTemplate.id)
                    TemplateConfigScreen(
                        queueManager = printQueueManager,
                        session = session,
                        appOrientation = session.appOrientation,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("printer_config") {
                    val session = getOrCreateSession(config, activeTemplate.id)
                    PrinterConfigScreen(
                        appOrientation = session.appOrientation,
                        printerManager = printQueueManager.printerManager,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("error_history") {
                    val session = getOrCreateSession(config, activeTemplate.id)
                    ErrorHistoryScreen(
                        appOrientation = session.appOrientation,
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
