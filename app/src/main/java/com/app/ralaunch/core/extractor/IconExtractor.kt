@file:Suppress("DEPRECATION")

package com.app.ralaunch.core.extractor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicConvolve3x3
import com.app.ralaunch.core.logging.AppLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 从 PE 文件（EXE/DLL）中提取图标
 * 纯 Kotlin 实现，不依赖 C# 或 CoreCLR
 */
object IconExtractor {
    private const val TAG = "IconExtractor"

    // Windows 资源类型
    private const val RT_ICON = 3
    private const val RT_GROUP_ICON = 14

    /**
     * 从 EXE/DLL 文件中提取最佳质量的图标并保存为 PNG
     *
     * @param exePath EXE/DLL 文件路径
     * @param outputPath 输出 PNG 文件路径
     * @return true 表示成功，false 表示失败
     */
    @JvmStatic
    fun extractIconToPng(exePath: String, outputPath: String): Boolean {
        var file: RandomAccessFile? = null
        return try {
            file = RandomAccessFile(File(exePath), "r")
            val reader = PeReader(file)

            // 验证 PE 格式
            if (!reader.isPeFormat()) {
                AppLog.e(TAG, "Not a valid PE file: $exePath")
                return false
            }

            // 读取 PE Header
            val peHeader = reader.readPeHeader()

            // 读取资源 Section
            val resourceSection = reader.readResourceSection(peHeader)
            if (resourceSection == null) {
                AppLog.e(TAG, "No resource section found")
                return false
            }

            // 读取根资源目录
            val rootDir = reader.readResourceDirectory(resourceSection, resourceSection.resourceFileOffset)

            // 查找图标组资源 (RT_GROUP_ICON)
            val iconGroup = findBestIconGroup(reader, resourceSection, rootDir)
            if (iconGroup == null) {
                AppLog.e(TAG, "No icon group found")
                return false
            }

            // 选择最佳质量的图标（最大尺寸，最高位深度）
            val bestEntry = selectBestIcon(iconGroup)
            if (bestEntry == null) {
                AppLog.e(TAG, "No suitable icon entry found")
                return false
            }

            AppLog.i(TAG, "Selected icon: ${bestEntry.width}x${bestEntry.height}, ${bestEntry.bitCount} bits")

            // 查找并读取图标数据
            val iconData = findIconData(reader, resourceSection, rootDir, bestEntry.id)
            if (iconData == null) {
                AppLog.e(TAG, "Icon data not found")
                return false
            }

            // 检测图标格式并解码
            val bitmap = decodeIconData(iconData)
            if (bitmap == null) {
                AppLog.e(TAG, "Failed to decode icon bitmap")
                return false
            }

            // 保存为 PNG
            FileOutputStream(outputPath).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            AppLog.i(TAG, "Icon extracted successfully to: $outputPath")
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to extract icon: ${e.message}", e)
            false
        } finally {
            try {
                file?.close()
            } catch (e: IOException) {
                // Ignore
            }
        }
    }

    /**
     * 从图标组中选择最佳质量的图标
     * 优先级：尺寸越大越好，位深度越高越好
     */
    private fun selectBestIcon(iconGroup: IconGroup): IconGroupEntry? {
        if (iconGroup.entries.isEmpty()) return null

        return iconGroup.entries.maxByOrNull { calculateIconScore(it) }
    }

    /**
     * 计算图标质量分数
     * 分数 = 尺寸 × 位深度权重
     */
    private fun calculateIconScore(entry: IconGroupEntry): Int {
        val sizeScore = entry.width * entry.height
        val bitWeight = when (entry.bitCount) {
            32 -> 4  // 32位有透明通道，最佳
            24 -> 3  // 24位真彩色
            8 -> 2   // 256色
            else -> 1
        }
        return sizeScore * bitWeight
    }

    /**
     * 查找最佳的图标组（选择最大尺寸）
     */
    @Throws(IOException::class)
    private fun findBestIconGroup(
        reader: PeReader,
        resourceSection: PeReader.ResourceSection,
        rootDir: PeReader.ResourceDirectory
    ): IconGroup? {
        // 查找 RT_GROUP_ICON 类型
        val groupIconType = rootDir.entries.find { it.nameOrId == RT_GROUP_ICON && it.isDirectory }
            ?: return null

        // 读取图标组列表
        val groupDirOffset = resourceSection.resourceFileOffset + groupIconType.offset
        val groupDir = reader.readResourceDirectory(resourceSection, groupDirOffset)

        if (groupDir.entries.isEmpty()) return null

        // 选择第一个图标组
        val firstGroup = groupDir.entries[0]
        if (!firstGroup.isDirectory) return null

        // 读取语言目录
        val langDirOffset = resourceSection.resourceFileOffset + firstGroup.offset
        val langDir = reader.readResourceDirectory(resourceSection, langDirOffset)

        if (langDir.entries.isEmpty()) return null

        // 读取数据
        val dataEntryOffset = resourceSection.resourceFileOffset + langDir.entries[0].offset
        val groupData = reader.readResourceData(resourceSection, dataEntryOffset)

        // 解析图标组数据
        return parseIconGroup(groupData)
    }

    /**
     * 解析图标组数据
     */
    private fun parseIconGroup(data: ByteArray): IconGroup? {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // NEWHEADER 结构
        val type = buffer.getShort(2).toInt() and 0xFFFF
        val count = buffer.getShort(4).toInt() and 0xFFFF

        if (type != 1 || count == 0) { // 1 = ICON
            return null
        }

        val entries = mutableListOf<IconGroupEntry>()

        // 读取每个图标条目 (RESDIR 结构，14 bytes each)
        var offset = 6
        repeat(count) {
            var width = buffer.get(offset).toInt() and 0xFF
            var height = buffer.get(offset + 1).toInt() and 0xFF
            val colorCount = buffer.get(offset + 2).toInt() and 0xFF
            val reserved = buffer.get(offset + 3).toInt() and 0xFF
            val planes = buffer.getShort(offset + 4).toInt() and 0xFFFF
            val bitCount = buffer.getShort(offset + 6).toInt() and 0xFFFF
            val bytesInRes = buffer.getInt(offset + 8)
            val id = buffer.getShort(offset + 12).toInt() and 0xFFFF

            // 0 表示 256
            if (width == 0) width = 256
            if (height == 0) height = 256

            entries.add(
                IconGroupEntry(
                    width = width,
                    height = height,
                    colorCount = colorCount,
                    reserved = reserved,
                    planes = planes,
                    bitCount = bitCount,
                    bytesInRes = bytesInRes,
                    id = id
                )
            )
            offset += 14
        }

        return IconGroup(entries)
    }

    /**
     * 检测并解码图标数据（支持 PNG 和 BMP）
     */
    private fun decodeIconData(iconData: ByteArray): Bitmap? {
        if (iconData.size < 4) return null

        // 检测 PNG 魔数: 89 50 4E 47 (0x89 'P' 'N' 'G')
        if (iconData.size >= 4 &&
            (iconData[0].toInt() and 0xFF) == 0x89 &&
            (iconData[1].toInt() and 0xFF) == 0x50 &&
            (iconData[2].toInt() and 0xFF) == 0x4E &&
            (iconData[3].toInt() and 0xFF) == 0x47
        ) {
            AppLog.i(TAG, "Detected PNG format icon")
            // PNG 格式，使用 BitmapFactory 解码
            return BitmapFactory.decodeByteArray(iconData, 0, iconData.size)
        }

        // BMP 格式（BITMAPINFOHEADER 开头应该是 0x28 = 40）
        AppLog.i(TAG, "Attempting BMP format decoding")
        return BmpDecoder.decodeBmpIcon(iconData)
    }

    /**
     * 查找指定 ID 的图标数据
     */
    @Throws(IOException::class)
    private fun findIconData(
        reader: PeReader,
        resourceSection: PeReader.ResourceSection,
        rootDir: PeReader.ResourceDirectory,
        iconId: Int
    ): ByteArray? {
        // 查找 RT_ICON 类型
        val iconType = rootDir.entries.find { it.nameOrId == RT_ICON && it.isDirectory }
            ?: return null

        // 读取图标列表
        val iconDirOffset = resourceSection.resourceFileOffset + iconType.offset
        val iconDir = reader.readResourceDirectory(resourceSection, iconDirOffset)

        // 查找匹配的 ID
        val targetIcon = iconDir.entries.find { it.nameOrId == iconId && it.isDirectory }
            ?: return null

        // 读取语言目录
        val langDirOffset = resourceSection.resourceFileOffset + targetIcon.offset
        val langDir = reader.readResourceDirectory(resourceSection, langDirOffset)

        if (langDir.entries.isEmpty()) return null

        // 读取数据
        val dataEntryOffset = resourceSection.resourceFileOffset + langDir.entries[0].offset
        return reader.readResourceData(resourceSection, dataEntryOffset)
    }

    /**
     * 高清化小图标（使用双三次插值+锐化）
     *
     * @param context Android Context
     * @param iconPath 原始图标路径
     * @return 高清化后的图标路径，失败返回null
     */
    @JvmStatic
    fun upscaleIcon(context: Context, iconPath: String): String? {
        return try {
            // 读取原始图标
            val original = BitmapFactory.decodeFile(iconPath)
            if (original == null) {
                AppLog.e(TAG, "Failed to decode original icon")
                return null
            }

            val originalWidth = original.width
            val originalHeight = original.height

            AppLog.i(TAG, "Original icon size: ${originalWidth}x$originalHeight")

            // 如果图标已经足够大,不需要高清化
            if (originalWidth >= 128 && originalHeight >= 128) {
                AppLog.i(TAG, "Icon is already large enough, skipping upscale")
                original.recycle()
                return iconPath
            }

            // 目标尺寸：256x256（或原尺寸的8倍，取较小值）
            val targetSize = minOf(256, maxOf(originalWidth, originalHeight) * 8)

            // 使用双三次插值放大
            val upscaled = Bitmap.createScaledBitmap(original, targetSize, targetSize, true)

            // 应用锐化滤镜提升清晰度
            val sharpened = applySharpen(context, upscaled)

            // 保存高清化后的图标
            val upscaledPath = iconPath.replace(".png", "_upscaled.png")
            FileOutputStream(upscaledPath).use { out ->
                sharpened.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // 清理
            original.recycle()
            upscaled.recycle()
            sharpened.recycle()

            AppLog.i(TAG, "Icon upscaled from ${originalWidth}x$originalHeight to ${targetSize}x$targetSize")

            upscaledPath
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to upscale icon: ${e.message}", e)
            null
        }
    }

    /**
     * 应用锐化滤镜
     *
     * @param context Android Context
     * @param src 源图像
     * @return 锐化后的图像，失败返回原图像
     */
    private fun applySharpen(context: Context, src: Bitmap): Bitmap {
        // 锐化卷积核
        val sharpenKernel = floatArrayOf(
            0f, -1f, 0f,
            -1f, 5f, -1f,
            0f, -1f, 0f
        )

        val result = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)

        var rs: RenderScript? = null
        return try {
            rs = RenderScript.create(context)
            val input = Allocation.createFromBitmap(rs, src)
            val output = Allocation.createFromBitmap(rs, result)

            val convolution = ScriptIntrinsicConvolve3x3.create(rs, Element.U8_4(rs))
            convolution.setInput(input)
            convolution.setCoefficients(sharpenKernel)
            convolution.forEach(output)

            output.copyTo(result)

            input.destroy()
            output.destroy()
            convolution.destroy()

            result
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to apply sharpen filter, using original: ${e.message}")
            src
        } finally {
            rs?.destroy()
        }
    }

    /**
     * 检查图标是否需要高清化
     *
     * @param iconPath 图标文件路径
     * @return true 表示需要高清化（图标小于5KB），false 表示不需要
     */
    @JvmStatic
    fun needsUpscale(iconPath: String): Boolean {
        return try {
            val iconFile = File(iconPath)
            if (!iconFile.exists()) return false
            // 如果图标文件小于5KB，可能是16x16或32x32的小图标，需要高清化
            iconFile.length() < 5 * 1024
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to check icon size: ${e.message}")
            false
        }
    }

    /**
     * 检查 EXE/DLL 文件是否包含图标资源
     *
     * @param exePath EXE/DLL 文件路径
     * @return true 表示包含图标，false 表示不包含或检查失败
     */
    @JvmStatic
    fun hasIcon(exePath: String): Boolean {
        var file: RandomAccessFile? = null
        return try {
            file = RandomAccessFile(File(exePath), "r")
            val reader = PeReader(file)

            // 验证 PE 格式
            if (!reader.isPeFormat()) return false

            // 读取 PE Header
            val peHeader = reader.readPeHeader()

            // 读取资源 Section
            val resourceSection = reader.readResourceSection(peHeader) ?: return false

            // 读取根资源目录
            val rootDir = reader.readResourceDirectory(resourceSection, resourceSection.resourceFileOffset)

            // 查找图标组资源 (RT_GROUP_ICON)
            val iconGroup = findBestIconGroup(reader, resourceSection, rootDir)
            iconGroup != null && iconGroup.entries.isNotEmpty()
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to check icon: ${e.message}")
            false
        } finally {
            try {
                file?.close()
            } catch (e: IOException) {
                // Ignore
            }
        }
    }

    /**
     * 图标组
     */
    private data class IconGroup(val entries: List<IconGroupEntry>)

    /**
     * 图标组条目
     */
    private data class IconGroupEntry(
        val width: Int,
        val height: Int,
        val colorCount: Int,
        val reserved: Int,
        val planes: Int,
        val bitCount: Int,
        val bytesInRes: Int,
        val id: Int
    )
}
