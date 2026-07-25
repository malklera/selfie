# Selfie – Implementation Plan

An Android tablet app designed for parties. Fullscreen, always-on, front and back camera support, configurable countdown, and internal gallery.

---

## Decisions Made (Based on User Answers)

| Question | Answer |
|---|---|
| "Gift" | Animated **GIF** |
| Overlay | A single image selected in config via file picker |
| Gallery | Only photos taken by this app, from the configured folder |
| Config access | Open for now; designed to add a PIN lock later |
| Save folder | Selected by the operator in config (folder picker) |
| Post-capture buttons | Floating over the taken photo |
| Main content | Pre-loaded by the operator in config (read directly via URI) |
| Android minimum | **API 23** (Android 6.0) – maximum compatibility |
| Camera Flip Button | **Only** displayed on the Main screen |
| Retake Buttons | Two optional buttons: one returns to Main, one goes directly to Preview |

---

## Tech Stack

| Area | Choice | Reason |
|---|---|---|
| Language | Kotlin | Required |
| UI | Jetpack Compose | Modern, less boilerplate, tablet-friendly |
| Camera | **CameraX** (`camera-camera2`, `camera-lifecycle`, `camera-view`) | Android API for camera; front+back; lifecycle-aware |
| Video in main | `VideoView` + `MediaPlayer` (Android stdlib) | Standard device API, no extra dependencies |
| GIF in main | **Coil 3** (`coil-gif`) | Only justified external dependency; Android has no native Compose GIF support |
| Gallery images | **Coil 3** | Compose-native, efficient |
| Config persistence| `DataStore<Preferences>` (Jetpack) | Modern, async, replaces SharedPreferences |
| Navigation | `Navigation Compose` | Standard for Compose |
| Concurrency | Kotlin Coroutines + Flow | Countdown timer, disk operations |
| DI | Manual (no Hilt) | Minimize external dependencies |
| Build | Gradle (Kotlin DSL) | Standard |

**Chosen Formats:**
- Photos → **JPEG** (`.jpg`), name = timestamp (`yyyy-MM-dd_HH-mm-ss.jpg`)
- Main screen video → **MP4** (`.mp4`)
- Main screen GIF → **GIF** (`.gif`)
- Main screen / overlay image → **PNG** or **JPEG**, this need transparency.

**Android SDK:**
- `minSdk = 23` (Android 6.0)
- `targetSdk = 36` (required for Google Play 2026)
- `compileSdk = 36`

---

## Project Structure

```text
app/src/main/
├── kotlin/com/selfie/
│   │
│   ├── SelfieApp.kt                        # Application class
│   ├── MainActivity.kt                     # Single Activity, NavHost, fullscreen, keep-screen-on
│   │
│   ├── data/
│   │   ├── preferences/
│   │   │   ├── AppPreferences.kt           # DataStore keys & default values
│   │   │   └── PreferencesRepository.kt    # Read/write config
│   │   └── gallery/
│   │       └── GalleryRepository.kt        # List JPEGs from configured folder
│   │
│   ├── domain/
│   │   └── model/
│   │       ├── AppConfig.kt                # Data class with all config
│   │       ├── MainContent.kt              # Sealed class: None | Image | Video | Gif
│   │       └── ButtonSlot.kt              # Data class for extensible button system
│   │
│   └── ui/
│       ├── navigation/
│       │   └── AppNavGraph.kt              # Navigation routes
│       │
│       ├── main/
│       │   ├── MainScreen.kt               # Main screen (idle)
│       │   └── MainViewModel.kt
│       │
│       ├── preview/
│       │   ├── PreviewScreen.kt            # Camera preview + countdown + overlay
│       │   └── PreviewViewModel.kt
│       │
│       ├── capture/
│       │   ├── CaptureResultScreen.kt      # Taken photo + floating buttons
│       │   └── CaptureResultViewModel.kt
│       │
│       ├── gallery/
│       │   ├── GalleryScreen.kt            # Grid gallery
│       │   ├── GalleryDetailScreen.kt      # Individual photo view
│       │   └── GalleryViewModel.kt
│       │
│       ├── config/
│       │   ├── ConfigScreen.kt             # Configuration screen
│       │   └── ConfigViewModel.kt
│       │
│       └── components/
│           ├── MainContentView.kt          # Renders None/Image/Video/GIF
│           ├── CameraFlipButton.kt         # Floating button to switch camera
│           ├── CountdownOverlay.kt         # Countdown overlay
│           ├── TransparentOverlay.kt       # Semi-transparent image over preview
│           └── FloatingActionButtonSlot.kt # Extensible button system
│
└── res/
    ├── values/strings.xml                  # ALL user-facing text in Spanish
    └── values/themes.xml                   # Fullscreen theme
```

---

## Screen Flow

```text
[App opens]
     │
     ▼
MainActivity (fullscreen, keep-screen-on, FLAG_IMMERSIVE_STICKY)
     │
     ▼
┌────────────────────────────────────────────────┐
│              MainScreen (idle)                 │
│  Shows: Nothing / Image / Video / GIF          │
│  Optional button: [Switch camera] (floating)   │
│                                                │
│  [5 tap top-right corner] → ConfigScreen       │
│  [tap anywhere else]    → PreviewScreen        │
└────────────────────────────────────────────────┘
     │ tap screen
     ▼
┌────────────────────────────────────────────────┐
│             PreviewScreen                      │
│  Selected camera preview                       │
│  Transparent image overlay (if configured)     │
│  Countdown (e.g.: 3...2...1...)                │
│                                                │
│  [countdown reaches 0] → takes photo           │
└────────────────────────────────────────────────┘
     │ photo taken
     ▼
┌────────────────────────────────────────────────┐
│           CaptureResultScreen                  │
│  Shows the photo just taken                    │
│  FLOATING buttons (all optional in config):    │
│    [🏠 Back to Main]   → MainScreen            │
│    [📸 Quick Retake]   → PreviewScreen         │
│    [🖼️ Gallery]       → GalleryScreen          │
│    [+ future buttons via ButtonSlot]           │
└────────────────────────────────────────────────┘
     │ tap Gallery
     ▼
┌────────────────────────────────────────────────┐
│             GalleryScreen                      │
│  LazyVerticalGrid with photos from folder      │
│  [tap photo] → GalleryDetailScreen             │
│  [← Back] → CaptureResultScreen                │
└────────────────────────────────────────────────┘

ConfigScreen (accessible from MainScreen, tap top-right)
  ├── Default camera (Front / Back)
  ├── Show camera switch button on Main (yes/no)
  ├── Countdown duration (0-30 seconds)
  ├── Preview overlay image (file picker → PNG/JPG)
  ├── Save folder (folder picker)
  ├── Main screen content:
  │     Type: None / Image / Video / GIF
  │     File: file picker
  ├── Show "Back to Main" button (yes/no)
  ├── Show "Quick Retake" button (yes/no)
  └── Show "Gallery" button (yes/no)
```

---

## Implementation Details

### Fullscreen and Keep-Screen-On

```kotlin
// MainActivity.kt
override fun onCreate(...) {
    // Immersive fullscreen
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    // Keep screen on
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}
```

### Camera (CameraX)

- `ProcessCameraProvider` to bind use cases to the lifecycle.
- `Preview` + `ImageCapture` bound to the `LifecycleOwner`.
- `PreviewView` wrapped in `AndroidView` in Compose.
- Camera switch: unbind all → re-bind with new `CameraSelector` (only exposed via `MainScreen`).
- The session camera selection lives in the ViewModel and is **not persisted** across app restarts.

### Countdown

```kotlin
// PreviewViewModel.kt
private fun startCountdown(seconds: Int) {
    viewModelScope.launch {
        for (remaining in seconds downTo 0) {
            _countdownState.value = remaining
            if (remaining == 0) {
                capturePhoto()
                break
            }
            delay(1000L)
        }
    }
}
```

### Extensible Button System

```kotlin
// domain/model/ButtonSlot.kt
data class ButtonSlot(
    val id: String,
    val label: String,          // in Spanish for the UI
    val icon: ImageVector,
    val visible: Boolean,
    val onClick: () -> Unit
)

// CaptureResultScreen base list:
val buttons = listOf(
    ButtonSlot("to_main", "Volver al inicio", Icons.Home, config.showToMainButton) { navToMain() },
    ButtonSlot("quick_retake", "Otra foto", Icons.Camera, config.showQuickRetakeButton) { navToPreview() },
    ButtonSlot("gallery", "Galería", Icons.Photo, config.showGalleryButton) { navToGallery() }
)
```

### Saving Photos

- `ImageCapture.OutputFileOptions.Builder(file)` using the configured folder.
- Name: `SimpleDateFormat("yyyyMMdd_HHmmss").format(Date()) + ".jpg"`.
- Folder selected with `ActivityResultContracts.OpenDocumentTree` (system folder picker).
- URI persistence with `takePersistableUriPermission`.

### Gallery

- `GalleryRepository` lists all `.jpg` files in the configured folder sorted by date.
- `LazyVerticalGrid` in Compose using Coil for efficient loading.
- `GalleryDetailScreen` with a pager to navigate between photos.

### MainScreen Content

```kotlin
// domain/model/MainContent.kt
sealed class MainContent {
    object None : MainContent()
    data class StaticImage(val uri: Uri) : MainContent()
    data class Video(val uri: Uri) : MainContent()
    data class AnimatedGif(val uri: Uri) : MainContent()
}
```

- `None` → black background (or app theme)
- `StaticImage` → Coil `AsyncImage`
- `Video` → `AndroidView` with `VideoView`, loop enabled
- `AnimatedGif` → Coil `AsyncImage` with GIF support (`coil-gif`)
- Pre-loaded content is read directly from the URI. The app will use `takePersistableUriPermission` on the URIs selected by the user to ensure access is not lost.

### ConfigScreen – Future Password Access

Access is currently open to anyone. The route is prepared to add a PIN dialog before navigating to config in the future:

```kotlin
// MainScreen.kt – top-right tap
// TODO: PIN verification will be inserted here in the future
navController.navigate(Route.Config)
```

---

## Dependencies (app/build.gradle.kts)

```kotlin
dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Coil (images + GIF)
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-gif:3.0.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
```

---

## Spanish Strings (res/values/strings.xml)

ALL user-facing text will be in `strings.xml` in Spanish. The code and logs will be in English.

```xml
<string name="btn_volver_inicio">Volver al inicio</string>
<string name="btn_otra_foto">Otra foto</string>
<string name="btn_galeria">Galería</string>
<!-- etc. -->
```

---

## Verification Plan

### Manual testing

| Feature | Verification |
|---|---|
| App fullscreen | No system bars visible |
| Keep screen on | Device does not sleep |
| Tap main → preview | Camera preview appears immediately with countdown |
| Countdown | Counts from N to 0 correctly |
| Photo saved | `.jpg` file with timestamp is saved in the correct folder |
| Preview overlay | Transparent image is shown over the camera preview |
| Camera switch | Works from Main screen, affects session only |
| Gallery | Shows only photos from the configured folder |
| Config persistence | Settings remain after app restart |
| Spanish text | All visible text is in Spanish |
| Optional buttons | Toggle buttons on/off from config |
