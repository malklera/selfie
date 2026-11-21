# Selfie Mirror — Performance, Resource & Security Review

**Date:** 2026-11-20
**Scope:** whole app (`app/src/main`), with priority on the unattended loop:

> **cover → countdown → capture → save → back to cover**

Everything inside `config` (printing, templates, queue, printer status) is treated as secondary
but still reviewed where it can freeze, crash, or exhaust memory on the tablet while the
capture loop is running. **Image quality is treated as a hard requirement, not a trade-off
against speed.**

This document is analysis only. No application code was changed.

---

## 0. TL;DR — the ten things worth doing first

| # | Finding | Area | Severity |
|---|---------|------|----------|
| 1 | Camera can bind **before** the CAMERA permission is granted; on a fresh install the loop stays broken until the screen is recreated | capture loop | **Critical** |
| 2 | Result screen never returns to the cover by itself (needs a tap) | capture loop | **High** |
| 3 | Default destination `/storage/emulated/0/Pictures/selfie` is unwritable on Android 10+; photo is shown then fails to save | capture loop | **Critical** |
| 4 | "Print" can enqueue an **empty URI** because the result screen appears before the file write finishes | capture loop | **High** |
| 5 | PDF rendering/decoding runs on the **main thread** when triggered from the queue screen → ANR | printing | **High** |
| 6 | `targetRotation` is never updated after boot → rotated photos when a tablet is turned | capture loop | **High** |
| 7 | Capture-box overlay is decoded at full resolution and kept in RAM for the whole session (~48 MB for 12 MP) | memory | **High** |
| 8 | Full-size JPEG is decoded again for the result screen after navigating back from config | memory | **High** |
| 9 | Config screen protected only by 5 taps on a 20 %-alpha icon; no PIN, no rate limiting | security | **High** |
| 10 | `allowBackup=true` and release builds silently fall back to the **debug signing key** | security | **High** |

---

## 1. Current priority-path map

| Step | Where | Notes |
|------|-------|-------|
| App start | `MainActivity.kt:58-119` | permissions requested here, `setContent` immediately after |
| Config load | `ConfigDataStore.appConfigFlow` at `MainActivity.kt:134` | `null` → splash stays |
| Cover | `CaptureScreen.kt:371-407` | `SubcomposeAsyncImage`, `FillBounds`, tap starts countdown |
| Camera bind | `CaptureScreen.kt:210-250` | binds preview + `ImageCapture` at composition time (behind the cover) |
| Countdown | `CaptureScreen.kt:297-305` | `delay(1000)` loop, then `takePhoto` |
| Capture | `CaptureScreen.kt:838-914` | in-memory JPEG (`OnImageCapturedCallback`) |
| Transform | `CaptureScreen.kt:736-836` | EXIF rotate + selfie mirror + **1:1 crop** + decoration overlay |
| Show result | `CaptureScreen.kt:316-321` | main-thread post, **before** the file exists |
| Save | `CaptureScreen.kt:881-893`, `930-967` | IO coroutine, JPEG quality 100 |
| Back to cover | `CaptureScreen.kt:610-616` | only on user tap ("OTRA FOTO") |

**What is already good (keep it):**

- Capture is done fully in memory; no double camera-triggered file write.
- The crop/rotate path is **pixel-exact** (translate + rotate + mirror, no resampling) and only
  allocates the final `outW × outH` bitmap — this is the right shape for image quality.
- Camera is pre-bound while the cover is displayed, so the shutter is warm.
- `CAPTURE_MODE_MAXIMIZE_QUALITY` + JPEG 100 is coherent with the stated quality priority.
- The app has **no `INTERNET` permission**, no analytics, no ads, no network client. This is a
  strong privacy posture and should be kept.
- Room/DataStore are used for local state only; photos go to a user-chosen folder.

---

## 2. P0 — things that break (or stall) the loop

### 2.1 Camera binds before the permission is granted — first launch is broken

`MainActivity.kt:95-107` launches the permission dialog and immediately calls `setContent`.
`CaptureScreen.kt:210-250` then binds the camera in a `LaunchedEffect` with **no permission
check**. On a first install `bindToLifecycle` throws (`SecurityException`, swallowed by
`catch (exc: Exception)` at line 245). The effect is keyed on `selectedResolution` and
`lifecycleOwner`, so it never re-runs when the user taps **Allow**. The only recovery is
leaving and re-entering the screen (e.g. tapping into config and back).

The retry path does not help either: `onError` increments `captureAttempt`
(`CaptureScreen.kt:327-334`), which re-runs the countdown but never rebinds the camera.

**Fix:** gate binding on the permission, and re-evaluate on resume.

```kotlin
var cameraGranted by remember {
    mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    )
}
val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
    cameraGranted = it
}
LaunchedEffect(Unit) { if (!cameraGranted) launcher.launch(Manifest.permission.CAMERA) }

LaunchedEffect(cameraGranted, selectedResolution, lifecycleOwner) {
    if (!cameraGranted) return@LaunchedEffect
    // ...existing bind code...
}
```

Also show a kiosk-friendly message when permission is denied permanently, and provide a
"open app settings" action. Remove the blind request from `onCreate` or turn it into a
single source of truth for permission state.

### 2.2 The result screen never goes back to the cover on its own

The stated loop ends with "go back to cover", but the only exit from `CAPTURED` is the
"OTRA FOTO" button (`CaptureScreen.kt:610-616`). For an unattended booth this means the
tablet sits on a customer's photo until the next person taps. It also keeps the full-size
`capturedBitmap` alive indefinitely.

**Fix:** add a result timeout (e.g. 5–10 s, configurable in the secondary config screen) and
reset to `WELCOME`:

```kotlin
LaunchedEffect(kioskState) {
    if (kioskState == KioskState.CAPTURED) {
        delay(config.resultScreenSeconds.seconds)   // cancel-on-tap semantics
        capturedBitmap = null
        capturedUriString = null
        kioskState = KioskState.WELCOME
    }
}
```

Any explicit tap ("OTRA FOTO", print) should cancel the timer. This is also the cleanest
place to enforce "one customer at a time".

**Related:** returning from the config screen today restores the previous `CAPTURED` state.
`kioskState` and `capturedUriString` are `rememberSaveable` (`CaptureScreen.kt:94-97`), and
Navigation Compose restores them on `popBackStack("main")`, so the tablet comes back showing
the previous customer's photo and re-decodes the full-size JPEG (see 3.1). When the operator
saves or closes config, force `WELCOME` and clear the captured state.

### 2.3 Default destination folder cannot be written on Android 10+

`AppConfig.kt:12` / `ConfigDataStore.kt:73` default to a raw path:

```
/storage/emulated/0/Pictures/selfie
```

On API 29+ scoped storage, direct `File` writes to shared storage are blocked for an app
targeting 37, and `FileOutputStream` throws `EACCES` (`CaptureScreen.kt:954-966`). The
pre-check in `checkDestinationWritable` (`CaptureScreen.kt:969-993`) is not reliable here:
`mkdirs()`/`canWrite()` can report success while the actual write is still refused by
scoped storage. The customer sees the photo (shown before saving, line 316), then gets a
"Paso 3" error and **no file**.

**Fix (in order of preference):**

1. Default new installs to `MediaStore.Images` under `Pictures/selfie`
   (`RELATIVE_PATH = Environment.DIRECTORY_PICTURES + "/selfie"`, `IS_PENDING` flow for
   API 29+). No storage permission, files visible in the gallery, correct date metadata.
2. Keep SAF for operator-chosen folders (already implemented) but make it a **required
   first-run setup** step instead of a default path.
3. Keep raw paths only for `SDK_INT <= 28`.

Also treat "save failed" as a first-class state: never show a result before the bytes are
durable, or show the result with an explicit unsaved banner and disable printing.

### 2.4 Print can be requested before the file exists

The result is displayed at `CaptureScreen.kt:316-321`; the save is launched afterwards
(`:881`). The print button uses `onPrint(capturedUriString ?: "", copies)`
(`:518`) — so a fast tap enqueues an empty URI **and** `incrementPrintCount` is still
called (`MainActivity.kt:192-197`). The queue then holds an unprintable item and the
counter drifts from reality.

**Fix:** gate print on save completion (e.g. `SaveState.Saving → Saved(uri)`), disable the
button while saving, and refuse empty URIs inside `PrintQueueManager.enqueuePhoto`. Also
rename the concept: `printCount` currently counts *requests*, not successful prints.

### 2.5 Queue printing blocks the UI thread (ANR)

`PrintQueueManager.printPages` (`:94-288`) does bitmap decoding and PDF rendering inside
`mutex.withLock` **in the caller's context**. `QueueManagementScreen` calls it from
`rememberCoroutineScope().launch` (`:124-125`, `:139-140`, `:219-220`, `:341-342`,
`:363-364`) — i.e. **Main**. Rendering one A4 page at 300 DPI means decoding up to
4 × 2480 × 3508 ARGB bitmaps plus PDF encoding; this will ANR the app.

**Fix:** wrap the body of `printPages` / `printReadyPages` / `retryFailedPages` in
`withContext(Dispatchers.IO)` (the `Mutex` works fine across dispatchers):

```kotlin
suspend fun printPages(context: Context, pageIds: List<Long>, allowPartial: Boolean = false): Boolean =
    withContext(Dispatchers.IO) {
        if (pageIds.isEmpty()) return@withContext false
        mutex.withLock { /* existing body */ }
    }
```

Note `Context` is application context here (`LocalContext` in a Composable is the Activity,
but this leaks for the duration of the job) — pass `context.applicationContext`.

### 2.6 Rotation is not propagated to the camera

`imageCapture.targetRotation` and `preview.targetRotation` are set **once** at bind time
from `previewView.display?.rotation` (`CaptureScreen.kt:214-228`). The manifest declares
`android:configChanges="orientation|screenSize|..."` (`AndroidManifest.xml:65`), so the
activity is not recreated on rotation, and there is no `onConfigurationChanged`
implementation anywhere. With `AppOrientation.AUTO` (the default), turning the tablet
leaves:

- preview/ JPEG rotation stale,
- `realSensorSize` stale,
- a wrong `outputRegion` crop → off-centre or wrongly-sized final photos.

**Fix:** observe display/configuration changes (e.g. `LocalConfiguration` +
`previewView.display.rotation` in a `DisposableEffect`, or `View.OnLayoutChangeListener`)
and update `preview.targetRotation`, `imageCapture.targetRotation`, then recompute the
output region. For a fixed installation, prefer defaulting `AppOrientation` to the
physical mount and offering AUTO only as an option.

### 2.7 Countdown can freeze forever on a folder error

`LaunchedEffect(kioskState, captureAttempt, isFolderCheckPassed)` (`:297`) requires
`isFolderCheckPassed`. If the destination check fails, the tap switches to `COUNTDOWN`
(`:376-379`), shows the countdown number, and then **nothing happens**: no countdown, no
error inside the UI (only a Toast at `:120`), and the settings button is hidden in
`COUNTDOWN`. A visitor is stuck with a still frame and a "3".

**Fix:** check the folder when the cover is tapped (or keep a reactive `folderState`), and
before entering `COUNTDOWN`. On failure: stay on `WELCOME`, show an inline message, and
log. Also re-validate right before saving (see 2.3) so a revoked SAF grant cannot lose a
photo.

### 2.8 Retries are unbounded

On capture error, `captureAttempt++` re-enters the effect, waits 2 s, and tries again
forever (`:299`, `:327-334`). A camera in a bad state (permission, HAL error, camera in use
by another app) means an infinite loop of failed shutters and Room `error_logs` inserts.

**Fix:** cap attempts (2–3), then return to `WELCOME` with a visible message and a single
log entry.

### 2.9 A DataStore failure leaves the app on the splash forever

`MainActivity.kt:134` collects `appConfigFlow` with `initial = null`, and `null` keeps the
splash visible (`:279-285`). If the DataStore file is corrupt or unreadable, the flow emits
nothing and the kiosk never starts. There is no `corruptionHandler` on
`preferencesDataStore` (`ConfigDataStore.kt:15`).

**Fix:** add a `ReplaceFileCorruptionHandler { emptyPreferences() }`, plus
`.catch { emit(defaultConfig) }` on the config flow so the app always reaches the cover.

### 2.10 Teardown blocks the main thread and can kill an in-flight capture

```kotlin
// CaptureScreen.kt:253-264
DisposableEffect(Unit) {
    onDispose {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()  // blocking
        cameraProvider.unbindAll()
        cameraExecutor.shutdown()
    }
}
```

- `.get()` is a blocking call on Main (usually resolved, but still wrong).
- `unbindAll()` is process-wide; prefer `cameraProvider.unbind(preview, imageCapture)`.
- If the app is backgrounded/back-pressed during the countdown, the coroutine is cancelled,
  but a shutter callback already dispatched to the (now shut down) executor can raise
  `RejectedExecutionException` inside `takePicture`, which is not caught in `takePhoto`.
- A single app-scoped `ExecutorService` owned by the activity/Application is simpler and
  avoids the create/shutdown race entirely.

---

## 3. Capture path — memory and quality

### 3.1 Memory peaks are the main stability risk

Rough per-object cost for a 12 MP sensor (4000 × 3000, `ARGB_8888` = 4 B/px):

| Object | Size |
|---|---|
| JPEG byte array (CameraX buffer copy, `CaptureScreen.kt:743-745`) | ~4–10 MB |
| Decoded source bitmap (`:747`) | ~48 MB |
| Result bitmap = crop (`:804`) | up to ~48 MB |
| `captureBoxBitmap` overlay, decoded full-size (`:143-159`) | up to ~48 MB |
| Displayed result bitmap on `CAPTURED` | same object as above |
| PDF: each slot decoded at 300 DPI (`PrintQueueManager.kt:170-174`) | 34 MB/slot |

On a 2 GB tablet with a 192–512 MB heap this is already tight; the overlay + a second
capture, or returning from config with a restored result, can hit OOM. Recommendations in
priority order:

1. **Bound the overlay decode.** Target = the output region dimensions, never larger.
   Use a power-of-two `inSampleSize` that keeps `decoded >= output` (no quality loss):

   ```kotlin
   fun decodeFor(context: Context, uri: Uri, minW: Int, minH: Int): Bitmap? {
       val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
       context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, b) }
       var sample = 1
       while (b.outWidth / (sample * 2) >= minW && b.outHeight / (sample * 2) >= minH) sample *= 2
       val o = BitmapFactory.Options().apply {
           inSampleSize = sample
           inPreferredConfig = Bitmap.Config.ARGB_8888
       }
       return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
   }
   ```

2. **Do not decode the saved JPEG at full size to show the result.** After a config round
   trip, `CaptureScreen.kt:125-140` re-decodes the whole file just for a preview. Use
   `decodeFor(..., screenWidthPx, screenHeightPx)`. The full-resolution file remains the
   source of truth for printing.
3. **Release the result when it is abandoned.** After "OTRA FOTO"/auto-return, null the
   reference (already done) and let GC collect; do not hold decoded bitmaps in
   `rememberSaveable` state.
4. **`android:largeHeap="true"`** is a legitimate mitigation for a single-purpose kiosk
   device. Treat it as a safety net, not a substitute for the fixes above.
5. **Serialize save and capture.** Today a user can tap "OTRA FOTO" during the background
   write and start the next capture while the previous 48 MB result is still referenced by
   the IO coroutine. A bounded single-thread save queue with a "saved" signal removes the
   overlap.

### 3.2 Recomposition micro-costs

- `Image(bitmap = bitmap.asImageBitmap())` at `:471` and `:360` creates a new
  `ImageBitmap` wrapper on every recomposition. Wrap: `remember(bitmap) { bitmap.asImageBitmap() }`.
- `Log.d("CaptureScreen", "CaptureScreen composed…")` at `:95` fires on every
  recomposition — remove or gate with `BuildConfig.DEBUG`.
- The `isPreviewReady` state at `:194-207` is written but never read. Either use it to hide
  the countdown until frames flow, or delete it.

### 3.3 Preview cost while the cover is displayed

The preview is bound for the app's whole life and keeps streaming behind the opaque cover
(`:209-250`, `:371`). That is CPU/GPU/ISP work for pixels nobody sees. It is a deliberate
latency trade-off, but it can be improved without losing the warm shutter:

- keep `imageCapture` bound and **unbind only the `Preview` use case** on `WELCOME`,
  rebind at the start of `COUNTDOWN`; the 3 s countdown hides the re-attach. Measure the
  rebind cost on the target tablet before shipping this.
- add an explicit **preview resolution strategy** (currently only aspect ratio is set,
  `:217-229`), sized to the on-screen preview box. Without it CameraX may pick a preview
  resolution far above what the tablet can show, burning bandwidth.
- `PreviewView` is left at its default `PERFORMANCE` (SurfaceView) mode. It is the right
  choice, but SurfaceView is not clipped by Compose on all devices; verify the
  `clipToBounds()` at `:348` actually clips the preview on the target tablet, otherwise
  use `COMPATIBLE` (TextureView) — at a GPU cost.

### 3.4 Derive the crop from the real frame

`outputRegion` is computed from `realSensorSize ?: selectedResolution`
(`:273-287`). `resolutionInfo` can be `null` immediately after binding, and the bound
resolution is the result of `FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER`
(`:175-177`) — it may not be the requested size. If the fallback differs in aspect, the
crop will be wrong.

**Fix:** compute the final crop from the **decoded, oriented** bitmap dimensions inside
`loadProcessAndDecorateImageProxy` (or verify at capture time and correct), using the same
`ImageGeometryHelper.calculateOutputRegion` with `screenAspect`. Keep the preview region
calculation for display only.

### 3.5 Quality specifics

- **Keep the 1:1 transform.** The current matrix pipeline (`:757-808`) rotates, mirrors and
  translates without scaling, so no resampling happens between sensor and file. Do not
  "optimize" this into `createScaledBitmap` calls.
- **JPEG 100**: visually lossless but 8–15 MB per 12 MP photo, slower to write and to
  re-decode for print. 95–97 is visually indistinguishable for prints at 300 DPI and
  roughly halves the file. If the operator wants 100, keep it — but expose it rather than
  hard-coding it at `:188`, `:951`, `:963`.
- **`DecorationFit.STRETCH` is the default** (`AppConfig.kt:16`) and distorts frame art at
  `:813-817`. `COVER` preserves geometry; make it the default and label STRETCH as an
  advanced option.
- **Cover image uses `ContentScale.FillBounds`** (`:386`), which stretches the cover art
  to the tablet's aspect ratio. Offer `Crop` (or per-image fit) so the "portada" is not
  distorted; it is the most visible asset in the loop.
- **Low-light quality:** front cameras on tablets are weak. Two cheap, high-impact wins:
  set screen brightness to max during the countdown, and optionally flash a full-screen
  white overlay as a fill light. Optionally use `Camera2Interop` to add +1 EV.
  (Quality priority justifies the extra shutter time that `MAXIMIZE_QUALITY` already has.)
- **EXIF handling is sound, but metadata is lost on save.** The buffer is decoded, then
  `ExifInterface(ByteArrayInputStream(bytes))` is parsed (`:749`) — correct. On save,
  `Bitmap.compress` drops all EXIF: if downstream apps inspect orientation/date, write it
  back with `ExifInterface` after compression.
- **Save is not fsync'd**: `outputStream().use { bitmap.compress(...) }` returns even if
  `compress` returned `false` (`:950-964`). Check the boolean and `fd.sync()` (or write to a
  temp name + rename) so a power cut cannot leave a truncated "successful" photo.
- **File names can collide.** `SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")` (`:931`) has
  one-second resolution. SAF auto-renames, but the raw-`File` branch (`:961-965`) will
  **overwrite** a photo taken in the same second. Add milliseconds or a short random suffix.

---

## 4. Print pipeline (secondary, but can take the whole device down)

1. **Batch size.** `MAX_BATCH_PAGES = 20` (`PrintQueueManager.kt:40`) keeps up to 20 pages
   in one `PdfDocument`. Each page can embed 1–4 images of up to 2480 × 3508. Even if
   `loadedBitmaps` are recycled per page, the PDF document retains the page content until
   `writeTo`/`close`. Reduce to **1–2 pages**, or emit one PDF per page and start a print
   job per page (also improves resilience: one bad photo does not fail 20 pages).
2. **Decode each photo once.** `decodeBitmapFromUri` (`:302-323`) opens the stream twice
   (bounds + pixels) and decodes the same file once **per copy**. Printing 4 copies of a
   single photo decodes 4 × 34 MB. Add a byte-bounded `LruCache<String, Bitmap>` keyed by
   `uri + targetSize`, cleared at the end of the job.
3. **Avoid the rotate allocation.** `ImageFit.drawFittedBitmap` (`ImageFit.kt:76-114`) calls
   `rotateBitmap` (a full copy) when `autoRotate` kicks in. Rotate in the canvas matrix
   instead (`canvas.save(); canvas.rotate(90f, …); draw; canvas.restore()`).
4. **`autoRotate` produces sideways prints.** For the two-up template (`DefaultTemplates.kt:22-33`)
   slots are landscape while selfies are portrait; `autoRotate = true` rotates the customer
   90°, so faces come out sideways. With quality as priority, **default `autoRotate = false`**
   (contain/letterbox) or make it a per-template option. Verify with a real print.
5. **Fragile DPI selection.** `template.quality.contains("300")` (`:232`) decides the
   `PrintAttributes` resolution. Add an explicit `dpi: Int` field to `PrintTemplate` and
   snapshot it; stop parsing a human-readable label.
6. **Print media is always ISO A4.** `MediaSize.ISO_A4` (`:226-230`) and only the
   orientation follow the template's `widthCm`/`heightCm`. All current templates are A4,
   so this is latent, not broken. Likewise `setColorMode(COLOR_MODE_COLOR)` is hard-coded
   (`:235`) while `template.colorMode` is part of the grouping key — the snapshot's color
   mode is effectively ignored.
7. **Stuck `SENT` pages.** `updatePageSentJob(renderedPageIds, null)` is called **before**
   `printManager.print` (`:244-247`). If `print()` throws or returns `null`, pages stay
   `SENT` forever: startup recovery only resets `PRINTING`
   (`PrintQueueRepository.kt:254-267`). Move the `SENT` write after a non-null job, and
   reconcile `SENT` on startup by matching `PrintManager.printJobs`.
8. **Polling.** One coroutine per print job polls every 1 s for up to 5 min (`:263-281`).
   Use backoff (1→2→4→…→30 s), stop when the app goes to background, and use
   `SupervisorJob` on the manager scope so one failed DB write does not cancel every job.
9. **Parse the template snapshot once per page.** `getTemplateSnapshot()` deserializes JSON
   each time it is called (`PrintPage.kt:27-33`) — it is called in grouping, in the render
   loop and again for `firstTemplate`. Cache the parsed template per page id for the job.
10. **Enqueue is not transactional.** `PrintQueueRepository.enqueuePhotos`
    (`:41-100`) performs `getLastItem`, `getLatestIncompletePage`, `insertItem`,
    `updatePage` in a loop without a transaction. Wrap in `db.withTransaction { }` to avoid
    duplicate page numbers / lost slots if two enqueues ever overlap (e.g. double tap on
    print).
11. **Room indices.** `PrintItemEntity` has no index on `pageId`, and `PrintPageEntity`
    has none on `status` / `(templateId, templateVersion, status)`. Every queue flow scan
    is a full-table scan plus per-page item queries. Add `@Index` and a migration (bump
    `AppDatabase` version).
12. **Five COUNT flows** for `QueueCounts` (`PrintQueueRepository.kt:102-115`) re-run on
    every page change. Combine into one `SELECT status, COUNT(*) … GROUP BY status` query.
13. **Full-bleed vs printer margins.** Templates assume zero margins; drivers usually
    reserve 3–5 mm, so photos are scaled/clipped at the edges. Add a configurable safe
    area (or document a borderless-capable printer requirement) for print quality.
14. **Printing is never automatic.** The comment at `PrintQueueManager.kt:59-60` is
    explicit: enqueue does not print. A customer's "IMPRIMIR" only fills a queue that an
    operator must then open and print. If the kiosk is meant to be truly unattended, this
    is an architectural gap: Android's framework printing goes through the system print UI.
    Options: vendor/network print SDK (Esc/POS, Star, CUPS/IPP), or a dedicated
    `PrintService`, or accept operator-driven printing and make that explicit in the UI.

---

## 5. Resource utilization & lifecycle

| Resource | Observation | Recommendation |
|---|---|---|
| Camera | Preview streams while the cover is opaque | Unbind `Preview` on WELCOME (see 3.3), keep `ImageCapture` |
| Screen | `FLAG_KEEP_SCREEN_ON` (`MainActivity.kt:68`) stays on forever | Fine for a plugged-in kiosk; note OLED burn-in, dim after idle if the panel is OLED |
| System bars | Hidden once in `onCreate` (`:74-76`); dialogs/transient swipes can bring them back | Re-hide on `onWindowFocusChanged`/`ON_RESUME` so a customer cannot reach Settings |
| Back | No `BackHandler` on the cover → back exits to the launcher | Swallow back on `main`; require the PIN path to exit |
| Error log | `ErrorLogger.log` inserts a row per event with no retention (`ErrorLogger.kt:19-31`) | Keep last N (e.g. 1 000) rows; prune on insert or at startup |
| Error logger scope | `CoroutineScope(Dispatchers.IO)` without `SupervisorJob` | A single DB failure cancels logging for the rest of the process |
| Print manager | Same: `CoroutineScope(Dispatchers.IO)` (`PrintQueueManager.kt:34`) | Add `SupervisorJob()`; move to an application-scoped service |
| Print polling | 1 Hz × 5 min per job | Backoff + cancel when not resumed |
| URI grants | `takePersistableUriPermission` on every cover/box/folder pick (`ConfigurationScreen.kt:182-225`) with no release | Release the previous grant when replacing; the 128-grant quota is finite and silent failures follow |
| Coil | Default caches; no explicit sizing/limit | Set an explicit `ImageLoader` with a memory cache sized to the device and `diskCache(null)` (or small) — only 2–3 local images are ever shown |
| `getNativeSizes` | Runs on Main during composition (`MainActivity.kt:147`, `ConfigurationScreen.kt:107`) | Move to `Dispatchers.IO` behind a cached repository; it calls the camera service binder |
| Recomposition | `SelfieApp` always collects `activeTemplateFlow` (`MainActivity.kt:145`) even on the cover | Collect only inside config screens |
| Camera executor | Created and shut down with the screen | Use one app-scoped single-thread executor |
| Build | `org.gradle.jvmargs=-Xmx2048m` with AGP 9 + KSP | `-Xmx4g`, enable `org.gradle.parallel=true` |

### Kiosk hardening (relevant to both "resource" and "security")

- Call `startLockTask()` behind a PIN/device-owner check, and add
  `android:lockTaskMode="if_whitelisted"` to the activity; otherwise Home/Recents/back can
  leave the app.
- Register a crash handler that logs to `ErrorLogger` and restarts the activity after a
  short delay; a kiosk should recover without an operator.
- Add an optional `BOOT_COMPLETED` receiver if the tablet must come back after a power cut.
- Keep `FLAG_KEEP_SCREEN_ON` only while resumed (it is per-window, so this is automatic).
- The window currently cannot be rotated before config loads; consider a fixed default
  orientation matching the mount.

---

## 6. Security & privacy

### Strengths to preserve

- **No network access at all** (`AndroidManifest.xml` has no `INTERNET`). No analytics, no
  remote logging, no crash reporting to a third party. This makes the photo data
  device-local and is the single best property of this app.
- Photos are written through SAF / user-chosen storage, not to app-private-and-hidden places
  the operator cannot find.
- `local.properties` is git-ignored and contains only `sdk.dir`.

### Findings

1. **Config access is effectively public.** `SecretSettingsButton` (`SecretSettingsButton.kt:36-46`)
   opens the configuration after 5 taps, from either the cover or the result screen, with a
   20 %-alpha icon. The config screen can:
   - change the destination folder (where all customer photos go),
   - change resolution/aspect/orientation,
   - reset the print counter,
   - **clear the entire print queue**, and
   - open the error history (which contains filesystem paths and screen/action names).
   The README already lists a PIN as a TODO. Given the kiosk context, treat this as a
   security item, not a convenience item: 4–6 digit PIN, salted hash
   (PBKDF2/`MessageDigest` with a per-install salt in DataStore), attempt counter with a
   lockout (e.g. 5 tries / 60 s), and a documented reset path (long-press the version label
   for 10 s, or device-owner reset).
2. **`allowBackup="true"`** (`AndroidManifest.xml:53`) with empty
   `backup_rules.xml`/`data_extraction_rules.xml` lets the system back up DataStore
   (folder path, print counters, config) and the Room DB (print queue, photo URIs, error
   logs with paths) to the user's cloud backup — **the app's lack of `INTERNET` does not
   prevent this**, the backup agent does it. For a kiosk, set `allowBackup="false"` (and
   `dataExtractionRules`/`fullBackupContent` accordingly), or exclude both stores.
3. **Release signing silently degrades to debug.** `app/build.gradle.kts:66-71`: if the
   keystore is missing, the release variant is signed with the **debug** key. Also the
   default keystore path is an absolute developer path (`:47`). Distribute such an APK and
   any future update from a real key becomes impossible. Make `assembleRelease` fail when
   `RELEASE_STORE_*` is absent, and read only from environment/CI (no fallback path).
4. **Unnecessary storage permissions.** `READ_EXTERNAL_STORAGE` (`maxSdkVersion=32`) is not
   needed: the app uses SAF (`OpenDocument`/`OpenDocumentTree`) and persisted grants.
   `WRITE_EXTERNAL_STORAGE` is only needed for the legacy raw-path destination. Removing
   them shrinks the permission surface and Play review questions.
5. **Information disclosure in the UI.** Toasts/logs print real filesystem paths
   (`"Sin permiso de escritura en $path"`, `CaptureScreen.kt:982-987`) on a screen the
   public can see. Keep path detail in the log; show a generic message on the kiosk UI.
   `Log.d` calls also include folder paths in release builds — gate them with
   `BuildConfig.DEBUG`.
6. **`file://` intents.** `openFolder` builds `file://` `ACTION_VIEW` intents
   (`ConfigurationScreen.kt:1270-1282`). On API 24+ these throw `FileUriExposedException`;
   they are caught and the loop moves on, but the behavior is undefined and the URI
   handling is dead weight. Remove them, or expose a `FileProvider` path if a file manager
   really must be opened.
7. **Reflection on hidden APIs.** `PrinterInfoHelper.getPrintServices` and
   `Settings.Secure.getString("enabled_print_services")` (`:288-345`) use hidden APIs /
   restricted settings. They are wrapped in `try/catch`, so the failure mode is "no
   printer list", not a crash — but this can break with any OS update and should not be
   on the critical path. Keep it inside the printer config screen only (it is).
8. **Debuggable/dev options.** For deployment: verify the shipping APK is not debuggable
   (`assembleRelease` is), disable developer options and USB debugging on the tablet, and
   lock the launcher via device-owner provisioning if the venue is untrusted.
9. **No encryption at rest for the queue.** For a photo booth, customer photos in a shared
   `Pictures` folder are readable by any app with storage access. That may be acceptable
   (customers want to share them), but it should be a conscious decision. If not, write to
   app-private storage and export on demand.
10. **Error history integrity.** `error_logs` stores `lastAction` (Spanish UI strings) and
    `screen` routes; no PII beyond paths. Consider a "clear logs" action and a retention
    limit (also in §5).

---

## 7. Quality-first checklist (given "quality over speed")

| Item | Status | Action |
|---|---|---|
| Sensor → file is 1:1 crop, no resampling | ✅ good | Keep; add a regression test |
| Capture mode maximize quality, flash off | ✅ good | Add optional fill-light / +EV for dim venues |
| Chosen resolution honored | ⚠️ | Log the actually-bound `resolutionInfo`; handle fallback aspect |
| Cover art not distorted | ❌ | `FillBounds` → offer `Crop`; default to `Crop` |
| Decoration not distorted | ❌ | Default `COVER` instead of `STRETCH` |
| Photos not sideways in multi-slot prints | ❌ | `autoRotate = false` by default |
| Print DPI explicit, not string-sniffed | ⚠️ | Add `dpi` to the template model/snapshot |
| Borderless/safe-area handling | ⚠️ | Add a 3 mm safe area option; test with the real printer |
| EXIF/metadata in saved files | ⚠️ | Set orientation normal/date; consider `ExifInterface.saveAttributes` |
| Truncated-file protection | ❌ | Check `compress` result + `fd.sync()`/temp-rename |
| Auto-return keeps RAM free | ❌ | Add result timeout (§2.2) |

A concrete acceptance test: print the same portrait selfie with the 1-photo, 2-photo and
4-photo templates on the real printer, then compare 100 % crops to the on-screen result.
This catches the auto-rotate and safe-area issues immediately.

---

## 8. Build & dependency hygiene

- `androidx.room:2.7.0-alpha01` (`gradle/libs.versions.toml`) is an alpha in a kiosk app.
  Move to a stable Room release; schema/migration bugs here lose the print queue.
- `fallbackToDestructiveMigration(dropAllTables = true)` (`AppDatabase.kt:50`) wipes the
  queue on any schema mismatch. Acceptable for a queue, but pair it with a log entry so
  operators know why the queue emptied. Real migrations are cheap for four tables.
- `exportSchema = false` (`AppDatabase.kt:23`) leaves no schema history. Enable schema
  export + Room migration tests, especially once the indices below are added.
- Add `@Index`es (see §4.11) — this requires a version bump and a migration.
- `material-icons-extended` is pinned at 1.7.8 outside the BOM; it is a very large
  dependency for a handful of icons. Shrinking handles it in release, but debug builds and
  build time pay for it. Consider vendoring the 5–8 icons used.
- Release minify is on, but there are **no keep rules** (`proguard-rules.pro` is empty,
  `rules.keep` is a template). R8 + Room/Compose consumer rules cover the app today; add a
  smoke test of the release APK (capture + print) to the release checklist.
- No baseline profile / profile installer. Startup is once per shift, so low priority, but
  a macrobenchmark of the priority loop is worthwhile before/after the fixes.
- `compileSdk/targetSdk 37`, `minSdk 23`, Java 11: fine; remember `MediaStore`/`ImageDecoder`
  branches need API guards for 23–28.
- Tests exist for geometry, templates, queue planning, resolution filtering — good.
  The orientation/mirror matrix in `loadProcessAndDecorateImageProxy` is **not** covered;
  extract the matrix computation into a pure function and unit-test all 8 EXIF
  orientations × mirroring.

---

## 9. Suggested order of work

| Phase | Items | Expected outcome |
|---|---|---|
| **1 — Loop reliability** | 2.1 permission gate, 2.3 MediaStore/SAF destination, 2.7 folder check before countdown, 2.8 bounded retries, 2.9 DataStore fallback | First install works; no stuck states; photos always land on disk |
| **2 — Loop timing & UX** | 2.2 auto-return, result-before-save gating, 2.6 rotation, back-button swallow | Unattended loop runs continuously, one customer at a time |
| **3 — Memory** | 3.1 bounded overlay + bounded result decode, `largeHeap`, release on abandon, save queue | No OOM during long shifts; stable GC |
| **4 — Print** | 2.5 IO dispatcher, batch 1–2, decode cache, no bitmap rotation, SENT reconciliation, indices | No ANR/ANR-adjacent freezes; predictable queue behavior |
| **5 — Kiosk & security** | PIN + lockout, `allowBackup=false`, release signing guard, lock task, error-log retention, bar re-hide | Tamper-resistant, recoverable device |
| **6 — Quality** | COVER defaults, `autoRotate=false`, explicit DPI, safe area, fill light/+EV, EXIF, fsync | Prints and files match the sensor image |
| **7 — Hygiene** | stable Room, migrations, tests for transform, removal of dead permissions/paths | Lower long-term risk |

### Budgets to instrument (log once per capture)

- cover first frame: < 300 ms from process start (warm cache)
- tap → countdown first tick: < 100 ms
- shutter → result on screen: target < 1.5 s at 12 MP (record actual; it is the real
  quality/latency dial — do not trade quality for it, but know the number)
- result → cover (auto): configured timeout, no leak
- `dumpsys meminfo ar.mimbi.selfie` before/after a 20-photo shift: peak should stay below
  ~60 % of the heap limit
- log the actual bound capture resolution and final crop rectangle for every shot; that
  makes field reports actionable.

---

## 10. Quick wins (≤ 1 hour each)

1. Wrap `printPages`/`printReadyPages` in `Dispatchers.IO` (`PrintQueueManager.kt:94`).
2. `MAX_BATCH_PAGES = 2` (`:40`).
3. `Image(bitmap = remember(bmp) { bmp.asImageBitmap() })` (`CaptureScreen.kt:360, 471`).
4. Remove the per-recomposition `Log.d` (`:95`).
5. Default `DecorationFit.COVER` (`AppConfig.kt:16`).
6. `allowBackup="false"` (`AndroidManifest.xml:53`).
7. Add `SupervisorJob()` to the two long-lived scopes (`ErrorLogger.kt:13`,
   `PrintQueueManager.kt:34`).
8. Guard the print call against `capturedUriString == null` (`CaptureScreen.kt:518`).
9. Add a `BackHandler` on the cover that does nothing.
10. Gate `Log.d` calls with `BuildConfig.DEBUG`.
