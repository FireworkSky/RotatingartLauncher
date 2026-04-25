package com.app.ralaunch.core.extractor

import android.graphics.Bitmap
import com.app.ralaunch.core.logging.AppLog
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * BMP 格式解码器
 * 专门用于解码 Windows 图标中的 BMP 数据
 */
object BmpDecoder {
    private const val TAG = "BmpDecoder"

    /**
     * 解码图标中的 BMP 数据为 Android Bitmap
     *
     * @param data BMP 数据（从 PE 资源中提取的原始数据）
     * @return Android Bitmap 对象，失败返回 null
     */
    @JvmStatic
    fun decodeBmpIcon(data: ByteArray?): Bitmap? {
        return try {
            if (data == null || data.size < 40) {
                AppLog.e(TAG, "Invalid icon data: too short")
                return null
            }

            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            // Windows 图标资源中的 BMP 没有 BITMAPFILEHEADER (14 bytes)
            // 直接从 BITMAPINFOHEADER 开始 (40 bytes)

            // 读取 BITMAPINFOHEADER
            val headerSize = buffer.getInt(0)
            val width = buffer.getInt(4)
            val height = buffer.getInt(8)
            val bitCount = buffer.getShort(14).toInt() and 0xFFFF

            // 验证header size
            if (headerSize != 40) {
                AppLog.e(TAG, "Invalid BITMAPINFOHEADER size: $headerSize")
                return null
            }

            // 图标的高度是实际高度的2倍（包含 AND mask）
            val actualHeight = height / 2

            // 只支持常见的位深度
            if (bitCount !in listOf(32, 24, 8, 4, 1)) {
                AppLog.e(TAG, "Unsupported bit count: $bitCount")
                return null
            }

            // 创建 Bitmap
            val bitmap = Bitmap.createBitmap(width, actualHeight, Bitmap.Config.ARGB_8888)

            // 解码像素数据
            var pixelDataOffset = headerSize

            // 如果有调色板
            if (bitCount <= 8) {
                val colorTableSize = (1 shl bitCount) * 4 // 每个颜色4字节 (BGRA)
                pixelDataOffset += colorTableSize
            }

            // 根据位深度解码
            when (bitCount) {
                32 -> decode32Bit(buffer, pixelDataOffset, bitmap, width, actualHeight)
                24 -> decode24Bit(buffer, pixelDataOffset, bitmap, width, actualHeight)
                8 -> decode8Bit(buffer, headerSize, pixelDataOffset, bitmap, width, actualHeight)
                4 -> decode4Bit(buffer, headerSize, pixelDataOffset, bitmap, width, actualHeight)
                1 -> decode1Bit(buffer, headerSize, pixelDataOffset, bitmap, width, actualHeight)
            }

            bitmap
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to decode BMP: ${e.message}", e)
            null
        }
    }

    /**
     * 解码 32-bit BMP (BGRA)
     */
    private fun decode32Bit(buffer: ByteBuffer, offset: Int, bitmap: Bitmap, width: Int, height: Int) {
        val pixels = IntArray(width * height)

        // BMP 是从下到上存储的
        for (y in height - 1 downTo 0) {
            val rowOffset = offset + (height - 1 - y) * width * 4
            for (x in 0 until width) {
                val pixelOffset = rowOffset + x * 4

                val b = buffer.get(pixelOffset).toInt() and 0xFF
                val g = buffer.get(pixelOffset + 1).toInt() and 0xFF
                val r = buffer.get(pixelOffset + 2).toInt() and 0xFF
                val a = buffer.get(pixelOffset + 3).toInt() and 0xFF

                pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * 解码 24-bit BMP (BGR)
     */
    private fun decode24Bit(buffer: ByteBuffer, offset: Int, bitmap: Bitmap, width: Int, height: Int) {
        val pixels = IntArray(width * height)
        val rowPadding = (4 - (width * 3) % 4) % 4 // 每行对齐到4字节

        for (y in height - 1 downTo 0) {
            val rowOffset = offset + (height - 1 - y) * (width * 3 + rowPadding)
            for (x in 0 until width) {
                val pixelOffset = rowOffset + x * 3

                val b = buffer.get(pixelOffset).toInt() and 0xFF
                val g = buffer.get(pixelOffset + 1).toInt() and 0xFF
                val r = buffer.get(pixelOffset + 2).toInt() and 0xFF

                pixels[y * width + x] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * 解码 8-bit BMP (indexed color)
     */
    private fun decode8Bit(buffer: ByteBuffer, headerSize: Int, offset: Int, bitmap: Bitmap, width: Int, height: Int) {
        // 读取调色板
        val palette = IntArray(256) { i ->
            val paletteOffset = headerSize + i * 4
            val b = buffer.get(paletteOffset).toInt() and 0xFF
            val g = buffer.get(paletteOffset + 1).toInt() and 0xFF
            val r = buffer.get(paletteOffset + 2).toInt() and 0xFF
            val a = buffer.get(paletteOffset + 3).toInt() and 0xFF
            (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val pixels = IntArray(width * height)
        val rowPadding = (4 - width % 4) % 4

        for (y in height - 1 downTo 0) {
            val rowOffset = offset + (height - 1 - y) * (width + rowPadding)
            for (x in 0 until width) {
                val index = buffer.get(rowOffset + x).toInt() and 0xFF
                pixels[y * width + x] = palette[index]
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * 解码 4-bit BMP (16 colors)
     */
    private fun decode4Bit(buffer: ByteBuffer, headerSize: Int, offset: Int, bitmap: Bitmap, width: Int, height: Int) {
        // 读取调色板
        val palette = IntArray(16) { i ->
            val paletteOffset = headerSize + i * 4
            val b = buffer.get(paletteOffset).toInt() and 0xFF
            val g = buffer.get(paletteOffset + 1).toInt() and 0xFF
            val r = buffer.get(paletteOffset + 2).toInt() and 0xFF
            val a = buffer.get(paletteOffset + 3).toInt() and 0xFF
            (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val pixels = IntArray(width * height)
        val bytesPerRow = (width + 1) / 2
        val rowPadding = (4 - bytesPerRow % 4) % 4

        for (y in height - 1 downTo 0) {
            val rowOffset = offset + (height - 1 - y) * (bytesPerRow + rowPadding)
            for (x in 0 until width) {
                val byteOffset = rowOffset + x / 2
                val pixelByte = buffer.get(byteOffset).toInt() and 0xFF
                val index = if (x % 2 == 0) (pixelByte shr 4) else (pixelByte and 0x0F)
                pixels[y * width + x] = palette[index]
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * 解码 1-bit BMP (monochrome)
     */
    private fun decode1Bit(buffer: ByteBuffer, headerSize: Int, offset: Int, bitmap: Bitmap, width: Int, height: Int) {
        // 读取调色板（通常是黑白）
        val palette = IntArray(2) { i ->
            val paletteOffset = headerSize + i * 4
            val b = buffer.get(paletteOffset).toInt() and 0xFF
            val g = buffer.get(paletteOffset + 1).toInt() and 0xFF
            val r = buffer.get(paletteOffset + 2).toInt() and 0xFF
            val a = buffer.get(paletteOffset + 3).toInt() and 0xFF
            (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val pixels = IntArray(width * height)
        val bytesPerRow = (width + 7) / 8
        val rowPadding = (4 - bytesPerRow % 4) % 4

        for (y in height - 1 downTo 0) {
            val rowOffset = offset + (height - 1 - y) * (bytesPerRow + rowPadding)
            for (x in 0 until width) {
                val byteOffset = rowOffset + x / 8
                val pixelByte = buffer.get(byteOffset).toInt() and 0xFF
                val bitIndex = 7 - (x % 8)
                val index = (pixelByte shr bitIndex) and 1
                pixels[y * width + x] = palette[index]
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }
}
