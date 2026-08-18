package com.app.ralaunch.core.extractor

import android.annotation.SuppressLint
import android.content.Context
import android.system.Os
import com.app.ralaunch.strings.StringsResource.Strings
import kotlinx.coroutines.CancellationException
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.compressors.CompressorStreamFactory
import timber.log.Timber
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.outputStream

/** Extracts content-detected archives and 7z archives. */
class ArchiveExtractor private constructor(private val options: Options) {
    data class Options(
        val id: String,
        val sourcePath: Path,
        val sourceExtractionPrefix: Path,
        val destinationPath: Path,
        val callback: ((Event) -> Unit)?
    )

    sealed interface Event {
        val id: String
        val message: String

        data class Progress(
            override val id: String,
            override val message: String,
            val progress: Float,
            val processedEntries: Int = 0
        ) : Event

        data class Complete(
            override val id: String,
            override val message: String
        ) : Event

        data class Error(
            override val id: String,
            override val message: String,
            val cause: Throwable
        ) : Event
    }

    sealed class Result {
        data class Success(val destinationPath: Path) : Result()
        data class Failure(val message: String, val cause: Throwable) : Result()
    }

    class Builder {
        private var id = ""
        private var sourcePath: Path? = null
        private var sourceExtractionPrefix = Path("")
        private var destinationPath: Path? = null
        private var callback: ((Event) -> Unit)? = null

        fun id(id: String) = apply { this.id = id }

        fun sourcePath(sourcePath: Path) = apply { this.sourcePath = sourcePath }

        fun sourceExtractionPrefix(sourceExtractionPrefix: Path) = apply {
            this.sourceExtractionPrefix = sourceExtractionPrefix
        }

        fun destinationPath(destinationPath: Path) = apply { this.destinationPath = destinationPath }

        fun callback(callback: ((Event) -> Unit)?) = apply { this.callback = callback }

        fun build() = ArchiveExtractor(
            Options(
                id = id,
                sourcePath = requireNotNull(sourcePath) { "sourcePath is required" },
                sourceExtractionPrefix = sourceExtractionPrefix,
                destinationPath = requireNotNull(destinationPath) { "destinationPath is required" },
                callback = callback
            )
        )
    }

    private class ExtractionState(
        var processedEntries: Int = 0
    )

    fun extract(): Result = try {
        val root = options.destinationPath.toAbsolutePath().normalize().also { it.createDirectories() }
        val state = ExtractionState()

        options.sourcePath.inputStream().buffered().use { source ->
            val compressorName = runCatching {
                CompressorStreamFactory.detect(source)
            }.getOrNull()

            if (compressorName != null) { // if is tar.gz / tar.xz ...
                CompressorStreamFactory()
                    .createCompressorInputStream(compressorName, source)
                    .buffered()
                    .use { decompressed ->
                        extractDetectedArchive(root, decompressed, state, supportsSevenZ = false)
                    }
            } else { // if is zip / 7z ...
                extractDetectedArchive(root, source, state)
            }
        }

        val message = Strings.extractor.complete
        options.callback?.invoke(Event.Progress(options.id, message, 1f, state.processedEntries))
        options.callback?.invoke(Event.Complete(options.id, message))
        Result.Success(options.destinationPath)
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        val message = Strings.extractor.failed
        options.callback?.invoke(Event.Error(options.id, message, ex))
        Result.Failure(message, ex)
    }

    private fun extractDetectedArchive(
        root: Path,
        input: java.io.InputStream,
        state: ExtractionState,
        supportsSevenZ: Boolean = true
    ) {
        val archiverName = ArchiveStreamFactory.detect(input)
        if (archiverName == ArchiveStreamFactory.SEVEN_Z) {
            require(supportsSevenZ) { "7z archives wrapped in a compressor are not supported" }
            extractSevenZip(root, state)
            return
        }

        extractArchive(root, input, archiverName, state)
    }

    private fun extractSevenZip(root: Path, state: ExtractionState) {
        SevenZFile.builder()
            .setPath(options.sourcePath)
            .get()
            .use { archive ->
                generateSequence { archive.nextEntry }.forEach { entry ->
                    writeEntry(root, entry, archive::read, state)
                }
            }
    }

    private fun extractArchive(root: Path, input: java.io.InputStream, archiverName: String, state: ExtractionState) {
        val archive: ArchiveInputStream<*> =
            ArchiveStreamFactory().createArchiveInputStream(archiverName, input)
        archive.use {
            archive.forEach { entry ->
                if (!archive.canReadEntryData(entry)) return@forEach
                writeEntry(root, entry, archive::read, state)
            }
        }
    }

    private fun writeEntry(
        root: Path,
        entry: ArchiveEntry,
        read: (ByteArray) -> Int,
        state: ExtractionState
    ) {
        val entryPath = Path(entry.name).normalize()
        val prefix = options.sourceExtractionPrefix.normalize()
        val relativePath = if (prefix.toString().isEmpty()) {
            entryPath
        } else {
            if (!entryPath.startsWith(prefix))
                return
            prefix.relativize(entryPath)
        }
        if (relativePath == Path("."))
            return

        val target = (root / relativePath).normalize()
        require(target.startsWith(root)) {
            "Archive entry escapes destination directory: $target"
        }

        when {
            entry.isDirectory ->
                target.createDirectories()

            (entry as? TarArchiveEntry)?.isSymbolicLink == true ->
                extractSymlink(root, target, entry.linkName)

            else ->
                extractFile(target, read, (entry as? TarArchiveEntry)?.mode ?: 0)
        }

        state.processedEntries++
        options.callback?.invoke(
            Event.Progress(
                options.id,
                Strings.extractor.inProgress.format(entry.name),
                0f,
                state.processedEntries
            )
        )
    }

    private fun extractSymlink(root: Path, target: Path, linkTarget: String) {
        val resolvedTarget = requireNotNull(target.parent).resolve(linkTarget).normalize()
        require(resolvedTarget.startsWith(root)) {
            "Archive symlink escapes destination directory: $resolvedTarget"
        }
        target.parent?.createDirectories()
        target.deleteIfExists()

        try {
            Os.symlink(linkTarget, target.toString())
            check(target.isSymbolicLink()) { "Symlink was not created" }
        } catch (ex: Exception) {
            Timber.w(ex, "Failed to create symlink: %s", target)
            if (resolvedTarget.exists()) {
                try {
                    resolvedTarget.copyTo(target, overwrite = true)
                } catch (copyEx: Exception) {
                    Timber.w(copyEx, "Failed to copy symlink target: %s", resolvedTarget)
                }
            }
        }
    }

    @SuppressLint("SetWorldReadable")
    private fun extractFile(target: Path, read: (ByteArray) -> Int, mode: Int) {
        target.parent?.createDirectories()
        target.outputStream().buffered().use { output ->
            val buffer = ByteArray(BUFFER_SIZE)
            var count: Int
            while (read(buffer).also { count = it } != -1) {
                if (count > 0) output.write(buffer, 0, count)
            }
        }
        target.toFile().apply {
            if ((mode and 0x40) != 0)
                setExecutable(true, false)
            setReadable(true, false)
        }
    }

    companion object {
        private const val BUFFER_SIZE = 8192

        fun builder() = Builder()

        @JvmStatic
        fun copyAssetToFile(context: Context, assetFileName: String, targetFile: Path) {
            context.assets.open(assetFileName).use { input ->
                targetFile.outputStream().buffered().use { output ->
                    input.copyTo(output, BUFFER_SIZE)
                }
            }
        }
    }
}
