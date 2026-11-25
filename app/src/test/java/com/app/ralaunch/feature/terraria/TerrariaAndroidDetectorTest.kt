package com.app.ralaunch.feature.terraria

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TerrariaAndroidDetectorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun apk(name: String, entries: Map<String, ByteArray>): File {
        val file = File(temporaryFolder.root, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun detectsUnityPayloadAcrossBaseAndSplitApks() {
        val base = apk("base.apk", mapOf(
            "assets/bin/Data/data.unity3d" to "UnityFS\u0000payload".toByteArray(Charsets.US_ASCII)
        ))
        val split = apk("split_config.arm64.apk", mapOf(
            "assets/bin/Data/resources.resource" to byteArrayOf(1, 2, 3)
        ))

        val result = TerrariaAndroidDetector.inspectArchives(listOf(base, split))

        assertTrue(result.hasUnityBundle)
        assertTrue(result.hasResourceStream)
        assertTrue(result.conversionSourceAvailable)
        assertEquals(2, result.archiveCount)
        assertEquals(2, result.readableArchiveCount)
    }

    @Test
    fun rejectsWrongBundleSignature() {
        val file = apk("base.apk", mapOf(
            "assets/bin/Data/data.unity3d" to "NOTUNITY".toByteArray(),
            "assets/bin/Data/resources.resource" to byteArrayOf(1)
        ))

        val result = TerrariaAndroidDetector.inspectArchives(listOf(file))

        assertFalse(result.hasUnityBundle)
        assertFalse(result.conversionSourceAvailable)
    }

    @Test
    fun rejectsMissingResourceStream() {
        val file = apk("base.apk", mapOf(
            "assets/bin/Data/data.unity3d" to "UnityFS\u0000payload".toByteArray(Charsets.US_ASCII)
        ))

        val result = TerrariaAndroidDetector.inspectArchives(listOf(file))

        assertTrue(result.hasUnityBundle)
        assertFalse(result.hasResourceStream)
        assertFalse(result.conversionSourceAvailable)
    }
}
