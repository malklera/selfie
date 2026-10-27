# Image Pipeline Implementation TODO (imgTODO.md)

Based on `imgPlan.md` and audit against the current state of the codebase.

**Current Status**: Plan only — no code modifications for `imgPlan.md` have been applied yet.

---

## Task Breakdown & Checklist

### Phase 1: Data Model & DataStore Persistence
- [x] **1.1 Update Enums and AppConfig (`data/AppConfig.kt`)**
  - Add `enum class PreviewFit { FIT, FILL, FIXED_ASPECT }`
  - Add `enum class FixedAspect { R4_3, R16_9 }`
  - Add `enum class DecorationFit { STRETCH, COVER }`
  - Update `AppConfig` fields:
    - Replace `pictureResolution` with `inputResolution: String? = null` (or alias/keep data compatibility)
    - Add `previewFit: PreviewFit = PreviewFit.FIT`
    - Add `fixedAspect: FixedAspect = FixedAspect.R16_9`
    - Add `decorationFit: DecorationFit = DecorationFit.STRETCH`
- [x] **1.2 Update DataStore (`data/ConfigDataStore.kt`)**
  - Keep `PICTURE_RESOLUTION` ("picture_resolution") preference key for backwards compatibility.
  - Add preference keys:
    - `PREVIEW_FIT` (string preference key: "preview_fit")
    - `FIXED_ASPECT` (string preference key: "fixed_aspect")
    - `DECORATION_FIT` (string preference key: "decoration_fit")
    - `LAST_SAVED_BYTES_PER_PIXEL` (double preference key: "last_saved_bytes_per_pixel")
    - `SAVED_SAMPLE_COUNT` (int preference key: "saved_sample_count")
  - Update `appConfigFlow` and `saveConfig` to handle these fields with proper default values.

---

### Phase 2: Geometry & Resolution Helper Logic
- [x] **2.1 Geometry Helper Functions (`data/ImageGeometryHelper.kt` or inside `CameraResolutionHelper.kt`)**
  - Implement `coverCrop(frameW: Int, frameH: Int, targetAspect: Float): Rect`
    - Crops center of `frameW x frameH` to match `targetAspect` without scaling.
  - Implement `fitSize(containerW: Float, containerH: Float, aspect: Float): SizeF` (or `DpSize`/`Size`)
    - Computes fitting dimensions inside a container preserving aspect ratio.
  - Implement output region calculator `calculateOutputRegion(...)`:
    - Mode `a` (FIT) -> Full frame native aspect `P`.
    - Mode `b` (FILL) -> Center crop to screen aspect `S`.
    - Mode `c` (FIXED_ASPECT) -> Center crop to chosen `T` (4:3 or 16:9).
- [x] **2.2 Refactor `CameraResolutionHelper.kt`**
  - Add `NativeSizes` data class: `(val aspect: Float, val sizes: List<CameraResolution>)`
  - Implement `getNativeSizes(context: Context): NativeSizes`
    - Read `JPEG` output sizes from `SCALER_STREAM_CONFIGURATION_MAP`.
    - Find the largest size by pixel count -> determine sensor native aspect ratio `P`.
    - Filter sizes to only those matching aspect `P` (within 2% tolerance).
    - Sort descending by total pixel count.
  - Implement `getMaxResolution(context: Context): CameraResolution?`
  - Implement `estimateFileSize(pixels: Long, bytesPerPixel: Double = 0.35): Double` (returns MB).
  - Retain `parseResolution(str: String?)` for stored key parsing and fallback validation.

---

### Phase 3: Unit Tests
- [x] **3.1 Add Unit Tests (`app/src/test/java/ar/mimbi/Selfie/ImageGeometryHelperTest.kt`)**
  - Test `coverCrop`:
    - 4:3 frame to 16:9 crop
    - 4:3 frame to custom screen aspect ratio
    - Equal aspects (no crop)
    - Rounding and odd pixel sizes handling
  - Test `fitSize`:
    - Container wider than aspect
    - Container taller than aspect
  - Test `calculateOutputRegion` for modes a, b, and c.
- [x] **3.2 Update `CameraResolutionHelperTest.kt`**
  - Test native aspect filtering (ignoring non-native aspect sizes).
  - Test file size estimation calculations.

---

### Phase 4: Camera & Preview Rework (`ui/screens/CaptureScreen.kt`)
- [x] **4.1 Update Camera Binding & Quality Settings**
  - Change `ImageCapture` mode to `CAPTURE_MODE_MAXIMIZE_QUALITY`.
  - Set Jpeg Quality to `100` (`.setJpegQuality(100)`).
  - Set separate `ResolutionSelector` for `Preview`:
    - Same aspect ratio strategy based on `P`.
    - No fixed resolution strategy (let CameraX pick best stream size).
  - Remove `ViewPort` configuration (`previewView.viewPort`) to prevent CameraX auto-cropping.
  - Set `PreviewView.scaleType = PreviewView.ScaleType.FILL_CENTER`.
- [x] **4.2 Implement Fitted Preview Viewfinder Layout**
  - Compute output region `R` from `imageCapture.resolutionInfo` (or sensor frame size + user mode settings + screen aspect ratio `S`).
  - Render outer `Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center)`:
    - Inner `Box` sized to `fitSize(screenSize, R.aspect)` and clipped to bounds.
    - `AndroidView(previewView)` fill max size inside inner box.
    - Decoration `Image(captureBoxBitmap)` fill max size inside inner box using `DecorationFit` (`FillBounds` for STRETCH, `Crop` for COVER).

---

### Phase 5: Save Pipeline Rework (`ui/screens/CaptureScreen.kt`)
- [ ] **5.1 Refactor Image Processing Function**
  - Rename `loadProcessAndCropBitmap` -> `loadProcessAndDecorateBitmap`.
  - Pass output region `R` and `DecorationFit` instead of `previewWidth`/`previewHeight`.
  - Step 1: Decode full size temporary JPEG and apply EXIF orientation / front camera mirror.
  - Step 2: Single-pass crop to region `R` at 1:1 native pixels.
  - Step 3: Draw decoration onto `R` canvas using `DecorationFit` rule:
    - **STRETCH**: Stretch decoration overlay over `R`.
    - **COVER**: Scale decoration overlay keeping proportions to cover `R`, centering and clipping overflow.
  - Step 4: Avoid redundant intermediate full-size bitmap allocations (use direct canvas blitting).
- [ ] **5.2 Update `saveBitmapToDestination`**
  - Set JPEG compression quality to `100` (changed from `95`).
- [ ] **5.3 Update Logcat Logging**
  - Log real `resolutionInfo` from camera.
  - Log output region `R` (dimensions and aspect).
  - Log saved image size and file size in bytes.

---

### Phase 6: Configuration UI Rework (`ui/screens/ConfigurationScreen.kt`)
- [ ] **6.1 Image Size Selector Section**
  - Rename "Calidad de las fotos" -> "Tamaño de la imagen".
  - Populate dropdown using `CameraResolutionHelper.getNativeSizes(context)`.
  - Display "Máxima calidad" for the top/first option along with dimensions and estimated file size (`≈ X.X MB`).
  - Fallback to highest available if saved setting is invalid or missing.
- [ ] **6.2 Preview Fit Section ("Vista previa")**
  - Add selector for `PreviewFit` modes:
    - `Ajustar` (FIT)
    - `Rellenar` (FILL)
    - `Relación fija` (FIXED_ASPECT)
  - If `Relación fija` is selected, show sub-choice for `FixedAspect` (`4:3` vs `16:9`).
- [ ] **6.3 Decoration Settings Section**
  - Add `DecorationFit` selector (`STRETCH` vs `COVER`) under the "Cuadro de captura" section.
  - Add helper text explaining the difference: "STRETCH deforma si la proporción no coincide; COVER recorta los bordes".
- [ ] **6.4 Diagnostics Line & Warning Cleanup**
  - Replace old aspect ratio warning banner with informational diagnostic block showing:
    `Pantalla: [W]x[H] ([S]) · Entrada: [W]x[H] ([P]) · Salida: [W]x[H] ([R]) · ≈ [MB] MB`
- [ ] **6.5 Update `hasChanges` logic and Save callback**
  - Include new fields (`previewFit`, `fixedAspect`, `decorationFit`, `inputResolution`) in state checks and save handling.

---

### Phase 7: Self-Calibration & Companion Polish
- [ ] **7.1 Calibration of Estimated File Size**
  - Calculate `bytesPerPixel` after saving photo (`fileSize / R.pixels`).
  - Update running average in `ConfigDataStore` (`last_saved_bytes_per_pixel`).
- [ ] **7.2 Diagnostics & Cleanup**
  - Verify migration for existing user installs (graceful fallback for old resolution settings).
  - Cleanup unused code, legacy 4:3 / 9:16 grouping methods, and unused function arguments.

---

### Phase 8: Acceptance Criteria & Manual Verification
- [ ] **8.1 Verify Resolution List**: Only sensor native aspect sizes listed in Configuration dropdown.
- [ ] **8.2 Verify Preview and File WYSIWYG**: Aspect of preview matches saved image aspect exactly in all 3 modes (FIT, FILL, FIXED_ASPECT).
- [ ] **8.3 Verify Black Bars**: Black bars appear only in preview when preview aspect differs from screen aspect; no black bars in saved JPEG file.
- [ ] **8.4 Verify Decoration Behavior**: Decoration matches preview and saved file identically under STRETCH and COVER modes.
- [ ] **8.5 Verify Quality & Performance**: Full resolution photo saved without upscaling or OOM errors.
