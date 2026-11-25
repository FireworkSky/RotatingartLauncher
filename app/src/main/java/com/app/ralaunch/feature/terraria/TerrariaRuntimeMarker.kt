package com.app.ralaunch.feature.terraria

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Stores the exact tModLoader release beside the installed assembly.
 * This prevents importing a mod built for a different loader release.
 */
internal object TerrariaRuntimeMarker {
    private const val FILE_NAME = "bunny-runtime.properties"
    private val versionPattern = Regex("""(?:\d+\.){1,3}\d+""")

    fun write(storageRoot: File, assemblyRelativePath: String, version: String): File {
        require(versionPattern.matches(version)) { "Versão do tModLoader inválida." }
        val assembly = resolveAssembly(storageRoot, assemblyRelativePath)
        val directory = checkNotNull(assembly.parentFile)
        val marker = File(directory, FILE_NAME)
        val temporary = File.createTempFile(".bunny-runtime-", ".properties", directory)
        try {
            val properties = Properties().apply {
                setProperty("loaderVersion", version)
                setProperty("terrariaVersion", "1.4.4.9")
                setProperty("source", "terraria-mobile")
            }
            temporary.outputStream().use { properties.store(it, "Bunny Evolution runtime metadata") }
            try {
                Files.move(
                    temporary.toPath(), marker.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), marker.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
        return marker
    }

    fun read(storageRoot: File, assemblyRelativePath: String): String {
        val assembly = resolveAssembly(storageRoot, assemblyRelativePath)
        val marker = File(checkNotNull(assembly.parentFile), FILE_NAME)
        require(marker.isFile) {
            "Não foi possível confirmar a versão do tModLoader instalado. Reinstale pelo Bunny antes de importar mods."
        }
        val properties = Properties()
        marker.inputStream().use(properties::load)
        val version = properties.getProperty("loaderVersion").orEmpty()
        require(versionPattern.matches(version)) { "O registro da versão do tModLoader está inválido." }
        return version
    }

    private fun resolveAssembly(storageRoot: File, assemblyRelativePath: String): File {
        val root = storageRoot.canonicalFile.toPath()
        val assembly = File(storageRoot, assemblyRelativePath).canonicalFile
        require(assembly.toPath().startsWith(root)) {
            "O caminho do tModLoader está fora da instalação."
        }
        require(assembly.isFile && assembly.name == "tModLoader.dll") {
            "A instalação tModLoader do Bunny está incompleta."
        }
        return assembly
    }
}
