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
 * Bunny's Terraria PE discovery panel. Resource recognition is only the first
 * conversion prerequisite; it does not mean that a playable tModLoader profile
 * has already been generated.
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
            Text("Terraria Android • verificação de recursos", style = MaterialTheme.typography.titleMedium)

            when (val state = detection) {
                null -> Text("Verificando Terraria e os arquivos de instalação…")
                TerrariaAndroidDetector.Detection.NotInstalled ->
                    Text("Terraria não encontrado. Instale a versão oficial para continuar.")
                is TerrariaAndroidDetector.Detection.Error ->
                    Text("Não foi possível verificar: ${state.message}")
                is TerrariaAndroidDetector.Detection.Installed -> {
                    Text("Terraria encontrado • versão ${state.versionName ?: "desconhecida"}")
                    Text(
                        "Pacotes APK legíveis: ${state.payload.readableArchiveCount}/${state.payload.archiveCount}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (state.payload.conversionSourceAvailable) {
                        Text(
                            "Estrutura Unity reconhecida. Os arquivos-base necessários para iniciar a conversão foram localizados; o perfil tModLoader ainda precisa ser preparado.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            "Instalação detectada, mas os contêineres Unity esperados não foram encontrados. Esta instalação ainda não pode ser convertida pelo Bunny.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
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
