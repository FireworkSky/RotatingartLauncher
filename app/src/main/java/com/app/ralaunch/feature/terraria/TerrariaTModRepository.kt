package com.app.ralaunch.feature.terraria

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.app.ralaunch.core.di.contract.IGameRepositoryServiceV3
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.koin.java.KoinJavaComponent

/** Copies validated .tmod containers into the user-data directory used by tModLoader. */
object TerrariaTModRepository {
    private const val MAX_MOD_BYTES = 256L * 1024L * 1024L

    suspend fun installFromUri(context: Context, gameId: String, uri: Uri): File =
        withContext(Dispatchers.IO) {
            val repository = KoinJavaComponent.get(IGameRepositoryServiceV3::class.java)
            val game = checkNotNull(repository.getById(gameId)) {
                "Instalação tModLoader não encontrada."
            }
            require(game.gameId == "tModLoader") {
                "O jogo selecionado não é uma instalação tModLoader."
            }

            val displayName = queryDisplayName(context, uri)
            require(displayName.endsWith(".tmod", ignoreCase = true)) {
                "Selecione um arquivo .tmod."
            }

            val storageRoot = File(repository.getGameGlobalStorageDirFull(), game.id)
            require(storageRoot.isDirectory) { "Pasta da instalação tModLoader não encontrada." }
            // GameLauncher sets HOME and XDG_DATA_HOME to /storage/emulated/0/RALauncher.
            // tModLoader 1.4.4 uses $XDG_DATA_HOME/Terraria/tModLoader/Mods.
            val externalHome = File(
                android.os.Environment.getExternalStorageDirectory(),
                "RALauncher"
            )
            val homeMods = File(externalHome, ".local/share/Terraria/tModLoader/Mods")
            val fallbackMods = File(storageRoot, ".local/share/Terraria/tModLoader/Mods")
            val modsDirectory = if (
                (externalHome.isDirectory || externalHome.mkdirs()) && externalHome.canWrite()
            ) homeMods else fallbackMods

            check(modsDirectory.isDirectory || modsDirectory.mkdirs()) {
                "Não foi possível criar a pasta Mods."
            }
            check(File(modsDirectory, "ModPacks").isDirectory ||
                File(modsDirectory, "ModPacks").mkdirs()) {
                "Não foi possível criar a pasta ModPacks."
            }

            val temporary = File.createTempFile(".bunny-mod-", ".tmod", modsDirectory)
            try {
                val input = checkNotNull(context.contentResolver.openInputStream(uri)) {
                    "Não foi possível abrir o arquivo selecionado."
                }
                input.use { source ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_MOD_BYTES) {
                                "O mod excede o limite de 256 MB."
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }

                // .tmod is a TMOD container, not a regular ZIP. Validate its own
                // header, table, Info entry, payload length and integrity hash.
                val metadata = TmodArchive.inspect(temporary, verifyHash = true)
                val destination = File(modsDirectory, "${metadata.name}.tmod")
                moveReplacing(temporary, destination)

                // tModLoader reads this list to decide which installed mods are enabled.
                val enabledFile = File(modsDirectory, "enabled.json")
                val enabled = if (enabledFile.isFile) {
                    runCatching { JSONArray(enabledFile.readText()) }.getOrElse { JSONArray() }
                } else JSONArray()
                val ids = (0 until enabled.length())
                    .mapNotNull { enabled.optString(it).takeIf(String::isNotBlank) }
                    .toMutableSet()
                ids.add(metadata.name)
                val stagedEnabled = File.createTempFile(".bunny-enabled-", ".json", modsDirectory)
                try {
                    stagedEnabled.writeText(JSONArray(ids.sorted()).toString(2))
                    moveReplacing(stagedEnabled, enabledFile)
                } finally {
                    stagedEnabled.delete()
                }

                destination
            } finally {
                temporary.delete()
            }
        }

    private fun moveReplacing(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index).orEmpty()
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "mod.tmod"
    }
}
