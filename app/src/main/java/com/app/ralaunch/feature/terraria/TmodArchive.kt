package com.app.ralaunch.feature.terraria

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Reads the public TMOD container header and file table.
 *
 * A .tmod file is a TMOD container, not an ordinary ZIP archive. This parser
 * validates its boundaries and SHA-1 payload before the launcher enables it.
 * It never extracts or executes assemblies.
 */
internal object TmodArchive {
    data class Metadata(
        val name: String,
        val version: String,
        val loaderVersion: String,
        val entries: Int,
    )

    fun inspect(file: File, verifyHash: Boolean = true): Metadata =
        RandomAccessFile(file, "r").use { input ->
            fun int32(): Int = Integer.reverseBytes(input.readInt())
            fun text(): String {
                var length = 0
                var shift = 0
                while (true) {
                    val next = input.readUnsignedByte()
                    require(shift < 28 || next <= 7) { "Comprimento de texto TMOD inválido." }
                    length = length or ((next and 0x7f) shl shift)
                    if (next and 0x80 == 0) break
                    shift += 7
                    require(shift <= 28) { "Comprimento de texto TMOD inválido." }
                }
                require(length in 0..262_144) { "Texto TMOD excede o limite." }
                val bytes = ByteArray(length)
                input.readFully(bytes)
                return bytes.toString(Charsets.UTF_8)
            }

            require(file.length() >= 300L) { "Arquivo TMOD muito pequeno." }
            val magic = ByteArray(4)
            input.readFully(magic)
            require(String(magic, Charsets.US_ASCII) == "TMOD") {
                "O arquivo selecionado não é um pacote .tmod válido."
            }

            val loaderVersion = text()
            require(loaderVersion.isNotBlank()) { "Versão do tModLoader ausente no mod." }
            val expectedHash = ByteArray(20)
            input.readFully(expectedHash)
            input.seek(input.filePointer + 256)
            val payloadLength = int32()
            val payloadStart = input.filePointer
            require(payloadLength >= 0 && input.length() - payloadStart == payloadLength.toLong()) {
                "Tamanho interno do pacote TMOD não corresponde ao arquivo."
            }

            if (verifyHash) {
                val digest = MessageDigest.getInstance("SHA-1")
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
                require(MessageDigest.isEqual(expectedHash, digest.digest())) {
                    "A verificação de integridade SHA-1 do mod falhou."
                }
            }

            input.seek(payloadStart)
            val name = text()
            require(name.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,127}"))) {
                "Nome interno do mod inválido."
            }
            val version = text()
            require(version.isNotBlank()) { "Versão do mod ausente." }
            val count = int32()
            require(count in 1..100_000) { "Tabela de arquivos TMOD inválida." }

            var packedTotal = 0L
            var hasInfo = false
            repeat(count) {
                val entryName = text()
                val unpacked = int32()
                val packed = int32()
                require(entryName.isNotBlank() && unpacked >= 0 && packed >= 0) {
                    "Entrada inválida na tabela TMOD."
                }
                if (entryName == "Info") hasInfo = true
                packedTotal = Math.addExact(packedTotal, packed.toLong())
                require(packedTotal <= payloadLength.toLong()) { "Tabela TMOD excede o tamanho do pacote." }
            }
            require(hasInfo) { "O pacote TMOD não contém metadados Info." }
            require(input.filePointer + packedTotal == input.length()) {
                "Dados internos do pacote TMOD estão incompletos."
            }

            Metadata(name, version, loaderVersion, count)
        }
}
