package com.app.ralaunch.feature.terraria.mods

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/**
 * Secure staging/catalog for Bunny Evolution .bmod packages.
 * Staging is NOT runtime loading: Android 1.4.4.9 bridge is not implemented.
 * Assemblies are catalogued but never executed or injected by this class.
 */
object BunnyModRepository {
    private const val MANIFEST = "manifest.json"
    private const val MAX_ENTRIES = 2_000
    private const val MAX_UNCOMPRESSED_BYTES = 256L * 1024L * 1024L
    private const val MAX_MANIFEST_BYTES = 64 * 1024L
    private val idPattern = Regex("^[a-z0-9][a-z0-9_.-]{1,63}$")

    data class ModInfo(val id: String, val name: String, val version: String, val author: String,
        val gameVersion: String, val loaderApi: Int, val description: String,
        val files: List<String>, val enabled: Boolean, val runtimeReady: Boolean = false)

    sealed interface Result { data class Success(val mod: ModInfo) : Result; data class Failure(val reason: String) : Result }
    private fun root(context: Context) = File(context.filesDir, "bunny/mods").apply { mkdirs() }
    private fun enabledFile(context: Context) = File(root(context), "enabled.json")

    @Synchronized
    fun importPackage(context: Context, archive: File): Result {
        if (!archive.isFile || archive.extension.lowercase() != "bmod") return Result.Failure("Selecione um pacote .bmod válido.")
        val temp = File(root(context), ".import-${System.nanoTime()}")
        try {
            ZipFile(archive).use { zip ->
                val entries = zip.entries(); var count = 0; var total = 0L; var manifest: JSONObject? = null
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement(); count++
                    if (count > MAX_ENTRIES) return Result.Failure("Pacote contém arquivos demais.")
                    val path = entry.name
                    if (path.startsWith("/") || path.startsWith("\\") || path.contains('\\') || path.split('/').any { it == ".." })
                        return Result.Failure("Caminho inseguro no pacote: $path")
                    if (entry.isDirectory) continue
                    if (entry.size > 0) { total += entry.size; if (total > MAX_UNCOMPRESSED_BYTES) return Result.Failure("Pacote excede 256 MB descompactados.") }
                    if (path == MANIFEST) {
                        if (entry.size > MAX_MANIFEST_BYTES) return Result.Failure("manifest.json muito grande.")
                        val raw = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        manifest = JSONObject(raw)
                    }
                }
                val json = manifest ?: return Result.Failure("manifest.json não encontrado.")
                val id = json.optString("id")
                if (!idPattern.matches(id)) return Result.Failure("ID inválido no manifest.json.")
                val name = json.optString("name").takeIf { it.isNotBlank() } ?: return Result.Failure("Nome do mod ausente.")
                val version = json.optString("version").takeIf { it.isNotBlank() } ?: return Result.Failure("Versão do mod ausente.")
                val gameVersion = json.optString("gameVersion", "1.4.4.9")
                val loaderApi = json.optInt("loaderApi", 1)
                if (gameVersion != "1.4.4.9") return Result.Failure("Este pacote declara Terraria $gameVersion; alvo atual: 1.4.4.9.")
                if (loaderApi != 1) return Result.Failure("API Bunny incompatível: $loaderApi.")
                temp.mkdirs(); val files = mutableListOf<String>()
                val all = zip.entries()
                while (all.hasMoreElements()) {
                    val entry = all.nextElement(); if (entry.isDirectory) continue
                    val out = File(temp, entry.name).canonicalFile
                    if (!out.toPath().startsWith(temp.canonicalFile.toPath())) return Result.Failure("Entrada fora da pasta do mod.")
                    out.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> out.outputStream().use { output ->
                        val buffer = ByteArray(8192); var copied = 0L
                        while (true) { val n = input.read(buffer); if (n < 0) break; copied += n
                            if (copied > MAX_UNCOMPRESSED_BYTES) throw IllegalArgumentException("Arquivo excede limite permitido.")
                            output.write(buffer, 0, n)
                        }
                    } }
                    files += entry.name
                }
                val destination = File(root(context), id)
                if (destination.exists()) destination.deleteRecursively()
                if (!temp.renameTo(destination)) return Result.Failure("Não foi possível instalar o pacote.")
                val info = parseInfo(destination, enabledIds(context)) ?: return Result.Failure("Manifesto instalado inválido.")
                return Result.Success(info.copy(files = files))
            }
        } catch (e: Exception) { return Result.Failure(e.message ?: "Falha ao ler pacote.") }
        finally { if (temp.exists()) temp.deleteRecursively() }
    }

    @Synchronized fun list(context: Context): List<ModInfo> {
        val enabled = enabledIds(context)
        return root(context).listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.mapNotNull { parseInfo(it, enabled) }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    @Synchronized fun setEnabled(context: Context, id: String, enabled: Boolean): Boolean {
        if (!idPattern.matches(id) || parseInfo(File(root(context), id), enabledIds(context)) == null) return false
        val ids = enabledIds(context).toMutableSet(); if (enabled) ids.add(id) else ids.remove(id)
        enabledFile(context).writeText(JSONObject().put("enabled", org.json.JSONArray(ids.sorted())).toString())
        return true
    }

    @Synchronized fun remove(context: Context, id: String): Boolean {
        if (!idPattern.matches(id)) return false
        val base = root(context).canonicalFile; val folder = File(base, id).canonicalFile
        if (!folder.toPath().startsWith(base.toPath())) return false
        val removed = folder.deleteRecursively(); setEnabled(context, id, false); return removed
    }

    private fun enabledIds(context: Context): Set<String> = try {
        val array = JSONObject(enabledFile(context).takeIf { it.isFile }?.readText() ?: "{}").optJSONArray("enabled") ?: return emptySet()
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(idPattern::matches) }.toSet()
    } catch (_: Exception) { emptySet() }

    private fun parseInfo(folder: File, enabled: Set<String>): ModInfo? = try {
        val file = File(folder, MANIFEST); if (!file.isFile || file.length() > MAX_MANIFEST_BYTES) return null
        val json = JSONObject(file.readText()); val id = json.optString("id")
        if (!idPattern.matches(id) || id != folder.name) return null
        val listed = folder.walkTopDown().filter { it.isFile }.map { it.relativeTo(folder).invariantSeparatorsPath }.toList()
        ModInfo(id, json.optString("name", id), json.optString("version", "0"), json.optString("author", "Desconhecido"),
            json.optString("gameVersion", "unknown"), json.optInt("loaderApi", 1), json.optString("description", ""),
            listed, id in enabled, runtimeReady = false)
    } catch (_: Exception) { null }
}