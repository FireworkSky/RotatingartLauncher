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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.app.ralaunch.feature.terraria.conversion.TerrariaPeConversion
import kotlinx.coroutines.launch

/**
 * Bunny's Terraria PE discovery and resource-conversion panel.
 * A successful conversion prepares content only; it does not claim that the
 * tModLoader runtime has launched or loaded a mod.
 */
@Composable
fun TerrariaAndroidStatusCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var detection by remember { mutableStateOf<TerrariaAndroidDetector.Detection?>(null) }
    var converting by remember { mutableStateOf(false) }
    var conversionMessage by remember { mutableStateOf<String?>(null) }
    var conversionProgress by remember { mutableStateOf(0) }

    LaunchedEffect(context) {
        detection = TerrariaAndroidDetector.detect(context)
    }

    val profileAvailable = remember(detection) {
        runCatching { TerrariaPeConversion.profileAvailable(context) }.getOrDefault(false)
    }
    val installed = detection as? TerrariaAndroidDetector.Detection.Installed

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
                            "Estrutura Unity reconhecida. Os arquivos-base para conversão foram localizados.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            "Instalação detectada, mas os contêineres Unity esperados não foram encontrados.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (!profileAvailable) {
                        Text(
                            "Perfil de conversão ausente neste APK. Ele precisa ser preparado com os recursos de Terraria para PC e a versão correspondente do tModLoader.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            "Perfil pronto • tModLoader ${TerrariaPeConversion.targetTml(context)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = installed != null,
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

                OutlinedButton(
                    onClick = {
                        detection = TerrariaAndroidDetector.detect(context)
                        conversionMessage = null
                        conversionProgress = 0
                    }
                ) {
                    Text("Verificar novamente")
                }
            }

            Button(
                enabled = installed?.payload?.conversionSourceAvailable == true &&
                    profileAvailable && !converting,
                onClick = {
                    converting = true
                    conversionProgress = 0
                    conversionMessage = "Preparando Terraria com tModLoader…"
                    scope.launch {
                        try {
                            val game = TerrariaMobileInstallService.install(context) { message, percent ->
                                scope.launch {
                                    conversionMessage = message
                                    conversionProgress = percent
                                }
                            }
                            conversionMessage = game.displayedName + " foi adicionado à biblioteca do Bunny."
                            conversionProgress = 100
                        } catch (error: Exception) {
                            conversionMessage = "Falha na preparação: " + (error.message ?: "erro desconhecido")
                        } finally {
                            converting = false
                        }
                    }
                }
            ) {
                Text(if (converting) "Preparando…" else "Preparar jogo com mods")
            }
            conversionMessage?.let {
                Text(
                    if (converting) it + " (" + conversionProgress + "%)" else it,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
