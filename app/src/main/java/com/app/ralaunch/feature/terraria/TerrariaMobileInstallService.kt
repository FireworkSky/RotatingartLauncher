package com.app.ralaunch.feature.terraria

import android.content.Context
import com.app.ralaunch.core.di.contract.IGameRepositoryServiceV3
import com.app.ralaunch.core.model.GameItem
import com.app.ralaunch.feature.installer.InstallCallback
import com.app.ralaunch.feature.installer.InstallPluginRegistry
import com.app.ralaunch.feature.installer.plugins.TerrariaInstallPlugin
import com.app.ralaunch.feature.terraria.conversion.TerrariaPeConversion
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.koin.java.KoinJavaComponent
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * End-to-end Android Terraria setup:
 * PE APK resources -> desktop-compatible Content -> isolated tModLoader folder
 * -> game repository entry. The original installed Terraria package is untouched.
 */
object TerrariaMobileInstallService {
    private const val API = "https://api.github.com/repos/tModLoader/tModLoader/releases/tags/v"
    private const val USER_AGENT = "BunnyEvolution-Android"

    suspend fun install(
        context: Context,
        reportProgress: (message: String, percent: Int) -> Unit
    ): GameItem = withContext(Dispatchers.IO) {
        val version = checkNotNull(TerrariaPeConversion.targetTml(context)) {
            "Este APK do Bunny não contém um perfil de conversão."
        }
        reportProgress("Verificando Terraria Android…", 2)
        val resources = File(
            context.filesDir,
            "bunny/terraria-android/converted-$version"
        )
        if (resources.exists()) resources.deleteRecursively()
        resources.parentFile?.mkdirs()

        var storageRoot: File? = null
        try {
            reportProgress("Convertendo recursos do Terraria PE…", 5)
            TerrariaPeConversion.convert(context, resources) { stage, current, total ->
                val percent = if (total > 0) 5 + current * 55 / total else 5
                reportProgress(conversionLabel(stage), percent)
                true
            }

            reportProgress("Baixando tModLoader $version…", 62)
            val archive = downloadTModLoader(context, version) { percent ->
                reportProgress("Baixando tModLoader $version…", 62 + percent * 20 / 100)
            }

            val repository = KoinJavaComponent.get(IGameRepositoryServiceV3::class.java)
            val (rootPath, _) = repository.createGameStorageRoot("tModLoader")
            val gameStorageRoot = File(rootPath)
            storageRoot = gameStorageRoot
            val plugin = InstallPluginRegistry.getPluginById("terraria") as? TerrariaInstallPlugin
                ?: error("O instalador Terraria do Bunny não está registrado.")

            reportProgress("Instalando tModLoader e preparando a pasta de mods…", 84)
            val gameItem = suspendCancellableCoroutine { continuation ->
                plugin.installConvertedResources(
                    resources,
                    archive,
                    gameStorageRoot,
                    object : InstallCallback {
                        override fun onProgress(message: String, progress: Int) {
                            reportProgress(message, progress.coerceIn(84, 98))
                        }

                        override fun onComplete(gameItem: GameItem) {
                            if (continuation.isActive) continuation.resume(gameItem)
                        }

                        override fun onError(error: String) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(IllegalStateException(error))
                            }
                        }

                        override fun onCancelled() {
                            if (continuation.isActive) {
                                continuation.resumeWithException(IllegalStateException("Instalação cancelada."))
                            }
                        }
                    }
                )
                continuation.invokeOnCancellation { plugin.cancel() }
            }

            // Record the exact runtime version so the mod importer can reject mismatched packages.
            writeRuntimeMarker(gameStorageRoot, gameItem.gameExePathRelative, version)

            reportProgress("Registrando o jogo no Bunny…", 99)
            repository.upsert(gameItem, 0)
            resources.deleteRecursively()
            reportProgress("Terraria com tModLoader foi adicionado à biblioteca.", 100)
            gameItem
        } catch (error: Exception) {
            storageRoot?.deleteRecursively()
            resources.deleteRecursively()
            throw error
        }
    }

    private fun writeRuntimeMarker(storageRoot: File, assemblyRelativePath: String, version: String) {
        val assembly = File(storageRoot, assemblyRelativePath).canonicalFile
        require(assembly.isFile && assembly.name == "tModLoader.dll") {
            "A instalação não contém o assembly tModLoader esperado."
        }
        val runtimeDirectory = checkNotNull(assembly.parentFile)
        val marker = File(runtimeDirectory, "bunny-runtime.json")
        val temporary = File.createTempFile(".bunny-runtime-", ".json", runtimeDirectory)
        try {
            temporary.writeText(
                JSONObject()
                    .put("loaderVersion", version)
                    .put("terrariaVersion", "1.4.4.9")
                    .put("source", "terraria-mobile")
                    .toString(2)
            )
            try {
                java.nio.file.Files.move(
                    temporary.toPath(), marker.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(
                    temporary.toPath(), marker.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            temporary.delete()
        }
    }

    private fun conversionLabel(stage: String): String = when (stage) {
        "extract" -> "Extraindo arquivos do Terraria Android…"
        "assets" -> "Convertendo texturas, fontes e efeitos…"
        "music" -> "Convertendo músicas…"
        "verify" -> "Verificando recursos convertidos…"
        else -> "Preparando recursos do Terraria…"
    }

    private fun downloadTModLoader(
        context: Context,
        version: String,
        onProgress: (Int) -> Unit
    ): File {
        val apiConnection = (URL(API + version).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        val release = try {
            require(apiConnection.responseCode == HttpURLConnection.HTTP_OK) {
                "Não foi possível consultar a versão oficial do tModLoader."
            }
            JSONObject(apiConnection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            apiConnection.disconnect()
        }
        val assets = release.getJSONArray("assets")
        var downloadUrl: String? = null
        var expectedSize = -1L
        var expectedDigest: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            if (asset.optString("name") == "tModLoader.zip") {
                downloadUrl = asset.optString("browser_download_url")
                expectedSize = asset.optLong("size", -1)
                expectedDigest = asset.optString("digest").removePrefix("sha256:")
                break
            }
        }
        val url = checkNotNull(downloadUrl) { "O pacote oficial tModLoader.zip não foi encontrado." }
        require(expectedSize > 0 && !expectedDigest.isNullOrBlank()) {
            "A publicação do tModLoader não forneceu tamanho e SHA-256 verificáveis."
        }

        val destination = File(context.cacheDir, "tModLoader-$version.zip")
        if (destination.isFile && destination.length() == expectedSize &&
            sha256(destination).equals(expectedDigest, ignoreCase = true) && validTModLoaderZip(destination)
        ) {
            onProgress(100)
            return destination
        }
        destination.delete()
        val temporary = File.createTempFile("bunny-tml-", ".zip", context.cacheDir)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Falha ao baixar o pacote oficial do tModLoader."
            }
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var total = 0L
                    var lastPercent = -1
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= expectedSize) { "O pacote baixado excedeu o tamanho publicado." }
                        output.write(buffer, 0, count)
                        val percent = (total * 100 / expectedSize).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                    output.fd.sync()
                    require(total == expectedSize) { "Download incompleto do tModLoader." }
                }
            }
            require(sha256(temporary).equals(expectedDigest, ignoreCase = true)) {
                "A verificação SHA-256 do tModLoader falhou."
            }
            require(validTModLoaderZip(temporary)) {
                "O ZIP oficial não contém tModLoader.dll."
            }
            check(temporary.renameTo(destination)) { "Não foi possível guardar o pacote tModLoader." }
            return destination
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }

    private fun validTModLoaderZip(file: File): Boolean = runCatching {
        ZipFile(file).use { zip ->
            zip.getEntry("tModLoader.dll") != null ||
                zip.entries().asSequence().any { it.name.endsWith("/tModLoader.dll") }
        }
    }.getOrDefault(false)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
