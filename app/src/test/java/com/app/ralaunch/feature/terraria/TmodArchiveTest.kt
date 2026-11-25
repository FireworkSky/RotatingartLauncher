package com.app.ralaunch.feature.terraria

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TmodArchiveTest {
    @Test
    fun readsValidTmodContainer() {
        val file = createTmod()
        try {
            val metadata = TmodArchive.inspect(file)
            assertEquals("SimpleTest", metadata.name)
            assertEquals("1.0.0", metadata.version)
            assertEquals("2026.09.3.1", metadata.loaderVersion)
            assertEquals(1, metadata.entries)
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsOrdinaryZipAndInvalidMagic() {
        val file = File.createTempFile("not-a-mod-", ".tmod")
        try {
            file.writeBytes("PK\u0003\u0004not a TMOD".toByteArray())
            assertThrows(IllegalArgumentException::class.java) {
                TmodArchive.inspect(file)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsModifiedPayload() {
        val file = createTmod()
        try {
            val bytes = file.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x7f).toByte()
            file.writeBytes(bytes)
            assertThrows(IllegalArgumentException::class.java) {
                TmodArchive.inspect(file, verifyHash = true)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsMalformedLoaderVersion() {
        val file = createTmod(loaderVersion = "not-a-version")
        try {
            assertThrows(IllegalArgumentException::class.java) {
                TmodArchive.inspect(file)
            }
        } finally {
            file.delete()
        }
    }

    private fun createTmod(loaderVersion: String = "2026.09.3.1"): File {
        fun writeString(output: DataOutputStream, value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            var remaining = bytes.size
            while (remaining >= 0x80) {
                output.writeByte((remaining and 0x7f) or 0x80)
                remaining = remaining ushr 7
            }
            output.writeByte(remaining)
            output.write(bytes)
        }

        val payloadBytes = ByteArrayOutputStream()
        DataOutputStream(payloadBytes).use { payload ->
            writeString(payload, "SimpleTest")
            writeString(payload, "1.0.0")
            payload.writeInt(Integer.reverseBytes(1))
            writeString(payload, "Info")
            payload.writeInt(Integer.reverseBytes(1))
            payload.writeInt(Integer.reverseBytes(1))
            payload.writeByte(0x42)
        }
        val payload = payloadBytes.toByteArray()
        val digest = MessageDigest.getInstance("SHA-1").digest(payload)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.write("TMOD".toByteArray(Charsets.US_ASCII))
            writeString(output, loaderVersion)
            output.write(digest)
            output.write(ByteArray(256))
            output.writeInt(Integer.reverseBytes(payload.size))
            output.write(payload)
        }
        return File.createTempFile("valid-mod-", ".tmod").apply {
            writeBytes(bytes.toByteArray())
        }
    }
}
