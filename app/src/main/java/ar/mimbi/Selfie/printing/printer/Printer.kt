package ar.mimbi.Selfie.printing.printer

import ar.mimbi.Selfie.printing.model.PrintablePage

interface Printer {
    suspend fun connect()
    suspend fun getStatus(): PrinterStatus
    suspend fun print(page: PrintablePage)
    suspend fun disconnect()
}
