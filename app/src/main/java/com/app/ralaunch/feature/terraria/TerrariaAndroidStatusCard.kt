package com.app.ralaunch.feature.terraria

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.app.ralaunch.core.di.contract.IGameRepositoryServiceV3
import com.app.ralaunch.feature.terraria.conversion.TerrariaPeConversion
import org.koin.java.KoinJavaComponent
import kotlinx.coroutines.launch

/**
 * Bunny's Terraria PE detection, isolated tModLoader installation and .tmod import panel.
 * Runtime launch and real mod loading still require device validation.
 */
@Composable
fun TerrariaAndroidStatusCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gameRepository = remember { KoinJavaComponent.get(IGameRepositoryServiceV3::class.java) }
    val games by gameRepository.games.collectAsState()
    var installedGameId by remember { mutableStateOf<String?>(null) }
    var detection by remember { mutableStateOf<TerrariaAndroidDetector.Detection?>(null) }
    var converting by remember { mutableStateOf(false) }
    var conversionMessage by remember { mutableStateOf<String?>(null) }
    var conversionProgress by remember { mutableStateOf(0) }

    LaunchedEffect(context) {
        detection = TerrariaAndroidDetector.detect(context)
    }

    LaunchedEffect(games) {
        installedGameId = games.firstOrNull { it.gameId == "tModLoader" }?.id
    }

    val modPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val gameId = installedGameId
        if (uri != null && gameId != null) {
            scope.launch {
                try {
                    val mod = TerrariaTModRepository.installFromUri(context, gameId, uri)
                    conversionMessage = "Mod " + mod.name + " instalado. Ele será carregado na próxima inicialização do tModLoader."
                } catch (error: Exception) {
                    conversionMessage = "Falha ao instalar mod: " + (error.message ?: "erro desconhecido")
                }
            }
        }
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

            OutlinedButton(
                enabled = installedGameId != null && !converting,
                onClick = { modPicker.launch(arrayOf("*/*")) }
            ) {
                Text("Adicionar arquivo .tmod")
            }
        }
    }
}
