# Selfie App – Implementation To-Do List

This document outlines the step-by-step order to implement the application based on the `plan.md`.

## Phase 1: Project Setup & Initialization
- [x] Initialize Android project directory structure (`app/src/main/kotlin/com/selfie/app`, `res`, etc.).
- [x] Create `.gitignore` and `settings.gradle.kts`.
- [x] Create project-level `build.gradle.kts` and `gradle/libs.versions.toml`.
- [x] Create app-level `build.gradle.kts` with all dependencies (Compose, CameraX, Coil, DataStore, Navigation).
- [x] Create `AndroidManifest.xml` (Permissions: Camera, Storage, Wake Lock; immersive mode, hardware features).
- [x] Define Spanish string resources (`strings.xml`) and fullscreen theme (`themes.xml`).

## Phase 2: Domain & Data Layers (Core State)
- [x] Define Data Models: `AppConfig`, `MainContent` (sealed class), `ButtonSlot`.
- [x] Setup Jetpack DataStore: `AppPreferenceKeys` and `PreferencesRepository`.
- [x] Setup Gallery Storage: `GalleryRepository` to list photos from the device using `DocumentFile`.

## Phase 3: Core App Setup & Navigation
- [x] Create the Application class (`SelfieApp.kt`) and configure Coil for GIF support.
- [x] Create `MainActivity.kt` with edge-to-edge full-screen configuration and `FLAG_KEEP_SCREEN_ON`.
- [x] Define Navigation routes (`Route.kt`) and create the main navigation graph (`AppNavGraph.kt`).

## Phase 4: Shared UI Components
- [x] Implement `MainContentView.kt` (renders None, Image, Video, or GIF).
- [x] Implement `CameraFlipButton.kt` (floating switch button).
- [x] Implement `CountdownOverlay.kt` (animated large text).
- [x] Implement `TransparentOverlay.kt` (semi-transparent image over preview).
- [x] Implement `FloatingButtonRow.kt` (extensible row of `ButtonSlot` components).

## Phase 5: Main Screen (Idle View)
- [x] Implement `MainViewModel.kt` (Exposes config, handles session camera state).
- [x] Implement `MainScreen.kt` (Shows `MainContentView`, invisible top-right config tap zone, tap-to-start, and camera flip button).

## Phase 6: Preview Screen (Camera & Capture)
- [x] Implement `PreviewViewModel.kt` (CameraX state, countdown logic, photo capture logic via SAF to the user-configured folder).
- [x] Implement `PreviewScreen.kt` (CameraX `PreviewView`, `TransparentOverlay`, `CountdownOverlay`, and auto-capture trigger on zero).

## Phase 7: Capture Result Screen
- [ ] Implement `CaptureResultViewModel.kt` (Exposes config for button visibility).
- [ ] Implement `CaptureResultScreen.kt` (Displays taken photo full-screen with the extensible floating button row: Home, Quick Retake, Gallery).

## Phase 8: Gallery Screen
- [ ] Implement `GalleryViewModel.kt` (Loads photos asynchronously via repository).
- [ ] Implement `GalleryScreen.kt` (`LazyVerticalGrid` with Coil async image loading).
- [ ] Implement `GalleryDetailScreen.kt` (`HorizontalPager` for fullscreen swiping between photos).

## Phase 9: Configuration Screen
- [ ] Implement `ConfigViewModel.kt` (Handles saving values to DataStore).
- [ ] Implement `ConfigScreen.kt` (UI for settings: default camera, flip button toggle, countdown duration, overlay image picker, save folder picker, main content type/file picker, and result button toggles).

## Phase 10: Final Polish & Testing
- [ ] Verify all Spanish text strings.
- [ ] Test permission handling (Camera, Storage).
- [ ] Verify fullscreen behavior and screen wake lock.
- [ ] Perform manual end-to-end flow testing (Main -> Preview -> Capture -> Result -> Gallery).
