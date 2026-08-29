package com.app.ralaunch.core.extractor

import android.annotation.SuppressLint
import android.system.Os
import com.app.ralaunch.strings.StringsResource.Strings
import kotlinx.coroutines.CancellationException
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.CompressorStreamFactory
import timber.log.Timber
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.outputStream

/** Extracts content-detected archives, 7z archives and already-opened zip files. */
class ArchiveExtractor private constructor(private val options: Options) {
    data class Options(
        val id: String,
        val sourcePath: Path?,
        val sourceZipFile: ZipFile?,
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
        private var sourceZipFile: ZipFile? = null
        private var sourceExtractionPrefix = Path("")
        private var destinationPath: Path? = null
        private var callback: ((Event) -> Unit)? = null

        fun id(id: String) = apply { this.id = id }

        fun from(sourcePath: Path) = apply { this.sourcePath = sourcePath }

        fun from(sourceZipFile: ZipFile) = apply { this.sourceZipFile = sourceZipFile }

        fun prefix(sourceExtractionPrefix: Path) = apply {
            this.sourceExtractionPrefix = sourceExtractionPrefix
        }

        fun to(destinationPath: Path) = apply { this.destinationPath = destinationPath }

        fun callback(callback: ((Event) -> Unit)?) = apply { this.callback = callback }

        fun build() = ArchiveExtractor(
            Options(
                id = id,
                sourcePath = sourcePath,
                sourceZipFile = sourceZipFile,
                sourceExtractionPrefix = sourceExtractionPrefix,
                destinationPath = requireNotNull(destinationPath) { "destinationPath is required" },
                callback = callback
            )
        )
    }

    init {
        require(options.sourcePath != null || options.sourceZipFile != null) { "sourcePath is required" }
    }

    private class ExtractionState(
        var processedEntries: Int = 0
    )

    fun extract(): Result = try {
        val root = options.destinationPath.toAbsolutePath().normalize().also { it.createDirectories() }
        val state = ExtractionState()

        val sourceZipFile = options.sourceZipFile
        if (sourceZipFile != null) {
            extractZipFileEntries(root, sourceZipFile, state)
        } else {
            val sourcePath = checkNotNull(options.sourcePath)
            sourcePath.inputStream().buffered().use { source ->
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
        }

        // emit success
        val message = Strings.extractor.complete
        options.callback?.invoke(Event.Progress(options.id, message, 1f, state.processedEntries))
        options.callback?.invoke(Event.Complete(options.id, message))
        Result.Success(options.destinationPath)
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        // emit failure
        val message = Strings.extractor.failed
        options.callback?.invoke(Event.Error(options.id, message, ex))
        Result.Failure(message, ex)
    }

    /** Extracts entries from an already-opened [ZipFile] (e.g. a zip embedded in another container). */
    private fun extractZipFileEntries(root: Path, zipFile: ZipFile, state: ExtractionState) {
        val entries = zipFile.entriesInPhysicalOrder.toList()
            .mapNotNull { entry -> entryTargetPath(root, entry)?.let { target -> entry to target } }
        val totalSize = entries.sumOf { it.first.size.coerceAtLeast(0) }
        var extractedSize = 0L

        for ((entry, target) in entries) {
            if (!zipFile.canReadEntryData(entry)) continue

            when {
                entry.isDirectory -> target.createDirectories()
                entry.isUnixSymlink -> extractSymlink(root, target, zipFile.getUnixSymlink(entry))
                else -> zipFile.getInputStream(entry).use { input ->
                    extractFile(target, input::read, entry.unixMode)
                }
            }

            state.processedEntries++
            extractedSize += entry.size.coerceAtLeast(0)

            // emit progress
            options.callback?.invoke(
                Event.Progress(
                    options.id,
                    Strings.extractor.inProgress.format(entry.name),
                    if (totalSize > 0) (extractedSize.toFloat() / totalSize).coerceIn(0f, 1f) else 1f,
                    state.processedEntries
                )
            )
        }
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
            .setPath(checkNotNull(options.sourcePath))
            .get()
            .use { archive ->
                val total = archive.entries.sumOf { it.size.coerceAtLeast(0) }
                var done = 0L
                generateSequence { archive.nextEntry }.forEach { entry ->
                    if (writeEntry(root, entry) { buffer ->
                            val readSize = archive.read(buffer)
                            if (readSize > 0) done += readSize
                            readSize
                        }
                    ) {
                        state.processedEntries++

                        // emit progress
                        options.callback?.invoke(
                            Event.Progress(
                                options.id,
                                Strings.extractor.inProgress.format(entry.name),
                                if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 1f,
                                state.processedEntries
                            )
                        )
                    }
                }
            }
    }

    private fun extractArchive(root: Path, input: java.io.InputStream, archiverName: String, state: ExtractionState) {
        val archive: ArchiveInputStream<*> =
            ArchiveStreamFactory().createArchiveInputStream(archiverName, input)
        val archiveSize = checkNotNull(options.sourcePath).fileSize().toFloat()
        archive.use {
            archive.forEach { entry ->
                if (!archive.canReadEntryData(entry)) return@forEach

                if (writeEntry(root, entry, archive::read)) {
                    state.processedEntries++

                    // emit progress
                    options.callback?.invoke(
                        Event.Progress(
                            options.id,
                            Strings.extractor.inProgress.format(entry.name),
                            (archive.bytesRead.toFloat() / archiveSize).coerceIn(0f, 1f),
                            state.processedEntries
                        )
                    )
                }
            }
        }
    }

    private fun writeEntry(
        root: Path,
        entry: ArchiveEntry,
        read: (ByteArray) -> Int
    ): Boolean {
        val target = entryTargetPath(root, entry) ?: return false

        when {
            entry.isDirectory ->
                target.createDirectories()

            (entry as? TarArchiveEntry)?.isSymbolicLink == true ->
                extractSymlink(root, target, entry.linkName)

            else ->
                extractFile(target, read, entryMode(entry))
        }

        return true
    }

    /** Resolves an archive entry to its destination path, or null when it is outside the extraction prefix. */
    private fun entryTargetPath(root: Path, entry: ArchiveEntry): Path? {
        val entryPath = Path(entry.name).normalize()
        val prefix = options.sourceExtractionPrefix.normalize()
        val relativePath = if (prefix.toString().isEmpty()) {
            entryPath
        } else {
            if (!entryPath.startsWith(prefix))
                return null
            prefix.relativize(entryPath)
        }
        if (relativePath == Path("."))
            return null

        val target = (root / relativePath).normalize()
        require(target.startsWith(root)) {
            "Archive entry escapes destination directory: $target"
        }
        return target
    }

    private fun entryMode(entry: ArchiveEntry): Int =
        (entry as? TarArchiveEntry)?.mode ?: (entry as? ZipArchiveEntry)?.unixMode ?: 0

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
    }
}
