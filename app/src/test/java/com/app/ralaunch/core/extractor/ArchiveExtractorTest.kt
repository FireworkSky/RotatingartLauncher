package com.app.ralaunch.core.extractor

import android.app.Application
import com.app.ralaunch.strings.StringsResource
import com.app.ralaunch.strings.generated.En
import com.app.ralaunch.strings.generated.LocaleStrings
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.div

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ArchiveExtractorTest {
    private lateinit var tempDir: Path
    private lateinit var previousStrings: LocaleStrings

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("archive-extractor-test")
        previousStrings = StringsResource.Strings
        StringsResource.Strings = En
    }

    @After
    fun tearDown() {
        StringsResource.Strings = previousStrings
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun extractsConfiguredPrefixAndEmitsConfiguredId() {
        val archive = createZip(
            "assets/patches/patch.txt" to "patch",
            "assets/other.txt" to "other"
        )
        val destination = tempDir / "destination"
        val events = mutableListOf<ArchiveExtractor.Event>()

        val result = ArchiveExtractor.builder()
            .id("patch-install")
            .sourcePath(archive)
            .sourceExtractionPrefix(Path.of("assets/patches"))
            .destinationPath(destination)
            .callback { events += it }
            .build()
            .extract()

        assertEquals(ArchiveExtractor.Result.Success(destination), result)
        assertEquals("patch", Files.readString(destination / "patch.txt"))
        assertFalse(Files.exists(destination / "other.txt"))
        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.id == "patch-install" })
        assertEquals(
            listOf("Extracting: assets/patches/patch.txt", "Extraction complete"),
            events.filterIsInstance<ArchiveExtractor.Event.Progress>()
                .filter { it.processedEntries > 0 }
                .map { it.message }
        )
        assertEquals(
            "Extraction complete",
            (events.last() as ArchiveExtractor.Event.Complete).message
        )
    }

    @Test
    fun detectsZipWithoutRecognizedExtension() {
        val archive = createZip("nested/file.txt" to "content")
        val destination = tempDir / "destination"

        val result = ArchiveExtractor.builder()
            .sourcePath(archive)
            .destinationPath(destination)
            .build()
            .extract()

        assertTrue(result is ArchiveExtractor.Result.Success)
        assertEquals("content", Files.readString(destination / "nested/file.txt"))
    }

    @Test
    fun detectsTarGzipAndXzByContent() {
        val archives = listOf(
            createTar(TarEntry("nested/file.txt", "tar")),
            createWrappedTar({ GzipCompressorOutputStream(it) }, TarEntry("nested/file.txt", "gzip")),
            createWrappedTar({ XZCompressorOutputStream(it) }, TarEntry("nested/file.txt", "xz"))
        )

        archives.forEachIndexed { index, archive ->
            val destination = tempDir / "destination-$index"
            val result = ArchiveExtractor.builder()
                .sourcePath(archive)
                .destinationPath(destination)
                .build()
                .extract()

            assertTrue(result is ArchiveExtractor.Result.Success)
            assertEquals(
                listOf("tar", "gzip", "xz")[index],
                Files.readString(destination / "nested/file.txt")
            )
        }
    }

    @Test
    fun extractsTarPrefixAndCountsProcessedEntries() {
        val archive = createTar(
            TarEntry("runtime/nested/file.txt", "content"),
            TarEntry("runtime2/ignored.txt", "ignored")
        )
        val destination = tempDir / "destination"
        val events = mutableListOf<ArchiveExtractor.Event>()

        val result = ArchiveExtractor.builder()
            .sourcePath(archive)
            .sourceExtractionPrefix(Path("runtime"))
            .destinationPath(destination)
            .callback { events += it }
            .build()
            .extract()

        assertTrue(result is ArchiveExtractor.Result.Success)
        assertEquals("content", Files.readString(destination / "nested/file.txt"))
        assertFalse(Files.exists(destination / "ignored.txt"))
        assertEquals(
            1,
            events.filterIsInstance<ArchiveExtractor.Event.Progress>()
                .first { it.processedEntries == 1 }
                .processedEntries
        )

    }

    @Test
    fun extractsBundledRuntimeAssetByContent() {
        val source = tempDir / "runtime.data"
        ArchiveExtractor.copyAssetToFile(RuntimeEnvironment.getApplication(), "dotnet.tar.xz", source)
        val destination = tempDir / "runtime"

        val result = ArchiveExtractor.builder()
            .sourcePath(source)
            .destinationPath(destination)
            .build()
            .extract()

        assertTrue(result is ArchiveExtractor.Result.Success)
        assertTrue(Files.exists(destination / "shared/Microsoft.NETCore.App/10.0.4"))
    }

    @Test
    fun preservesTarExecutableMode() {
        val archive = createTar(TarEntry("run.sh", "run", mode = 0x40))
        val destination = tempDir / "destination"

        val result = ArchiveExtractor.builder()
            .sourcePath(archive)
            .destinationPath(destination)
            .build()
            .extract()

        assertTrue(result is ArchiveExtractor.Result.Success)
        assertTrue((destination / "run.sh").toFile().canExecute())
    }

    @Test
    fun extractsSafeTarSymlink() {
        val archive = createTar(
            TarEntry("target.txt", "content"),
            TarEntry("link", linkName = "target.txt")
        )
        val destination = tempDir / "destination"

        val result = ArchiveExtractor.builder()
            .sourcePath(archive)
            .destinationPath(destination)
            .build()
            .extract()

        assertTrue(result is ArchiveExtractor.Result.Success)
        assertEquals("content", Files.readString(destination / "link"))
    }

    @Test
    fun reportsTenProcessedEntries() {
        val archive = createTar(*(1..10).map { TarEntry("file-$it.txt", "$it") }.toTypedArray())
        val events = mutableListOf<ArchiveExtractor.Event>()

        ArchiveExtractor.builder()
            .sourcePath(archive)
            .destinationPath(tempDir / "destination")
            .callback { events += it }
            .build()
            .extract()
        assertEquals(
            (1..10).toList(),
            events.filterIsInstance<ArchiveExtractor.Event.Progress>()
                .filter { it.message.startsWith("Extracting:") && it.processedEntries > 0 }
                .map { it.processedEntries }
                .distinct()
        )
        assertEquals(
            10,
            events.filterIsInstance<ArchiveExtractor.Event.Progress>().last().processedEntries
        )
    }

    @Test
    fun rejectsTraversalEntry() {
        val outside = tempDir / "outside.txt"
        val archive = createZip("../outside.txt" to "outside")
        val destination = tempDir / "destination"
        val events = mutableListOf<ArchiveExtractor.Event>()
        val result = ArchiveExtractor.builder()
            .sourcePath(archive)
            .destinationPath(destination)
            .callback { events += it }
            .build()
            .extract()

        assertTrue(result is ArchiveExtractor.Result.Failure)
        result as ArchiveExtractor.Result.Failure
        assertTrue(result.cause.message.orEmpty().contains("destination directory"))
        assertEquals("Extraction failed", result.message)
        assertEquals(
            "Extraction failed",
            (events.single() as ArchiveExtractor.Event.Error).message
        )
        assertFalse(Files.exists(outside))
    }

    @Test
    fun rejectsRelativeTarEntriesThatEscapeExtractionDirectory() {
        listOf("../../outside.txt", "nested/../../../outside.txt").forEach { entryName ->
            val destination = tempDir / entryName.hashCode().toString()
            val result = ArchiveExtractor.builder()
                .sourcePath(createTar(TarEntry(entryName, "content")))
                .destinationPath(destination)
                .build()
                .extract()

            assertTrue(result is ArchiveExtractor.Result.Failure)
            result as ArchiveExtractor.Result.Failure
            assertTrue(result.cause.message.orEmpty().contains("destination directory"))
            assertFalse(Files.exists(tempDir / "outside.txt"))
        }
    }

    @Test
    fun rejectsAbsolutePathEntry() {
        val absolute = (tempDir / "absolute.txt").toAbsolutePath()
        val archive = createZip(absolute.toString() to "absolute")
        val destination = tempDir / "destination"

        val result = ArchiveExtractor.builder()
            .sourcePath(archive)
            .destinationPath(destination)
            .build()
            .extract()

        assertTrue(result is ArchiveExtractor.Result.Failure)
        result as ArchiveExtractor.Result.Failure
        assertTrue(result.cause.message.orEmpty().contains("destination directory"))
        assertFalse(Files.exists(absolute))
    }

    @Test
    fun rejectsSymlinkOutsideDestination() {
        val destination = tempDir / "destination"
        val events = mutableListOf<ArchiveExtractor.Event>()
        val result = ArchiveExtractor.builder()
            .sourcePath(createTar(TarEntry("links/outside", linkName = "../../outside.txt")))
            .destinationPath(destination)
            .callback { events += it }
            .build()
            .extract()

        assertTrue(result is ArchiveExtractor.Result.Failure)
        result as ArchiveExtractor.Result.Failure
        assertTrue(result.cause.message.orEmpty().contains("destination directory"))
        assertEquals("Extraction failed", (events.single() as ArchiveExtractor.Event.Error).message)
        assertFalse(Files.exists(tempDir / "outside.txt"))
    }

    private data class TarEntry(
        val name: String,
        val content: String = "",
        val linkName: String? = null,
        val mode: Int = 0
    )

    private fun createZip(vararg entries: Pair<String, String>): Path {
        val archive = Files.createTempFile(tempDir, "archive", "data")
        ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return archive
    }

    private fun createTar(vararg entries: TarEntry): Path =
        createWrappedTar({ it }, *entries)

    private fun createWrappedTar(wrapper: (OutputStream) -> OutputStream, vararg entries: TarEntry): Path {
        val archive = Files.createTempFile(tempDir, "archive", "data")
        Files.newOutputStream(archive).use { fileOutput ->
            wrapper(fileOutput).use { output ->
                TarArchiveOutputStream(output).use { tar ->
                    entries.forEach { entry ->
                        val tarEntry = if (entry.linkName == null) {
                            TarArchiveEntry(entry.name).apply {
                                size = entry.content.toByteArray().size.toLong()
                                mode = entry.mode
                            }
                        } else {
                            TarArchiveEntry(entry.name, TarConstants.LF_SYMLINK).apply {
                                linkName = entry.linkName
                            }
                        }
                        tar.putArchiveEntry(tarEntry)
                        if (entry.linkName == null) tar.write(entry.content.toByteArray())
                        tar.closeArchiveEntry()
                    }
                }
            }
        }
        return archive
    }
}
