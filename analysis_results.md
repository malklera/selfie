# Codebase Analysis Report

This document contains the findings from a comprehensive search across the project for **empty files**, **unused elements**, and **potential performance issues**.

---

## 2. Unused Elements ("Unused Things")

### Unused Database Entities & DAOs
1. **`PrintBatchEntity` & `PrintBatchDao`**
   - **Files**:
     - `app/src/main/java/ar/mimbi/selfie/printing/persistence/PrintBatchEntity.kt`
     - `app/src/main/java/ar/mimbi/selfie/printing/persistence/PrintBatchDao.kt`
   - **Issue**: `PrintBatchEntity` table (`print_batches`) and its DAO `PrintBatchDao` are registered in `AppDatabase.kt`, but batch records are never created, inserted, or queried anywhere in the application.
   - **Impact**: Dead database table and unused boilerplate code.

### Unused / Stubbed Infrastructure
1. **`PrinterManager` & `PrinterStatus` & `Printer` Interface**
   - **Files**:
     - `app/src/main/java/ar/mimbi/selfie/printing/printer/Printer.kt`
     - `app/src/main/java/ar/mimbi/selfie/printing/printer/PrinterManager.kt`
     - `app/src/main/java/ar/mimbi/selfie/printing/printer/PrinterStatus.kt`
   - **Issue**:
     - `Printer` interface is not implemented by any active class in the project.
     - `PrinterStatus` only defines `data object Disconnected`.
     - `PrinterManager` holds state flows (`_status`, `_currentPrinterName`) that are never updated or read during actual print workflows. Printing is performed directly via Android's native `PrintManager` and `PrintDocumentAdapter`.

### Deprecated Code
1. **`AppConfig.pictureResolution`**
   - **File**: `app/src/main/java/ar/mimbi/selfie/data/AppConfig.kt`
   - **Details**: Property is annotated `@Deprecated("Use inputResolution instead", ReplaceWith("inputResolution"))` but still retains getter logic delegating to `inputResolution`.

### Unused Exception Parameters
1. **`CaptureScreen.kt`** (Line 131): Exception parameter `e` in `catch (e: Exception)` when decoding photo bitmaps.
2. **`ConfigurationScreen.kt`** (Line 1045): Exception parameter `e` in `catch (e: Exception)` during SAF directory resolution.

---

## 3. Potential Performance Issues

### 1. Memory Pressure & OOM Risk in PDF Printing Generation
- **File**: `app/src/main/java/ar/mimbi/selfie/printing/queue/PrintQueueManager.kt`
- **Issue**: In `printPages()`, all photos across all pages in a batch are decoded at full scale into RAM simultaneously (`loadedBitmaps`). For queue items containing multiple pages with multi-slot templates (e.g. 4 photos per page across 20 pages = 80 full-resolution bitmaps in RAM), memory usage can easily exceed the Android app heap limit, leading to `OutOfMemoryError` (OOM).
- **Recommendation**: Process and recycle bitmaps per page rather than holding all batch bitmaps in memory simultaneously, or bound max batch page size.

### 2. Room Transaction Flow Re-emitting Full Queue History
- **File**: `app/src/main/java/ar/mimbi/selfie/printing/persistence/PrintPageDao.kt`
- **Issue**: `getAllPagesWithItemsFlow()` uses a `@Transaction` query that selects ALL pages and items sorted by `createdAt DESC`. Whenever any page or item status is updated, Room re-runs the full query and emits the complete history of all pages and items.
- **Impact**: As the historical queue grows, UI screens observing `allPagesFlow` (such as `QueueManagementScreen`) suffer increased recomposition latencies and higher CPU usage.

### 3. Collection Allocation Churn in Camera & Printer Helpers
- **Files**:
  - `app/src/main/java/ar/mimbi/selfie/data/CameraResolutionHelper.kt` (Line 53)
  - `app/src/main/java/ar/mimbi/selfie/printing/printer/PrinterInfoHelper.kt` (Line 92, 197)
- **Issue**: Expressions like `resolutions.filter { ... }.sortedByDescending { ... }` and `activeJobs.mapNotNull { ... }.distinct()` create multiple intermediate list instances.
- **Recommendation**: Use Kotlin `Sequence` (`asSequence()`) to avoid intermediate collection allocations.

### 4. Excessive Intermediate Bitmap Allocations in Capture Workflow
- **File**: `app/src/main/java/ar/mimbi/selfie/ui/screens/CaptureScreen.kt` (`loadProcessAndDecorateImageProxy`)
- **Issue**: Image capture creates and recycles multiple intermediate Bitmaps (`bitmap`, `orientedBitmap`, `mirroredBitmap`, `resultBitmap`) sequentially for orientation and mirroring operations.
- **Impact**: High-resolution camera frames trigger large contiguous memory allocations in quick succession, driving garbage collection (GC) pauses during photo capture.

### 5. Thread Safety / Visibility in `UserActionTracker`
- **File**: `app/src/main/java/ar/mimbi/selfie/data/UserActionTracker.kt`
- **Issue**: `currentScreen` and `lastAction` are stored as non-volatile plain `var` properties. They are updated on the Main UI thread and read on background coroutine threads (`Dispatchers.IO`) inside `ErrorLogger`.
- **Recommendation**: Add `@Volatile` annotations or thread-safe primitives to guarantee atomic visibility across threads.

### 6. Missing Recomposition Caching (`remember` / `derivedStateOf`)
- **File**: `app/src/main/java/ar/mimbi/selfie/printing/ui/owner/QueueManagementScreen.kt` (Line 186)
- **Issue**: Mapping transformations (e.g. `eligibleSelectablePages.map { it.id }.toSet()`) are re-evaluated on every recomposition without being wrapped in `remember`.

---

## 4. Summary Table

| Category | Item | File Path / Location | Severity |
| :--- | :--- | :--- | :--- |
| **Empty File** | Empty `<resources />` | `app/src/main/res/values/colors.xml` | Low |
| **Empty File** | Unused Keep Rules | `app/src/main/keepRules/rules.keep` | Low |
| **Unused Element** | Unused DB Entity & DAO | `PrintBatchEntity.kt`, `PrintBatchDao.kt` | Medium |
| **Unused Element** | Stubbed Printer Classes | `PrinterManager.kt`, `PrinterStatus.kt`, `Printer.kt` | Medium |
| **Performance** | OOM Risk in PDF Printing | `PrintQueueManager.kt` | **High** |
| **Performance** | Transaction Flow Overhead | `PrintPageDao.kt` (`getAllPagesWithItemsFlow`) | **High** |
| **Performance** | Intermediate Bitmap Churn | `CaptureScreen.kt` (`loadProcessAndDecorateImageProxy`) | Medium |
| **Performance** | Collection Allocation Churn | `CameraResolutionHelper.kt`, `PrinterInfoHelper.kt` | Low |
| **Concurrency** | Non-volatile State Access | `UserActionTracker.kt` | Medium |

