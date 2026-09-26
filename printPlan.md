# Android Photo Printing System — Implementation Plan

## 1. Objective

Implement a Kotlin Android photo-printing subsystem with these requirements:

* There are two roles:

  * **Owner:** configures the printer, printing settings, templates, and manages the print queue.
  * **Client:** only chooses whether a photo should be printed and how many copies. The client must not see printer configuration or a print dialog.
* The client can continue taking photos while printing happens asynchronously.
* Printing is based on configurable templates.
* A template determines how many photos fit on each physical page and where each photo is placed.
* Photos must:

  * preserve aspect ratio;
  * never be cropped;
  * never be distorted;
  * be scaled down/up to fit inside their assigned slot;
  * be centered in the slot when aspect ratios differ, leaving blank space as necessary.
* The template selected when a group of photos is queued must remain associated with those photos even if the active template changes later.
* The system must accumulate photos until enough exist to form complete pages.
* Example: a 2-photo template with 5 selected photos should automatically print the first 4 and leave 1 pending.
* The owner must have a **Print Remaining** action that forces incomplete pages to print, leaving unused template slots blank.
* Printing must be printer-agnostic for now.
* The final printer transport will be implemented behind an abstraction once the exact printer/model/protocol is known.
* Do not depend on the Android system print UI for the normal client workflow.

---

# 2. Recommended architecture

Use a layered architecture:

```text
UI
 |
 v
Application / ViewModel
 |
 v
Print Queue / Use Cases
 |
 +----------------------+
 |                      |
 v                      v
Template system       Printer abstraction
 |                      |
 v                      v
Page renderer        Printer implementation
 |                      |
 v                      v
Rendered page        Physical printer
```

Keep the following concepts separate:

1. Photo selection
2. Print queue
3. Print batches
4. Templates
5. Template rendering
6. Physical printer communication

The printer implementation must never contain template/layout logic.

The template renderer must never know how the printer is connected.

---

# 3. Project/package structure

Use a structure similar to:

```text
printing/
    model/
        PrintTemplate.kt
        PhotoSlot.kt
        PrinterConfig.kt
        PrintBatch.kt
        PrintItem.kt
        PrintStatus.kt

    template/
        TemplateRenderer.kt
        ImageFit.kt
        PageLayout.kt

    queue/
        PrintQueueRepository.kt
        PrintQueueManager.kt
        PrintQueueWorker.kt

    printer/
        Printer.kt
        PrinterStatus.kt
        PrinterCapabilities.kt
        PrinterFactory.kt

    persistence/
        AppDatabase.kt
        PrintBatchDao.kt
        PrintItemDao.kt
        TemplateDao.kt
        PrinterConfigDao.kt

    ui/
        owner/
            PrinterConfigScreen.kt
            TemplateConfigScreen.kt
            QueueManagementScreen.kt

        client/
            PhotoActionScreen.kt
            CopySelector.kt

    test/
        template/
        queue/
        printer/
```

Use the existing architecture conventions of the app if an architecture such as MVVM/Clean Architecture is already established. Do not introduce a second competing architecture.

Use the current ux/ui regarding where the configuration for the owner and the printing selection for the user is at.

---

# 4. Domain models

## 4.1 PrintTemplate

Create a template model containing at least:

```kotlin
data class PrintTemplate(
    val id: String,
    val name: String,
    val slots: List<PhotoSlot>,
    val version: Int
)
```

`slots.size` determines how many photos fit on one page.

Do NOT hard-code `photosPerPage` separately if it can always be derived from `slots.size`.

Examples:

* 1 slot = one photo per page
* 2 slots = two photos per page
* 4 slots = four photos per page

---

## 4.2 PhotoSlot

Represent positions using normalized coordinates rather than fixed pixels:

```kotlin
data class PhotoSlot(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)
```

Coordinates must use the range:

```text
0.0 .. 1.0
```

where:

```text
left/top = 0.0
right/bottom = 1.0
```

This makes the template independent of page resolution.

Example 2-photo template:

```text
slot 1:
left = 0
top = 0
width = 1
height = 0.5

slot 2:
left = 0
top = 0.5
width = 1
height = 0.5
```

Example 4-photo template:

```text
slot 1 = top-left
slot 2 = top-right
slot 3 = bottom-left
slot 4 = bottom-right
```

---

# 5. Template versioning / immutability

A queued print must not change meaning if the owner later edits the template.

When a new batch begins, save enough information to guarantee that the batch continues using the original template.

Preferred design:

```kotlin
data class PrintBatch(
    val id: Long,
    val templateId: String,
    val templateVersion: Int,
    val createdAt: Long,
    val status: BatchStatus
)
```

If templates are editable, either:

1. maintain immutable template versions, or
2. save a snapshot of the template layout into the batch.

Do not allow an existing batch to silently start using a newer template version.

New photos should always use the currently active template when they are added.

Existing photos must remain attached to the batch/template under which they were added.

If the selected template changes the last batch get automatically added to the queue, even if it do not have its fill of slots.

---

# 6. Print batches

Do not use only a flat global photo queue.

Use batches so template changes cannot mix incompatible photos.

For example:

```text
Batch A
Template: 2 photos/page
A
B
C
D
E

Batch B
Template: 4 photos/page
F
G
H
I
```

Each batch is independently processed.

Use:

```kotlin
data class PrintBatch(
    val id: Long,
    val templateId: String,
    val templateVersion: Int,
    val createdAt: Long,
    val status: BatchStatus
)
```

Possible statuses:

```kotlin
enum class BatchStatus {
    ACTIVE,
    PRINTING,
    COMPLETED,
    FAILED
}
```

---

# 7. Individual print items

Each selected photo/copy should be represented as an individual queue item.

Use:

```kotlin
data class PrintItem(
    val id: Long,
    val batchId: Long,
    val photoUri: String,
    val sequence: Long,
    val status: PrintItemStatus
)
```

Statuses:

```kotlin
enum class PrintItemStatus {
    PENDING,
    PRINTING,
    PRINTED,
    FAILED
}
```

If the client requests 3 copies of one photo, insert 3 logical print items.

Example:

```text
A
A
A
```

This makes template grouping predictable.

Do not store only:

```text
photo = A
copies = 3
```

unless there is a strong reason to optimize the database model later.

---

# 8. Client workflow

The client must have no access to printer configuration.

The client workflow should be:

```text
Take/select photo
    |
    v
Choose whether to print
    |
    v
Choose number of copies
    |
    v
Add copies to the active print batch
    |
    v
Immediately return to photo-taking workflow
```

The client should not:

* choose the printer;
* choose paper size;
* choose print quality;
* see a print dialog;
* choose Wi-Fi/Bluetooth;
* change templates;
* configure image fitting;
* see technical printer status unless the owner explicitly exposes a minimal error indicator.

After adding a photo to the queue, show only a lightweight confirmation such as:

```text
Added to print queue
```

and continue.

---

# 9. Active template

At any moment, the owner configures an active template.

When the client adds a photo:

```text
active template
      |
      v
create/use current batch for that template version
      |
      v
add PrintItem(s)
```

If the owner changes the active template later:

```text
existing batch -> unchanged
new client photos -> new template/batch
```

Never mix photos from different template versions into the same batch.

---

# 10. Queue grouping logic

For each active batch:

```text
template.slotCount = number of photos required for a complete page
```

For example:

```text
1 slot -> page size 1
2 slots -> page size 2
4 slots -> page size 4
```

For automatic printing:

```kotlin
items.chunked(template.slots.size)
```

Process only complete groups.

Example:

```text
Template size = 2

A
B
C
D
E

complete pages:
A B
C D

remaining:
E
```

The remaining item stays pending.

Do not delete it.

---

# 11. Print Remaining behavior

Add an owner-only **Print Remaining** action.

Normal queue processing:

```kotlin
processQueue(flush = false)
```

Flush processing:

```kotlin
processQueue(flush = true)
```

Normal mode:

```text
5 items with 2-photo template

A B -> print
C D -> print
E   -> remain pending
```

Flush mode:

```text
A B -> print
C D -> print
E   -> print alone
```

The final page must contain the photo in its assigned slot and leave every unused slot completely blank.

Do not invent placeholder images or alter the template.

---

# 12. Template rendering

Create a printer-independent renderer:

```kotlin
interface TemplateRenderer {
    fun render(
        template: PrintTemplate,
        photos: List<Bitmap>,
        pageWidthPx: Int,
        pageHeightPx: Int
    ): RenderedPage
}
```

The renderer must:

1. create a blank page;
2. convert normalized slot coordinates to actual page pixels;
3. load/scale each photo;
4. preserve aspect ratio;
5. fit the complete photo inside the slot;
6. center the result;
7. leave unused areas blank;
8. never crop;
9. never stretch/deform;
10. produce deterministic output for a given template, photo set, and page size.

---

# 13. Image fitting

Implement a reusable image-fit function.

Use:

```text
scale = min(
    slotWidth / photoWidth,
    slotHeight / photoHeight
)
```

Then:

```text
scaledWidth = photoWidth * scale
scaledHeight = photoHeight * scale
```

Center the scaled image inside the slot.

This is equivalent to an aspect-preserving FIT operation.

Do NOT use crop/centerCrop.

Do NOT stretch the bitmap to the slot dimensions.

Do not let different callers implement image fitting differently.

Put the behavior in one reusable function/class such as:

```kotlin
fun fitBitmapInside(
    bitmap: Bitmap,
    slotWidth: Int,
    slotHeight: Int
): FittedBitmap
```

---

# 14. Page dimensions

Do not render templates based on the phone display size.

The renderer needs a physical/page output size supplied by the printing layer.

Keep page dimensions separate from template coordinates.

For example:

```kotlin
data class PageSize(
    val widthPx: Int,
    val heightPx: Int
)
```

Later the printer configuration/adapter can determine the required output dimensions.

The same template should therefore be usable at multiple resolutions or physical paper sizes.

---

# 15. PrintablePage

Create a printer-neutral output object.

For example:

```kotlin
data class PrintablePage(
    val bitmap: Bitmap,
    val pageSize: PageSize
)
```

Later, a printer adapter can convert it to whatever representation the physical printer requires.

---

# 16. Printer abstraction

Create a minimal interface now, even before deciding the real printer protocol.

Example:

```kotlin
interface Printer {

    suspend fun connect()

    suspend fun getStatus(): PrinterStatus

    suspend fun print(page: PrintablePage)

    suspend fun disconnect()
}
```

Possible status:

```kotlin
sealed class PrinterStatus {
    data object Ready : PrinterStatus()
    data object Printing : PrinterStatus()
    data class Error(val message: String) : PrinterStatus()
    data object Disconnected : PrinterStatus()
}
```

Keep the interface small.

Do not expose Bluetooth or Wi-Fi terminology in the domain layer.

---

# 17. Printer manager

Create a higher-level `PrinterManager` or similar service that owns the printer lifecycle.

Responsibilities:

* read saved printer configuration;
* instantiate the selected printer implementation;
* connect when necessary;
* determine whether printing can proceed;
* send rendered pages;
* report errors;
* disconnect when appropriate.

It should not:

* decide photo layout;
* modify templates;
* manipulate client UI;
* decide which photos belong to which template.

---

# 18. Queue processing

Create one central queue processor.

Example:

```kotlin
suspend fun processQueue(
    batchId: Long,
    flush: Boolean
)
```

Responsibilities:

1. acquire pending items;
2. determine their template/version;
3. group them into pages;
4. leave incomplete groups alone unless `flush == true`;
5. mark items `PRINTING`;
6. render a page;
7. send it to the printer;
8. on success, mark corresponding items `PRINTED`;
9. on failure, mark items `FAILED` or restore them to `PENDING` according to retry policy;
10. continue with remaining pages if appropriate.

Do not mark items as printed before the printer operation succeeds.

---

# 19. Concurrency

Only allow one physical print-processing operation at a time.

The client can add queue items concurrently, but only one queue worker should communicate with the physical printer.

Use appropriate coroutine synchronization, such as:

* a single worker;
* `Mutex`;
* or a serialized work mechanism.

Avoid two simultaneous calls such as:

```text
printQueue()
printQueue()
```

both sending pages to the printer.

---

# 20. Persistence

Use the project's established persistence layer.

Persist at minimum:

### Printer configuration

Whatever is needed later, but keep it generic for now:

```text
selected printer ID
printer implementation/type
printer-specific configuration blob if necessary
```

### Templates

```text
template ID
name
version
slot geometry
active/inactive
```

### Print batches

```text
batch ID
template ID
template version
createdAt
status
```

### Print items

```text
item ID
batch ID
photo URI
sequence
status
```

Do not rely on in-memory collections for queue state.

The queue must survive app restarts.

---

# 21. Photo storage and queue references

The print queue must **not duplicate the image data**. Each `PrintItem` should store a persistent reference to the already-saved photo, preferably as a `Uri`/content URI rather than relying on a raw filesystem path.

For example:

```kotlin
data class PrintItem(
    val id: Long,
    val batchId: Long,
    val photoUri: String,
    val sequence: Long,
    val status: PrintItemStatus
)
```

The queue therefore contains metadata and references:

```text
PrintItem
    |
    └── photoUri ──────────────► existing PNG file
```

Prefer Android `Uri`/content-URI references where applicable instead of assuming that a photo can always be accessed through a raw filesystem path.

---

# 22. Loading and memory management during printing

Photos should be loaded into memory **only when they are actually needed for rendering a page**.

The intended pipeline is:

```text
PrintItem.photoUri
        |
        v
Load PNG
        |
        v
Decode at appropriate size
        |
        v
Fit into template slot
        |
        v
Draw onto rendered page
        |
        v
Release temporary Bitmap
        |
        v
Continue with next photo/page
```

Do not preload the entire print queue into memory.

For a template with four photos per page, load only the four photos required for the current page.

After the page has been rendered, release/discard the temporary source `Bitmap` objects as soon as they are no longer needed.

The renderer should process one page at a time.

Use Android image decoding APIs that allow the image to be decoded at an appropriate size rather than always decoding the original PNG dimensions.

The target decode size should be based on the size that the photo will occupy on the rendered page. Avoid decoding a 6000×4000 camera image to full resolution when the photo will only occupy a much smaller region of the printable page.

Do not resize the original PNG on disk merely for printing. The original saved photo should remain unchanged.

Memory behavior should therefore be:

```text
Queue:
    references only

During page rendering:
    current page photos -> temporary Bitmaps

After page rendering:
    temporary source Bitmaps -> released

After print success/failure:
    rendered page + temporary objects -> released

Queue:
    retains photo references and status
```

Test this with high-resolution camera PNGs and with long queues to ensure memory usage remains bounded and does not grow with the total number of queued photos.

Also test that a photo being printed remains accessible until the print operation has completed, and that a missing/deleted source PNG results in a recoverable `PhotoLoadFailed`/failed queue state rather than crashing the application.

---

# 25. Automatic queue printing

When enough photos have accumulated for a complete page:

```text
queue receives new item
       |
       v
check active batch
       |
       v
is there a complete page?
       |
   +---+---+
   |       |
  no      yes
   |       |
   |       v
   |    queue print work
   |       |
   +-------+
```

The client should not wait for the printer operation.

The queue worker should process it asynchronously.

If a printer is unavailable, items remain recoverable rather than disappearing.

---

# 26. What happens when printing fails

Define a clear policy.

Example:

```text
PENDING
   |
   v
PRINTING
   |
   +---- success ---> PRINTED
   |
   +---- failure ---> FAILED
```

A failed item/page must not silently become `PRINTED`.

The owner should be able to retry failed work.

Be especially careful with partial physical printing.

If the printer reports failure after actually producing a page, automatic retries can cause duplicates.

Therefore keep the printer layer able to report the best available status and do not assume every transport gives transactional "exactly once" semantics.

For the first version, make failures visible to the owner rather than attempting sophisticated automatic recovery.

---

# 27. Print Remaining and failed items

The owner should be able to distinguish:

```text
Pending
Failed
Printed
```

Do not make `Print Remaining` automatically retry unrelated failures unless intentionally designed.

Prefer separate actions:

```text
Print Remaining
Retry Failed
```

This avoids accidentally producing duplicates.

---

# 28. Test strategy

Testing must be divided into:

1. pure unit tests;
2. rendering tests;
3. persistence/queue tests;
4. integration tests;
5. manual printer tests.

The template and queue systems must be testable without any physical printer.

---

# 29. Template unit tests

Test all template definitions.

For every template verify:

* expected number of slots;
* slot coordinates are within 0..1;
* slot dimensions are positive;
* slots have expected positions;
* template versions are correct.

Examples:

```text
one-photo template -> 1 slot
two-photo template -> 2 slots
four-photo template -> 4 slots
```

---

# 30. Image fitting tests

Create deterministic unit tests for image fitting.

Test cases:

### Same aspect ratio

Input:

```text
1000x1000
```

Slot:

```text
500x500
```

Expected:

```text
500x500
```

### Landscape photo into portrait slot

Verify:

* entire image remains visible;
* image is centered;
* blank space exists on top/bottom;
* no horizontal distortion.

### Portrait photo into landscape slot

Verify:

* entire image remains visible;
* image is centered;
* blank space exists on left/right.

### Very wide photo

### Very tall photo

### Tiny image

### Large image

Ensure no case crops or stretches the image.

---

# 31. Renderer tests

For each template:

1. create predictable test images;
2. render at a known page size;
3. verify slot placement;
4. verify aspect preservation;
5. verify blank unused areas;
6. verify photo ordering.

Prefer golden/snapshot image tests for the renderer if practical.

For example:

```text
Template = 4 photos
Photos = red, green, blue, yellow test images
```

Expected result:

```text
red     green
blue    yellow
```

The actual test should use deterministic generated test bitmaps rather than arbitrary camera photos.

---

# 32. Partial-page renderer tests

Test:

```text
2-slot template + 1 photo
```

Expected:

```text
photo in slot 1
slot 2 blank
```

Test:

```text
4-slot template + 1 photo
4-slot template + 2 photos
4-slot template + 3 photos
```

All unused slots must remain blank.

---

# 33. Queue tests

Test automatic processing.

For a 2-photo template:

### 0 photos

Nothing printed.

### 1 photo

Nothing printed; 1 remains pending.

### 2 photos

One page printed; 0 pending.

### 3 photos

One page printed; 1 pending.

### 4 photos

Two pages printed; 0 pending.

### 5 photos

Two pages printed; 1 pending.

---

# 34. Flush tests

For a 2-photo template:

```text
A
B
C
```

Normal processing:

```text
printed: A B
pending: C
```

Flush:

```text
printed: A B
printed: C
pending: none
```

For a 4-photo template test:

```text
A B C
```

Flush must print one page containing:

```text
A B C [blank]
```

Do not silently discard C.

---

# 35. Copy-count tests

Verify:

```text
1 photo + 1 copy -> 1 queue item
1 photo + 2 copies -> 2 queue items
1 photo + 5 copies -> 5 queue items
```

For a 2-photo template:

```text
A x 3
```

must produce:

```text
A A
A
```

when flushed.

For automatic mode:

```text
A A
```

prints immediately as one complete page, while the third A remains pending.

---

# 36. Template-change tests

Critical test:

1. select template A;
2. add photos 1, 2, 3;
3. change active template to B;
4. add photos 4, 5;
5. verify photos 1–3 remain attached to template A;
6. verify photos 4–5 belong to template B;
7. verify they never get combined into one page.

Also test template version changes.

---

# 37. Persistence tests

Test:

* app restart with pending queue;
* app restart during an active batch;
* queued photos remain associated with their batch;
* statuses persist;
* printed items do not become pending again simply because the app restarted;
* failed items remain retryable;
* template versions remain stable.

Use an in-memory Room database for fast tests.

---

# 38. Fake printer for tests

Implement:

```kotlin
class FakePrinter : Printer
```

The fake printer should:

* record every `PrintablePage`;
* expose configurable success/failure;
* record connect/disconnect calls;
* optionally delay printing;
* allow tests to inspect page count/order.

Example:

```kotlin
val printer = FakePrinter()

processor.processQueue(...)

assertEquals(2, printer.printedPages.size)
```

Do not need a physical printer for queue tests.

---

# 39. Printer abstraction tests

Test the queue processor against the interface, not against Epson.

Verify:

* connect happens;
* pages are sent in the correct order;
* successful pages become printed;
* failed pages are not marked printed;
* no duplicate pages are generated from the same pending items;
* only one print operation occurs at a time.

---

# 40. Concurrency tests

Test scenarios such as:

```text
client adds photo
+
queue worker starts
+
client adds another photo
```

The system must not:

* lose queue items;
* process the same item twice;
* corrupt ordering;
* create duplicate pages.

Also test two attempts to start queue processing simultaneously.

Only one should own the printer-processing lock.

---

# 41. Restart/recovery tests

Simulate:

```text
queue contains A B
worker starts
app process stops
app starts again
```

Decide and document the recovery policy for an item that was `PRINTING`.

Recommended initial behavior:

```text
PRINTING
   after unexpected restart
       ↓
return to FAILED or PENDING
       ↓
owner reviews/retries
```

Do not blindly mark it printed.

Physical printers may have produced the page even if the app did not receive confirmation.

---

# 42. UI tests

Client UI tests should verify:

* client can select print;
* client can choose copies;
* item is added to queue;
* client can immediately continue;
* no printer configuration is exposed;
* no print dialog is displayed.

Owner UI tests should verify:

* printer configuration is accessible;
* template configuration is accessible;
* queue status is visible;
* Print Remaining is available;
* failed items can be handled;
* configuration changes do not modify existing batches.

---

# 43. Manual end-to-end tests

Once the real printer is chosen, create a dedicated physical-printer test checklist.

Test at least:

1. one photo/page;
2. two photos/page;
3. four photos/page;
4. mixed aspect ratios;
5. portrait photos;
6. landscape photos;
7. large camera images;
8. multiple copies;
9. odd number of photos;
10. Print Remaining;
11. empty queue;
12. printer disconnected;
13. printer unavailable;
14. printer out of paper if detectable;
15. app restarted with pending items;
16. change template while a previous batch still has pending items;
17. queue many photos while printing is in progress.

---

# 44. Printer integration should be the final layer

Do NOT implement printer-specific code before the queue and renderer are stable.

Once the actual Epson model and connection type are known:

1. determine supported protocol/API;
2. determine how the printer is discovered;
3. determine how the owner configures the printer;
4. determine supported paper sizes;
5. determine supported image/page formats;
6. implement a concrete `Printer`;
7. connect it to `PrinterManager`;
8. run physical integration tests.

The rest of the application should remain unchanged.

---

# 46. Configuration persistence

Persist the following separately:

### Application print configuration

* active template;
* template version;
* general defaults.

### Printer configuration

* selected printer;
* implementation/type;
* transport-specific information later.

### Queue

* batches;
* items;
* statuses.

Do not mix all of these into one settings object.

---

# 47. Logging

Add structured logging around the queue and printer layers.

Log:

* batch ID;
* item IDs;
* template ID/version;
* page number;
* number of photos on page;
* print start;
* print success/failure;
* printer status;
* retry operations.

Never log unnecessary personal/private photo content.

Avoid logging full photo URIs if they contain sensitive filesystem/path information unless needed for debugging.

---

# 48. Error handling

Errors should be converted into application-level states.

Examples:

```text
PrinterUnavailable
PrinterNotConfigured
PrinterConnectionFailed
PrintFailed
InvalidTemplate
PhotoLoadFailed
InsufficientStorage
```

Do not expose raw low-level Bluetooth/Wi-Fi/vendor exceptions directly to the client.

Owner-facing diagnostics can contain more detail.

---

# 49. Performance targets

The system should:

* add a photo to the queue quickly;
* never block the camera workflow waiting for physical printing;
* render one page at a time;
* avoid keeping all photos as bitmaps in memory;
* survive long photo sessions with many queued items;
* avoid unnecessary re-rendering.

---

# 50. Implementation order

Implement in this order:

### Phase 1 — Domain models

Implement:

```text
PrintTemplate
PhotoSlot
PrintBatch
PrintItem
statuses
PageSize
PrintablePage
```

Add unit tests.

### Phase 2 — Template definitions

Implement initial templates:

```text
1 photo
2 photos
4 photos
```

Add validation and tests.

### Phase 3 — Image fitting

Implement aspect-preserving FIT behavior.

Add comprehensive unit tests.

### Phase 4 — Page renderer

Implement `TemplateRenderer`.

Add deterministic rendering tests/golden tests.

### Phase 5 — Database

Implement Room entities/DAOs for:

```text
templates
batches
items
printer configuration
```

Add persistence tests.

### Phase 6 — Queue manager

Implement:

```text
add photo/copies
create batch
automatic complete-page processing
Print Remaining
status transitions
```

Use `FakePrinter`.

Add comprehensive queue tests.

### Phase 7 — Async worker/concurrency

Implement background queue processing and ensure only one printer operation occurs at once.

Add concurrency tests.

### Phase 8 — Owner UI

Implement:

```text
printer configuration placeholder
template configuration
queue status
Print Remaining
Retry Failed
```

### Phase 9 — Client UI

Implement the minimal flow:

```text
photo
→ print?
→ copies
→ enqueue
→ return immediately
```

### Phase 10 — Printer abstraction

Finalize the `Printer` interface and `PrinterManager`, using only a fake implementation initially.

### Phase 11 — Real printer integration

After the exact printer is known, determine its protocol and implement the real `Printer`.

Do not modify the domain/template/queue logic merely to accommodate the printer unless absolutely necessary.

### Phase 12 — Physical testing

Run the complete manual test matrix against the real printer.

# 51. Acceptance criteria

The feature is considered functionally complete when all of the following are true:

- Client can add photos to a print queue without interacting with printer settings.
- Client can specify copy count.
- Client can continue taking photos immediately after queueing a print.
- Photos persist across application restarts.
- Templates are independent of printer technology.
- Templates preserve aspect ratio.
- No photo is cropped.
- No photo is distorted.
- Photos are centered within their slots.
- Complete pages print automatically.
- Incomplete pages remain queued.
- Owner can explicitly print incomplete pages using Print Remaining.
- Template changes affect only newly created batches.
- Existing batches retain their original template/version.
- Multiple template batches never get mixed.
- Queue survives application restarts.
- Printing failures do not falsely mark items as printed.
- A fake printer can be used to test the entire queue/renderer system without hardware.
- The actual printer can later be implemented behind the Printer interface without redesigning the client workflow.
- No printer-specific protocol assumptions exist in the template or queue layers.

# 52. Important implementation constraint

Do not over-engineer the printer layer before the actual printer is selected.

For the first implementation, the important pieces are:

Template
    +
Image fitting
    +
Page renderer
    +
Persistent queue
    +
Batch/template association
    +
Automatic complete-page processing
    +
Owner Print Remaining
    +
Fake printer

These components can be developed and tested completely independently of Wi-Fi/Bluetooth.

Once the physical printer is known, only then implement:

Printer discovery
Printer configuration
Connection
Printer protocol
Page transmission
Printer-specific errors/status

The rest of the application should consume the same Printer interface.
