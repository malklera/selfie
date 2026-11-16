package ar.mimbi.Selfie.printing.printer

import ar.mimbi.Selfie.printing.model.PrintablePage

interface Printer {
    suspend fun print(page: PrintablePage)
}
