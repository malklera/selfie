# Image pipeline plan (imgPlan.md)

Implementation plan for reworking capture resolution, preview fitting and the saved
image size / decoration composition.

Background and the quality investigation: see `imageRes.md`.
**Status: plan only — no code has been changed.**

---

## 1. Goal

1. Expose the **real sensor sizes** as a selector: the sensor's native aspect ratio only,
   "Máxima calidad" by default, then progressively smaller real sizes down to the minimum,
   each with an estimated file size.
2. Add a **preview/output fitting** option that decides which part of the sensor frame goes
   into the file (three modes, below).
3. **WYSIWYG:** the file has the *same aspect ratio as what the preview shows*. The preview
   is only scaled to the screen; that scaling is never written to the file.
4. The file is produced at the **native pixel density of the captured frame** (a 1:1 crop of
   the sensor output — never upscaled, never saved at screen resolution).
5. The decoration is **stretched to the picture/output region, not to the screen**, so it
   follows the image everywhere (preview and file).

---

## 2. Core model: one output region `R`

Everything reduces to **“which part of the captured frame goes into the file?”**

Let:
- `P` = aspect of the captured frame = **the sensor's native aspect** (the aspect of its
  largest JPEG size; practically 4:3, but whatever the device reports)
- `S` = aspect of the preview view **measured at runtime** (not a standard name; on a
  1920x1200 screen it is 1.60, but the code uses `width/height` exactly)
- `T` = chosen fixed aspect (4:3 or 16:9), used only by mode c

| Mode | UI label (es) | Output region `R` | File aspect | Preview |
|---|---|---|---|---|
| **a** | `Ajustar` | the **whole frame** (`P`) | `P` | `R` fitted into the screen → black bars on the short axis |
| **b** | `Rellenar` | center crop of the frame to **`S`** | **`S` (the screen's real aspect)** | `R` == screen → fills it, no bars |
| **c** | `Relación fija` | centre crop of the frame to **`T`** | `T` | the `T` frame fitted into the screen → black bars around the frame |

Rules:

- The file is **always** `R` at native pixel density. No scaling, no screen pixel size, no
  bars.
- The preview is **always** `R` fitted into the screen. Mode b has `R.aspect == S` by
  construction, which is why it looks like “fill and crop” and shows no bars. Modes a/c show
  bars because `R.aspect != S`. Bars are preview-only and pure black.
- Decoration is stretched over exactly `R` (same rectangle in preview and in the file).
- `R` is a plain integer-pixel centre crop, so nothing is interpolated.

### Geometry helpers (pure, unit-testable)

```text
coverCrop(frameW, frameH, aspect) : Rect
    if (frameW / frameH > aspect)   // frame wider → crop width
        w = round(frameH * aspect); x = (frameW - w) / 2; return Rect(x, 0, w, frameH)
    else                            // frame taller → crop height
        h = round(frameW / aspect); y = (frameH - h) / 2; return Rect(0, y, frameW, h)

fitSize(containerW, containerH, aspect) : Size   // preview only
    if (containerW / containerH > aspect) Size(containerH * aspect, containerH)
    else                                  Size(containerW, containerW / aspect)
```

`R` is computed **once** from the real capture size (`imageCapture.resolutionInfo`, after
rotation) and reused for the preview box and the save crop, so preview and file cannot
drift.

Example, landscape tablet, 4032x3024 capture (4:3):

| Mode | File |
|---|---|
| a | 4032 x 3024 |
| b | 4032 x 2520 (aspect 1.60 = the measured screen aspect) |
| c 16:9 | 4032 x 2268 |
| c 4:3 | 4032 x 3024 |

---

## 3. Camera layer (`CaptureScreen.kt`)

### Input size selector (real sensor sizes, native aspect only)

1. Read `CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP.getOutputSizes(JPEG)`.
2. Pick the **largest by pixel count** → its aspect becomes `P` (the sensor's native aspect).
3. Keep **only sizes with that same aspect** (tolerance ≈ 2 %, enough to absorb “nearest mod16” variants). Everything else (e.g. 16:9 crops) is not shown: the sensor's shape is the shape you shoot.
4. Sort descending by pixels. The list is the selector:
   `Máxima calidad (default) · 4032x3024 ≈ 5.6 MB · 3264x2448 ≈ 3.8 MB · … · 640x480 ≈ 0.1 MB`
5. **Estimated size** (heuristic, always shown with `≈`):
   - Android exposes no "JPEG size for this resolution" API, and real JPEG size depends on scene noise/lighting, so any number here is an approximation (±20–30 %).
   - `estimatedBytes = pixels(R) * bytesPerPixel`, where `R` is the **output region** (so the number reflects the current mode, not the raw sensor frame). `bytesPerPixel = ~0.35 B/px` at quality 100.
6. Default = first entry(highest). Persist the choice (string `"WxH"`, same DataStore key as today).
   On load, if the stored value is not in the current list, fall back to the first entry.
7. After binding, read `imageCapture.resolutionInfo?.resolution`, log it, and show it in Configuración if it differs from the request (CameraX may compromise).
8. `getHighResolutionOutputSizes()` is not bindable by CameraX and is therefore not listed.

### Use cases

1. `ImageCapture.Builder()`
   - `.setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)`
   - `.setJpegQuality(100)`
   - `ResolutionSelector`: aspect strategy derived from `P` (4:3 or 16:9;
     `FALLBACK_AUTO_STRATEGY` if `P` is neither) + `ResolutionStrategy(selectedSize, FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)`.
2. `Preview.Builder()`
   - **Separate** selector: same aspect strategy as `P`, no fixed size (CameraX picks a
     screen-friendly preview stream). Never reuse the still selector.
   - `PreviewView.ScaleType.FILL_CENTER`.
3. **No `ViewPort`.** All cropping is decided by `R` in the app; the camera must not crop.
4. `LaunchedEffect(selectedSize)` re-binds; `targetRotation` as today.

---

## 4. Preview layer

```kotlin
Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
    // 1. Output region box: aspect == R (computed from resolutionInfo)
    Box(
        Modifier
            .size(/* fitSize(screen, R.aspect) in dp */)
            .clipToBounds()
    ) {
        // 2. Camera, cropped to R by FILL_CENTER (stream aspect == P, box == R)
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        // 3. Decoration stretched to R (same as the save path)
        Image(deco, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
    }
}
```

- `R.aspect == S` → the box is the whole screen (mode b).
- `R.aspect != S` → the box is fitted and centred → black bars (modes a/c).
- The countdown overlay stays exactly as it is today (not touched).
- The post-capture review keeps showing the saved bitmap with `ContentScale.Fit`.

---

## 5. Save path (`loadProcessAndCropBitmap` → rename to `loadProcessAndDecorateBitmap`)

```
decode temp JPEG at full size
  → EXIF orientation (rotate/flip)
  → front-camera mirror
  → crop to R at native pixels            ← same R as the preview
  → stretch decoration over R
  → JPEG, quality 100
```

- Delete the “crop to `previewView.width/height`” block and the `previewWidth/Height`
  plumbing; the crop now comes from `R`, which is the aspect the user actually saw.
- Bars are never part of the file.
- Avoid extra full-size copies: allocate one ARGB_8888 canvas of `R` size and blit the
  source region with `Canvas.drawBitmap(src, srcRect, dstRect, null)` (1:1), then draw the
  decoration on it. Today up to three full-size bitmaps are alive at once.
- Decoration is scaled **up** to `R` if the asset is smaller — author decorations at least at
  the expected `R` size (e.g. ≥ 4000 px on the long side).

Logs to keep/extend:

```
ImageCapture resolution: WxH          ← real sensor size
Output region R: WxH (aspect)         ← what the file will be
Saved: WxH, N bytes                   ← must equal R + refresh bytesPerPixel
```

---

## 6. Decoration

- Fit is a **user setting** — `DecorationFit { STRETCH, COVER }` — applied identically in the
  preview and in the save path, and always against `R` (never the screen):
  - **STRETCH** (default, today's behaviour): `ContentScale.FillBounds` /
    `createScaledBitmap(decoration, R.w, R.h, true)`. The asset is deformed to `R`'s aspect.
  - **COVER**: keep the asset's proportions, scale up until it covers `R`, centre and clip
    the overflow (`ContentScale.Crop` in the preview, `srcRect`-based cover + clip in the
    save path). Circles stay circles; the decoration loses its outer margin.
  - Both paths must use the *same* rule and the same clip rect, otherwise preview and file
    diverge.
- Because assets are custom per device, Configuración shows the current output geometry so
  the asset can be authored against it:
  `Pantalla: 1920x1200 (1.60) · Entrada: 4032x3024 (4:3) · Salida: 4032x2520 (1.60) · ≈ 3.9 MB`
- The existing “⚠️ La relación de aspecto … se verá deformada” warnings under Portada /
  Cuadro de captura are replaced by that informational block **and** the STRETCH/COVER
  toggle (with a one-line hint: “STRETCH deforma si la proporción no coincide; COVER recorta
  los bordes”).

---

## 7. Config / data model

```kotlin
enum class PreviewFit { FIT, FILL, FIXED_ASPECT }   // a, b, c
enum class FixedAspect { R4_3, R16_9 }              // mode c only
enum class DecorationFit { STRETCH, COVER }         // how the PNG adapts to R

data class AppConfig(
    val portadaPath: String? = null,
    val captureBoxPath: String? = null,
    val countdownSeconds: Int = 3,
    val destinationPath: String = "/storage/emulated/0/Pictures/selfie",
    val inputResolution: String? = null,   // "4032x3024", null/absent = highest available
    val previewFit: PreviewFit = PreviewFit.FIT,
    val fixedAspect: FixedAspect = FixedAspect.R16_9,
    val decorationFit: DecorationFit = DecorationFit.STRETCH
)
```

- DataStore: keep the `picture_resolution` **key name** for `inputResolution` (existing
  installs keep their size when it is still in the list); add `preview_fit`,
  `fixed_aspect`, `decoration_fit`, `last_saved_bytes_per_pixel` (double) and
  `saved_sample_count` (int) for the `≈` estimates. Defaults applied when absent.
- `CameraResolutionHelper`:
  - `getNativeSizes(context): NativeSizes` — `{ aspect: Float, sizes: List<CameraResolution> }`
    implementing the max-size-aspect filter above (replaces the 9:16/3:4 pair),
  - `getMaxResolution()` = `sizes.first()`,
  - `parseResolution()` stays for the stored key,
  - `estimateFileSize(size, bytesPerPixel)`.
- UI:
  - “Calidad de las fotos” → “Tamaño de la imagen”: dropdown with the real sizes + `≈ MB`.
  - New “Vista previa” section: three modes; 4:3 / 16:9 sub-choice enabled only for mode c.
  - New “Decoración” sub-option under the capture-box item: STRETCH / COVER.
  - Diagnostics line as in section 6.

---

## 8. Migration / cleanup

- Stored size validated against the device list at load; if missing → highest.
- `ConfigurationScreen.hasChanges` updated for the new fields.
- Remove the old 4:3/9:16 grouping UI and the old crop block; delete unused
  `previewWidth/Height` args.
- Tests: `getNativeSizes` (native aspect detection, filtering, ordering), `estimateFileSize`,
  `coverCrop` (4:3→16:9, 4:3→screen, equal, odd sizes/rounding), `fitSize`, and the
  mode→`R` mapping.
- README TODO “Check quality of images taken” updated once the companion fixes land.

---

## 9. Companion quality fixes (agreed)

- `CAPTURE_MODE_MAXIMIZE_QUALITY`, `setJpegQuality(100)`, final encode at 100.
- Preview gets its own selector (never the still size).
- Remove the `ViewPort` wiring (it is normally `null` at bind time and crops the capture).
- Verify/log the real `resolutionInfo` instead of assuming the requested size.

---

## 10. Acceptance criteria

1. Configuración lists only the sensor's native-aspect sizes, real values, with `≈ MB`,
   “Máxima calidad” default, down to the smallest available.
2. Mode a → file aspect == `P`; mode b → file aspect == the **measured** screen aspect;
   mode c → file aspect == chosen 4:3/16:9. In all three, file pixels == the `R` crop of the
   sensor output (verified in logcat and with `identify`/`exiftool`).
3. Preview aspect == file aspect in every mode; black bars only in a/c, never in the file.
4. Decoration covers `R` exactly, with the selected STRETCH/COVER rule used by both the
   preview and the save path.
5. No upscaling of the photo anywhere.
6. The `≈ MB` figure is computed from the output region `R` (so it changes with the mode)
   and self-calibrates after each saved photo.
7. Old installs open Configuración without errors; stale sizes fall back to the highest.
8. One max-size shot on the target tablet completes without OOM.

---

## 11. Resolved decisions

- **Modes are not display-only**: the file aspect equals the previewed region's aspect.
  Mode a → sensor native aspect; mode b → the measured screen aspect; mode c → 4:3 or 16:9.
- **Only mode c can produce a ratio different from the sensor's** (16:9 or 4:3) — it is a
  crop, the input selector never changes aspect.
- **Input selector is real and single-aspect**: the sensor's native aspect only, highest
  default, decreasing real sizes down to the minimum, `≈ MB` per entry.
- **Estimated size is an approximation heuristic**.
- **Decoration fit is a toggle** (STRETCH default, COVER), in preview and file alike.
- Countdown UI and mirroring are out of scope; companion quality fixes are in scope.
