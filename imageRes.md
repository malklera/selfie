# Diagnosis: why the saved photo looks softer than its resolution suggests

*(Analysis report — no code changes were made.)*

---

## 1. What the code actually does today

| Step | Code | Result |
|---|---|---|
| Resolution chosen in settings | `CameraResolutionHelper.getSupportedResolutions()` — `SCALER_STREAM_CONFIGURATION_MAP.getOutputSizes(JPEG)` | Only the "regular" JPEG sizes are listed |
| Selector | `CaptureScreen.kt:139-162` `ResolutionStrategy(size, FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)` + 4:3 or 16:9 strategy | A *hint*, not a guarantee |
| Same selector reused for `Preview` | `CaptureScreen.kt:186-208` | Preview is also asked for the huge still size |
| Capture | `CAPTURE_MODE_MINIMIZE_LATENCY`, JPEG | Camera-side quality trade-off |
| Decode | `CaptureScreen.kt:402` `BitmapFactory.decodeFile(...)` | Full size, **no resampling** |
| Rotate/flip | `:412-435` `Bitmap.createBitmap(..., matrix, true)` | Filtered 1:1 pixels |
| Crop | `:437-458` center-crop to `previewView.width/height` | **Aspect = screen (1920x1200 = 1.6:1)**, not the chosen ratio |
| Decoration | `:461-470` `createScaledBitmap(overlay, finalW, finalH, true)` | Overlay stretched to the photo aspect |
| Save | `:568 / :580` `compress(JPEG, 95)` | Second lossy encode |

Two things are worth stating clearly up front:

- **Nothing in the app upscales or resamples the camera pixels.** The final file is written at exactly the size of the processed bitmap, so "the file is the resolution of the taken image" is implemented correctly.
- **Therefore the softness is already in the JPEG that CameraX produced (or in your chosen size), not added by the crop/decoration step.** The decorations look crisp because they are drawn *after* the camera pixels at 1:1 into the final bitmap; they are not a proof that the photo is good.

## 2. Ranked probable causes

### #1 `CAPTURE_MODE_MINIMIZE_LATENCY` (highest suspicion) — `CaptureScreen.kt:158`
This is the default mode and the docs are explicit: it **"prioritize[s] latency over image quality … the image quality may be reduced"**. Concretely, in CameraX:
- its default `jpegQuality` is **95**, while `MAXIMIZE_QUALITY` uses **100** (and you then re-encode again at 95);
- it **skips the 3A-converged check** (`mEnableCheck3AConverged = false`) — i.e. the AF/AE trigger before capture is skipped, so the frame can be taken before focus/exposure have settled.

For a photo-booth app with a 3–6 s countdown, latency is basically free. Taking a slightly slower, properly converged and less-compressed shot is exactly what `CAPTURE_MODE_MAXIMIZE_QUALITY` exists for. This is the most likely reason a 5–8 MP file looks like a 1–2 MP upscale, especially in party lighting.

### #2 The selected size is never verified, and the real max size may not even be offered
- `ImageCapture`'s `ResolutionSelector` is only a preference; CameraX "compromises on resolution … based on device capability" and the final size must be read from `imageCapture.resolutionInfo`. The app never reads it — it only logs whatever came out of the decode (`Raw Size` at `:410`).
- The dropdown is built from `getOutputSizes(ImageFormat.JPEG)` only. On many devices the true full-resolution still sizes are in **`getHighResolutionOutputSizes(JPEG)`**, which CameraX still cannot select (known limitation). So the list you pick from can be well below what the sensor can do (very common on tablets whose front camera reports 1080p-class regular output).
- The offered list is also filtered to only 4:3 and 9:16 (`CameraResolutionHelper.kt:78-83`), so native 16:10 sizes (this tablet is 1920x1200) never appear.

Net effect: you can select X, the HAL/session hands back Y < X, and the app silently saves at Y (after crop). Good way to produce "correct-looking dimensions, wrong-looking detail".

### #3 The crop is to the *screen* ratio, which throws pixels away
The saved size is the captured size center-cropped to `previewView` (i.e. 1920x1200 = 1.6:1). On this tablet:

| Selected | Captured | Saved after crop |
|---|---|---|
| 3264x2448 (4:3, 8 MP) | 3264x2448 | **3264x2040 (6.7 MP)** |
| 3264x1836 (16:9, 6 MP) | 3264x1836 | **2938x1836 (5.4 MP)** |
| 1920x1080 | 1920x1080 | **1728x1080 (1.9 MP)** |

No blur is introduced, but the pixel count is lower than the selection, and the field of view is narrower. On a phone with a 20:9 screen the loss would be ~45% of the width, so this scales badly if the app is ever used elsewhere. It is also a *triple* crop when the `ViewPort` is active (ViewPort crop → CameraX encode crop → your manual crop), which the docs warn about when ViewPort and use-case aspect ratios differ.

### #4 `previewView.viewPort` is almost certainly `null` at bind time
`LaunchedEffect(selectedResolution)` (`:179`) runs before the `AndroidView` holding `previewView` has been measured, so `previewView.viewPort` (`:217`) is normally null and the ViewPort is **never applied** (the effect is keyed only on `selectedResolution`, so there is no rebind later). Consequences:
- The "WYSIWYG" guarantee CameraX would give you is not in effect; the saved frame is only an approximation made by your manual crop.
- The `Preview` is given the **still-image resolution** instead of a screen-sized one, which is wasteful and on constrained devices can push the session into a compromise configuration — feeding cause #2.
- Note the same ordering issue is visible in the fallback at `:243-244` (`if (previewView.width > 0) … else displayMetrics`): that fallback is what is normally used, so the crop ratio is the *full screen* ratio, including any edge-to-edge area — fine here because the preview is genuinely full screen.

### #5 Double JPEG encode + EXIF dropped
Camera writes JPEG (95 in this mode), the app decodes and re-encodes at 95. Overlay compositing makes one re-encode unavoidable, but 95 → 100 is free quality, and the source EXIF (including any camera tuning/metadata) is discarded. This is a minor but real generation-loss contributor.

### #6 Memory pressure (not your symptom, but a real hazard)
`decodeFile` + `createBitmap(matrix)` + merged bitmap = up to **three full-size ARGB_8888 copies**. A 12 MP frame is ~48 MB each (~150 MB peak). On a low-RAM tablet with a 20 MP+ camera this ends in `OutOfMemoryError`, caught at `:474` and reported as "Paso 2: Falló el procesamiento" — i.e. no photo rather than a soft photo. Worth knowing given the crash-free "soft photo" report.

## 3. What I would rule out
- The decoration compositing does **not** affect the photo's scale (it draws at 0,0 at the photo's size).
- The preview scaling (`FILL_CENTER`) never touches the file.
- The crop math matches `FILL_CENTER` correctly (crops the long dimension, centered), so the *framing* logic is right; only the FOV/pixel cost is the issue.
- JPEG 95 is not enough to make a sharp photo look low-res.

## 4. How to confirm in ~5 minutes (no code change)
1. `adb logcat -s CaptureScreen` while taking one photo. You will get:
   - `EXIF Orientation: …, Raw Size: WxH` ← **the real captured size** from CameraX,
   - `Bitmap cropped from WxH to WxH` ← what is saved.
   Compare `Raw Size` with the resolution selected in Configuración. If it's smaller, cause #2 is confirmed and #1/#4 amplify it.
2. Pull the saved file and check it on the PC (`identify file.jpg` / `exiftool`), and compare a 100% crop of the photo against the same area of an image taken with the stock camera app in the same light. If the stock app is also soft, it's the sensor; if it's sharper, it's `MINIMIZE_LATENCY` / the session configuration.
3. Take one shot in good light, one in dim light. If the gap is huge, 3A/noise-reduction (cause #1) is dominant.

## 5. Fixes I would apply (described, not applied)
1. `setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)` and `setJpegQuality(100)`; final save at 100. Keep the countdown as the latency budget.
2. Don't reuse the capture `ResolutionSelector` for `Preview` — give the preview its own aspect-only selector; keep the still size for `ImageCapture` only.
3. After `bindToLifecycle`, read `imageCapture.resolutionInfo` and log/expose the **actual** size; if it differs from the selection, show it or fall back visibly instead of silently.
4. Include `getHighResolutionOutputSizes(JPEG)` in the offered list (with the caveat that CameraX may not honor them) so the dropdown reflects what the device can really do.
5. Decide explicitly what "resolution of the taken image" means: either save the full sensor frame (no crop, `ViewPort` removed) or crop to the *selected* aspect ratio instead of the screen ratio. Today it is the screen ratio, which is what shrinks 4:3 12 MP to ~10 MP.
6. Set the `ViewPort` only after the `PreviewView` is laid out (or build it yourself from `displayMetrics` + rotation); otherwise delete the dead `viewPort` branch and accept manual cropping.
7. Avoid the extra decode/re-encode round trip: capture `inMemory`, or build the overlay composite once and encode once; use `inSampleSize` only for the on-screen thumbnail.

**Bottom line:** the save path is dimensionally correct and introduces no resampling — the photo's softness comes from the capture configuration. The two concrete culprits to check first are `CAPTURE_MODE_MINIMIZE_LATENCY` (`CaptureScreen.kt:158`) and an actual captured size lower than the one selected (`CaptureScreen.kt:139-162` + `CameraResolutionHelper.kt:66-83`), with the screen-ratio crop reducing the effective pixel count on top.

---

## 6. Notes from discussion

*(to be filled while we discuss)*
