package com.app.ralaunch.feature.terraria

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Small reusable status panel for Bunny Evolution's Terraria integration.
 * It deliberately launches the original game normally; it does not claim
 * that Android tModLoader support is available.
 */
@Composable
fun TerrariaAndroidStatusCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var detection by remember { mutableStateOf<TerrariaAndroidDetector.Detection?>(null) }

    LaunchedEffect(context) {
        detection = TerrariaAndroidDetector.detect(context)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Bunny Evolution", style = MaterialTheme.typography.titleLarge)
            Text("Terraria Android 1.4.4.9", style = MaterialTheme.typography.titleMedium)

            when (val state = detection) {
                null -> Text("Verificando se o Terraria está instalado…")
                TerrariaAndroidDetector.Detection.NotInstalled ->
                    Text("Terraria não encontrado. Instale a versão oficial para continuar.")
                is TerrariaAndroidDetector.Detection.Error ->
                    Text("Não foi possível verificar: ${state.message}")
                is TerrariaAndroidDetector.Detection.Installed -> {
                    Text("Terraria encontrado • versão ${state.versionName ?: "desconhecida"}")
                    Text(
                        "O jogo original pode ser aberto. O carregamento de mods Android ainda está em desenvolvimento.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = detection is TerrariaAndroidDetector.Detection.Installed,
                    onClick = {
                        val opened = runCatching {
                            TerrariaAndroidDetector.launch(context)
                        }.getOrDefault(false)
                        if (!opened) {
                            Toast.makeText(
                                context,
                                "Não foi possível abrir o Terraria.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    Text("Abrir Terraria")
                }

                OutlinedButton(onClick = {
                    detection = TerrariaAndroidDetector.detect(context)
                }) {
                    Text("Verificar novamente")
                }
            }
        }
    }
}
