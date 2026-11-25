package com.app.ralaunch.feature.terraria

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.app.ralaunch.core.di.contract.IGameRepositoryServiceV3
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent

/** Copies complete .tmod archives into the data directory used by tModLoader. */
object TerrariaTModRepository {
    private const val MAX_MOD_BYTES = 256L * 1024L * 1024L

    suspend fun installFromUri(context: Context, gameId: String, uri: Uri): File =
        withContext(Dispatchers.IO) {
            val repository = KoinJavaComponent.get(IGameRepositoryServiceV3::class.java)
            val game = checkNotNull(repository.getById(gameId)) { "Instalação tModLoader não encontrada." }
            require(game.gameId == "tModLoader") { "O jogo selecionado não é uma instalação tModLoader." }

            val displayName = queryDisplayName(context, uri)
            require(displayName.endsWith(".tmod", ignoreCase = true)) {
                "Selecione um arquivo .tmod."
            }
            val safeName = displayName.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            require(safeName.isNotBlank() && safeName.endsWith(".tmod", ignoreCase = true)) {
                "Nome de mod inválido."
            }

            val storageRoot = File(repository.getGameGlobalStorageDirFull(), game.id)
            require(storageRoot.isDirectory) { "Pasta da instalação tModLoader não encontrada." }
            val externalHome = File(Environment.getExternalStorageDirectory(), "RALauncher")
            val externalMods = File(externalHome, "Terraria/tModLoader/Mods")
            val fallbackMods = File(storageRoot, "game/tModLoader/Terraria/tModLoader/Mods")
            val modsDirectory = if (
                (externalHome.isDirectory || externalHome.mkdirs()) &&
                externalHome.canWrite() &&
                (externalMods.isDirectory || externalMods.mkdirs()) &&
                externalMods.canWrite()
            ) externalMods else fallbackMods
            check(modsDirectory.isDirectory || modsDirectory.mkdirs()) {
                "Não foi possível criar a pasta Mods."
            }
            File(modsDirectory, "ModPacks").mkdirs()

            val temporary = File.createTempFile(".bunny-mod-", ".tmp", modsDirectory)
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
                            require(total <= MAX_MOD_BYTES) { "O mod excede o limite de 256 MB." }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                require(ZipFile(temporary).use { it.entries().hasMoreElements() }) {
                    "O arquivo .tmod não é um pacote ZIP válido."
                }
                val destination = File(modsDirectory, safeName)
                if (destination.exists()) destination.delete()
                check(temporary.renameTo(destination)) { "Não foi possível instalar o mod." }
                destination
            } finally {
                temporary.delete()
            }
        }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index).orEmpty()
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "mod.tmod"
    }
}
