package ar.mimbi.Selfie.printing.ui.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun CopySelectorDialog(
    initialCopies: Int = 1,
    maxCopies: Int = 10,
    onConfirm: (copies: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var copyCount by remember { mutableIntStateOf(initialCopies) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Seleccionar Copias",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { if (copyCount > 1) copyCount-- },
                        enabled = copyCount > 1,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("-", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "$copyCount",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    OutlinedButton(
                        onClick = { if (maxCopies == 0 || copyCount < maxCopies) copyCount++ },
                        enabled = maxCopies == 0 || copyCount < maxCopies,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { onConfirm(copyCount) },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Imprimir")
                    }
                }
            }
        }
    }
}
