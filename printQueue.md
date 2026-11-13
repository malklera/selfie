# Task: Refactor the print queue to be page-centric and template-accurate

## Objective

Refactor the current print queue so that:

1. The queue reflects the active/selected template.
2. The page is the atomic unit for display, selection, failure, and retry.
3. Each queue entry clearly shows which template it belongs to.
4. Mixed-template selections are printed correctly, each page with its own template.
5. Printing uses one multi-page PDF per `PrintManager` job, not one job per page.
6. A failure affects the whole page, and retrying failed pages sends them together.
7. Remove the confusing "Lote n" concept from the UI and remove "Foto n" item titles.
8. Use the header format:
   `Página {n} - Plantilla {id} - {fotos}/{slots} fotos - {estado}`

This is a planning/implementation spec. Read the existing code first, then implement it consistently with the project style.

## Current state to preserve or replace

Relevant files:

- `app/src/main/java/ar/mimbi/Selfie/printing/model/PrintItem.kt`
- `app/src/main/java/ar/mimbi/Selfie/printing/model/PrintBatch.kt`
- `app/src/main/java/ar/mimbi/Selfie/printing/model/PrintTemplate.kt`
- `app/src/main/java/ar/mimbi/Selfie/printing/queue/PrintQueueRepository.kt`
- `app/src/main/java/ar/mimbi/Selfie/printing/queue/PrintQueueManager.kt`
- `app/src/main/java/ar/mimbi/Selfie/printing/ui/owner/QueueManagementScreen.kt`
- `app/src/main/java/ar/mimbi/Selfie/printing/persistence/*`
- `app/src/main/java/ar/mimbi/Selfie/data/db/AppDatabase.kt`
- `app/src/test/java/ar/mimbi/Selfie/printing/PrintQueueTest.kt`

Current problems:

- `PrintItem` = one photo copy.
- `PrintBatch` (“Lote”) = capture session tied to a template/version.
- The queue is a flat item list and only shows `Lote {batchId}`.
- `printSelectedItems()` ignores the template of each item and uses the currently active template for every selected item.
- Pages are inferred with `chunked(slotCount)`, so they have no stable identity or status.
- Retry is per item, not per page.
- `printRemaining(flush = true)` can print partial pages with blank slots without a clear warning.
- `PrinterManager` is never given a printer in `MainActivity`, so the background `processQueue()` path is effectively inactive. The real printing path is Android `PrintManager`.

## Core decisions (do not re-litigate)

- **Page is the atomic unit** for selection, printing, failure, and retry.
- **A page belongs to exactly one `(templateId, templateVersion)`.** Never mix photos from different templates in the same page.
- **Never fill a page across templates.** Each template has its own open page.
- **One multi-page PDF per `PrintManager` job**, containing all selected/eligible pages in deterministic order. Split into multiple jobs only when print attributes are incompatible or a configured size cap is exceeded.
- **Android job state applies to all pages included in that job.** If the job fails/cancels, all included pages become FAILED/CANCELED together.
- **Render failures are page-level.** If any photo in a page cannot be decoded/rendered, mark that page FAILED, exclude it from the PDF, and continue with other pages.
- **Do not auto-open the Android print dialog on capture.** Enqueue only. Printing is explicit from the queue.
- **Remove “Lote n” from the UI.** Use “Página n”.
- **Remove “Foto n” item titles.** Show thumbnails, slot position, and status.
- **Page numbering is per `(templateId, templateVersion)`.** `Página 2 - Plantilla 4` is unambiguous because the template is always shown.
- **Printing order is deterministic:** `page.createdAt ASC`, then `page.pageNumber ASC`, then `page.id ASC`. Within a page, order by `item.slotIndex ASC`.
- **Incomplete pages are not printable by default.** Only allow “Imprimir página parcial” through an explicit confirmation action.

## 1. Data model

### Add `PrintPageEntity`

Create `printing/persistence/PrintPageEntity.kt`:

```kotlin
@Entity(tableName = "print_pages")
data class PrintPageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Int,
    val templateVersion: Int,
    val templateSnapshotJson: String,
    val pageNumber: Int,
    val slotCount: Int,
    val createdAt: Long,
    val readyAt: Long? = null,
    val status: String, // PrintPageStatus.name
    val wasPartial: Boolean = false,
    val statusMessage: String? = null,
    val lastPrintJobId: String? = null,
    val printedAt: Long? = null
)
```

`templateSnapshotJson` must contain a frozen snapshot of the template at page creation:

- `id`, `description`, `version`
- `slots` (left/top/width/height)
- `widthCm`, `heightCm`
- `quality`, `colorMode`

Rendering must use this snapshot, not the current active template and not a later version of the template.

### Add `PrintPageStatus`

```kotlin
enum class PrintPageStatus {
    OPEN,       // accepts more photos; itemCount < slotCount
    READY,      // full; eligible for normal print
    SENT,       // included in an Android print job
    PRINTING,   // optional, for Android job in progress
    BLOCKED,    // optional, printer blocked
    PRINTED,    // job completed successfully
    FAILED,     // render or print failure; retryable
    CANCELED    // job canceled; retryable
}
```

### Update `PrintItemEntity`

Add:

```kotlin
val pageId: Long,
val slotIndex: Int
```

Keep `sequence` if useful for history/global ordering, but page composition/rendering must use `slotIndex`, not `chunked()`.

`PrintItemStatus` remains as-is, but synchronize it with the parent page:

- Page `OPEN`/`READY` → items `PENDING`
- Page `SENT`/`PRINTING` → items `SENT`
- Page `PRINTED` → items `PRINTED`
- Page `FAILED` → items `FAILED`
- Page `CANCELED` → items `CANCELED`

### Deprecate `PrintBatch`

- Stop exposing `PrintBatch`/`batchId` in the UI.
- Stop using batches for page grouping, printing, or retry.
- The page is the top-level queue grouping.
- Keep the batch table/entity temporarily only if needed for migration or history. Do not base new logic on it.
- Remove `"Lote ${item.batchId}"` from `QueueManagementScreen`.

### Room

- Add `PrintPageEntity` to `AppDatabase`.
- Add `PrintPageDao`.
- Bump the database version.
- Add a migration if practical. If not, rely on the existing `fallbackToDestructiveMigration(dropAllTables = true)` and explicitly note that the print queue will be cleared on upgrade.
- Prefer a migration that:
  - Creates `print_pages`.
  - Adds nullable `pageId` and `slotIndex` to `print_items`.
  - Rebuilds pages from existing batches/items grouped by template/version and `slotCount`.
  - Then makes `pageId` non-null for active items.

## 2. Page assembly and enqueue behavior

Create a pure, testable `PagePlanner` (or equivalent functions) used by the repository.

### On enqueue of a photo with `copies`

For each copy:

1. Get the active template.
2. Find the latest `OPEN` page for `(templateId, templateVersion)`.
3. If none exists, create one:
   - Snapshot the template JSON.
   - `pageNumber = max(pageNumber for that template/version) + 1`.
   - `slotCount = template.slotCount`.
   - `status = OPEN`.
4. Assign the item to the first free `slotIndex` (`0..slotCount-1`).
5. When `itemCount == slotCount`:
   - Set page `status = READY`, `readyAt = now`.
   - Do not create the next page until another item is enqueued.
6. If more copies remain, create the next page and continue.
7. Perform the whole operation in a Room transaction.
8. Do not call `processQueue()` or auto-print from enqueue.

This means:

- A 4-slot template with 1 copy creates “Página 1 - Plantilla 4 - 1/4 fotos - Abierta”.
- The next 3 copies fill the same page and mark it READY.
- The next copy creates “Página 2 - Plantilla 4 - 1/4 fotos - Abierta”.
- Switching to another template does not close the old template’s open page. Each template/version has its own open page.
- Switching back to the old template fills its still-open page.
- Copies of the same photo can fill multiple slots/pages.

### Page numbering

- `pageNumber` is sequential per `(templateId, templateVersion)`.
- Do not reuse numbers.
- Gaps are acceptable if pages are deleted; do not renumber existing pages.

## 3. Queue UI

Replace the flat item list with page cards/sections.

### Header format

Use exactly:

```text
Página {n} - Plantilla {id} - {fotos}/{slots} fotos - {estado}
```

Spanish status labels:

- `OPEN` → `Abierta`
- `READY` → `Lista`
- `SENT` → `Enviada`
- `PRINTING` → `Imprimiendo`
- `BLOCKED` → `Bloqueada`
- `PRINTED` → `Impresa`
- `FAILED` → `Fallida`
- `CANCELED` → `Cancelada`

### Page card content

- Show the page layout using the frozen snapshot slots, as a mini A4 preview.
- Each slot shows the assigned thumbnail, or an empty placeholder `+` if the page is incomplete.
- No “Foto n” title.
- Show a page status badge.
- Show `fotos/slots` progress.
- Add a horizontal divider between pages. Use `HorizontalDivider` or clear card separation. The divider must visually separate pages, not items inside the same page.

### Selection

- Default and primary selection unit: **the page**.
- Tapping the page header/card toggles selection of the whole page.
- Do not allow per-photo print selection in the first version. Per-photo actions should be “Quitar foto” (modify page composition), not “select only this photo”. This preserves page atomicity.
- `Seleccionar todo` selects all visible eligible pages.
- Selected count must be shown as pages, e.g. `3 páginas seleccionadas`.
- Mixed-template selection is allowed: selected pages from different templates are sent in one PDF when print attributes are compatible.
- Incomplete pages (`OPEN`) are not selectable for normal printing. Show a disabled/“Esperando n fotos” hint.
- `SENT`, `PRINTING`, `BLOCKED` pages are not selectable.
- `PRINTED` pages are not selectable for normal printing. If reprint is needed, require an explicit confirmation and re-send as a new job.
- `FAILED`/`CANCELED` pages are selectable for retry.
- Per-page overflow actions:
  - `Imprimir página parcial` only for `OPEN` pages with fewer than `slotCount` photos. Require confirmation. Set `wasPartial = true`; the page is closed after printing and must not receive more photos.
  - `Quitar página` deletes the page and its items.
  - `Reintentar página` for `FAILED`/`CANCELED`.

### Screen-level actions

Replace the current menu actions:

- `Imprimir páginas listas` → prints all `READY` pages in one PDF/job.
- `Reintentar fallidas` → resets all `FAILED`/`CANCELED` pages to `READY`/`OPEN` as appropriate and prints them together in one PDF/job.
- `Limpiar impresos` → deletes `PRINTED` pages and their items.
- `Vaciar cola completa` → deletes all pages and items.

Remove `printRemaining(flush = true)` or repurpose it to “print all READY pages”. Never silently flush partial pages.

### Banner

Replace the current “Generará un PDF de X páginas” estimate based on selected items.

Show:

- Active template.
- Counts by page status: ready, open/incomplete, sent, printed, failed.
- When pages are selected: `N páginas seleccionadas · M hojas a imprimir`.

## 4. Printing pipeline

### API

Replace `printSelectedItems(itemIds)` with something like:

```kotlin
suspend fun printPages(
    context: Context,
    pageIds: List<Long>,
    allowPartial: Boolean = false
): PrintPagesResult
```

Also provide:

```kotlin
suspend fun printReadyPages(context: Context): PrintPagesResult
suspend fun retryFailedPages(context: Context): PrintPagesResult
```

### Validation

Before starting a job:

- Load pages by id.
- Verify each page is eligible (`READY`, or `FAILED`/`CANCELED` being retried, or explicit partial `OPEN`).
- Verify `itemCount == slotCount` unless `allowPartial = true`.
- Parse `templateSnapshotJson`; if invalid, mark the page `FAILED` with a message.
- Verify each item has a decodable URI. Decode errors must be page-level failures.

### Grouping and ordering

- Group selected pages by compatible print attributes:
  - media size,
  - orientation,
  - resolution,
  - color mode,
  - margins.
- In this app, all default templates are A4 portrait, so normally this produces exactly one group.
- Each group becomes one `PdfDocument` and one `PrintManager` job.
- Order pages deterministically:
  - `createdAt ASC`,
  - then `pageNumber ASC`,
  - then `id ASC`.
- Within each page, order by `slotIndex ASC`.

### Rendering

- Render pages directly and sequentially into `PdfDocument`; do not build a `List<PrintablePage>` holding every page bitmap.
- Decode each photo with `inSampleSize` based on its slot pixel size, not the full page. Recycle each source bitmap after use.
- Ideally add a `TemplateRenderer.renderToCanvas(template, photos, canvas, pageSize)` method so a full-page intermediate bitmap is not required. If keeping `render()` returning `PrintablePage`, render one page at a time, draw it into the PDF page, then recycle immediately.
- Page size for `PdfDocument.PageInfo.Builder` must come from the page’s frozen snapshot.
- If a photo fails to decode/render:
  - Mark the whole page `FAILED` and its items `FAILED`.
  - Do not include the page in the PDF.
  - Continue with the next page.
- If no page could be rendered, do not call `PrintManager.print()`.

### Status transitions

Before calling `PrintManager.print()`:

- Set all included pages and items to `SENT`.
- Store `lastPrintJobId`.

After the job is created, poll `PrintJobInfo.state`:

- `STATE_COMPLETED` → pages/items `PRINTED`, set `printedAt`.
- `STATE_FAILED` → pages/items `FAILED`.
- `STATE_CANCELED` → pages/items `CANCELED`.
- `STATE_BLOCKED` → keep `SENT` or set `BLOCKED` with a message.
- Timeout (e.g. 5 minutes) → leave pages `SENT` with `statusMessage = "Estado de impresión desconocido"`. Do not mark as printed without success.
- On `print()` returning null or throwing → mark included pages/items `FAILED`.

Persist the Android print job id so app restart can attempt reconciliation via `PrintManager.getPrintJobInfos()`.

### Retry

`retryFailedPages()`:

- Select all `FAILED`/`CANCELED` pages.
- Reset each page to `READY` if full, or `OPEN` if incomplete and retry is explicitly allowed.
- Reset their items to `PENDING`.
- Send all retried pages in **one** new PDF/job.
- This satisfies the requirement: if several photos failed because their shared template/page failed, printing again sends them together.

### PrintDocumentAdapter

- Keep one adapter per PDF.
- `onLayout` reports the exact page count.
- `onWrite` writes the complete PDF and returns `PageRange.ALL_PAGES`.
- In-app page selection is the source of truth; do not claim to honor system page-range subsets if you always write all pages.
- Make PDF closing idempotent.
- Handle `print()` returning null and exceptions.

### Print attributes

Build from the template:

- Media size from `widthCm`/`heightCm`.
- Orientation from dimensions.
- Resolution from `quality` (e.g. 300 DPI vs 200 DPI).
- Color mode.
- Margins are encoded in the template slot geometry (there is no separate margin field).

## 5. Deletion and consistency rules

- Deleting a page deletes its items.
- Deleting an item from an unprinted page:
  - Remove the item.
  - Reflow remaining items’ `slotIndex` to keep them contiguous.
  - If the page was `READY` and now has free slots, set it back to `OPEN`.
  - If the page becomes empty, delete it.
- Deleting an item from a `SENT`/`PRINTING`/`PRINTED` page should not affect the physical output. Allow removal from queue history only; do not mutate a page that is in flight.
- `clearPrintedItems()` must delete `PRINTED` pages and their items.
- `clearQueue()` deletes all pages and items.
- On app start, do not blindly reset all in-flight pages. Only reset `PRINTING` to `READY`/`OPEN` if you cannot reconcile with `PrintManager`. Leave unknown `SENT` pages as `SENT` with a message.

## 6. Tests

Add/update unit tests for pure logic:

- Page planning:
  - 1 copy on a 4-slot template → one OPEN page, 1/4.
  - 4 copies → one READY page, 4/4.
  - 5 copies → one READY page + one OPEN page, 1/4.
  - Copies spanning multiple pages.
  - Switching templates keeps the old open page and creates/fills the new template’s open page.
  - Switching back fills the old open page.
  - Page numbers increment per template/version.
  - Never mix templates in one page.
- Deletion/reflow:
  - Deleting a middle item reflows slots.
  - READY page becomes OPEN when an item is removed.
  - Empty page is deleted.
- Print planning:
  - Mixed-template pages are ordered chronologically and each uses its own snapshot.
  - Incomplete pages are excluded unless partial is allowed.
  - Page render failure excludes only that page.
  - Failed pages are grouped into one retry plan.
  - Attribute grouping produces one group for A4 templates.
- Status transitions:
  - Job completed → all included pages PRINTED.
  - Job failed/canceled → all included pages FAILED/CANCELED.
  - Timeout → SENT, not PRINTED.
- Update `PrintQueueTest.kt` to the new page-based model.

## 7. Acceptance criteria

- The queue displays pages, not raw items.
- No “Lote n” appears anywhere.
- No “Foto n” title appears.
- Each page header matches:
  `Página {n} - Plantilla {id} - {fotos}/{slots} fotos - {estado}`.
- Pages are separated by a clear horizontal divider.
- A 4-slot template shows 1/4, 2/4, 3/4 as photos arrive, and becomes READY at 4/4.
- Photos from different templates never share a page.
- Selecting pages from template 2 and template 4 produces one PDF/job when media attributes match; each page is rendered with its own template snapshot.
- Printing uses one multi-page PDF per job, not one job per page.
- One page failing does not prevent other pages from printing; the failed page is marked failed.
- Retry sends all failed pages together in one PDF/job.
- Android job success marks all included pages PRINTED; failure/cancel marks all included pages FAILED/CANCELED; unknown/timeout leaves them SENT.
- Incomplete pages cannot be printed without an explicit partial-print confirmation.
- Bitmaps are decoded/recycled page-by-page or slot-by-slot; the app does not hold all page bitmaps in memory at once.
