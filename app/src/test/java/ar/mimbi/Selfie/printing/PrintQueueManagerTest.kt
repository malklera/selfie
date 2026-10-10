package ar.mimbi.Selfie.printing

import ar.mimbi.Selfie.printing.model.PrintItem
import ar.mimbi.Selfie.printing.model.PrintItemStatus
import ar.mimbi.Selfie.printing.printer.Printer
import ar.mimbi.Selfie.printing.printer.PrinterManager
import ar.mimbi.Selfie.printing.printer.PrinterStatus
import ar.mimbi.Selfie.printing.queue.PrintQueueManager
import ar.mimbi.Selfie.printing.queue.PrintQueueRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintQueueManagerTest {

    private open class FakeRepository : PrintQueueRepository(null) {
        val failedItemsList = mutableListOf<PrintItem>()
        val resetIdsCalled = mutableListOf<Long>()

        override suspend fun getFailedItems(): List<PrintItem> {
            return failedItemsList
        }

        override suspend fun resetFailedItemsToPendingForIds(ids: List<Long>) {
            resetIdsCalled.addAll(ids)
        }

        override suspend fun initDefaultTemplates() {}
        override suspend fun resetPrintingItemsToPending() {}
    }

    private fun createTestPrinter(status: PrinterStatus): Printer = object : Printer {
        override suspend fun connect() {}
        override suspend fun getStatus(): PrinterStatus = status
        override suspend fun print(page: ar.mimbi.Selfie.printing.model.PrintablePage) {}
        override suspend fun disconnect() {}
    }

    @Test
    fun testRetryFailedReturnsFalseWhenNoPrinterConnected() = runBlocking {
        val fakeRepo = FakeRepository()
        fakeRepo.failedItemsList.add(
            PrintItem(id = 10, batchId = 1, photoUri = "file://1", sequence = 1, status = PrintItemStatus.FAILED)
        )
        val printerManager = PrinterManager() // Default status Disconnected

        val queueManager = PrintQueueManager(
            repository = fakeRepo,
            printerManager = printerManager
        )

        // When retryFailed is called without connected printer
        val result = queueManager.retryFailed()

        // Then it returns false and does not reset any items
        assertFalse(result)
        assertTrue(fakeRepo.resetIdsCalled.isEmpty())
    }

    @Test
    fun testRetryFailedResetsOnlyFailedItemsWhenPrinterReady() = runBlocking {
        val fakeRepo = FakeRepository()
        fakeRepo.failedItemsList.addAll(
            listOf(
                PrintItem(id = 10, batchId = 1, photoUri = "file://1", sequence = 1, status = PrintItemStatus.FAILED),
                PrintItem(id = 11, batchId = 1, photoUri = "file://2", sequence = 2, status = PrintItemStatus.CANCELED)
            )
        )
        val printerManager = PrinterManager()
        val readyPrinter = createTestPrinter(PrinterStatus.Ready)
        printerManager.setPrinter(readyPrinter, "Test Printer", printerType = "TEST", saveToDb = false)

        val queueManager = PrintQueueManager(
            repository = fakeRepo,
            printerManager = printerManager
        )

        val result = queueManager.retryFailed()

        assertTrue(result)
        assertEquals(listOf(10L, 11L), fakeRepo.resetIdsCalled)
    }

    @Test
    fun testPrintRemainingReturnsFalseWhenNoPrinterConnected() = runBlocking {
        val fakeRepo = FakeRepository()
        val printerManager = PrinterManager() // Default status Disconnected

        val queueManager = PrintQueueManager(
            repository = fakeRepo,
            printerManager = printerManager
        )

        val result = queueManager.printRemaining()

        assertFalse(result)
    }

    @Test
    fun testPrintRemainingReturnsTrueWhenPrinterReady() = runBlocking {
        val fakeRepo = FakeRepository()
        val printerManager = PrinterManager()
        val readyPrinter = createTestPrinter(PrinterStatus.Ready)
        printerManager.setPrinter(readyPrinter, "Test Printer", printerType = "TEST", saveToDb = false)

        val queueManager = PrintQueueManager(
            repository = fakeRepo,
            printerManager = printerManager
        )

        val result = queueManager.printRemaining()

        assertTrue(result)
    }

    @Test
    fun testClearQueueDelegatesToRepository() = runBlocking {
        var clearQueueCalled = false
        val fakeRepo = object : FakeRepository() {
            override suspend fun clearQueue() {
                clearQueueCalled = true
            }
        }
        val printerManager = PrinterManager()
        val queueManager = PrintQueueManager(
            repository = fakeRepo,
            printerManager = printerManager
        )

        queueManager.clearQueue()

        assertTrue(clearQueueCalled)
    }
}
