package com.app.ralaunch.feature.terraria

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class TerrariaRuntimeMarkerTest {
    @Test
    fun writesAndReadsExactRuntimeVersion() {
        val root = Files.createTempDirectory("bunny-runtime-marker-").toFile()
        try {
            val assembly = File(root, "game/tModLoader/tModLoader.dll")
            assertTrue(assembly.parentFile!!.mkdirs())
            assembly.writeBytes(byteArrayOf(1, 2, 3))

            val marker = TerrariaRuntimeMarker.write(root, "game/tModLoader/tModLoader.dll", "2026.09.3.1")

            assertTrue(marker.isFile)
            assertEquals("2026.09.3.1",
                TerrariaRuntimeMarker.read(root, "game/tModLoader/tModLoader.dll"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsAssemblyPathOutsideStorageRoot() {
        val root = Files.createTempDirectory("bunny-runtime-root-").toFile()
        val outside = Files.createTempDirectory("bunny-runtime-outside-").toFile()
        try {
            val assembly = File(outside, "tModLoader.dll").apply { writeBytes(byteArrayOf(1)) }
            assertThrows(IllegalArgumentException::class.java) {
                TerrariaRuntimeMarker.read(root, "../${outside.name}/${assembly.name}")
            }
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test
    fun rejectsInvalidRuntimeVersion() {
        val root = Files.createTempDirectory("bunny-runtime-invalid-").toFile()
        try {
            val assembly = File(root, "tModLoader.dll").apply { writeBytes(byteArrayOf(1)) }
            assertThrows(IllegalArgumentException::class.java) {
                TerrariaRuntimeMarker.write(root, assembly.name, "not-a-version")
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
