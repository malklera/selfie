package ar.mimbi.selfie.printing.printer

import ar.mimbi.selfie.printing.model.PrintablePage

interface Printer {
    suspend fun print(page: PrintablePage)
}
