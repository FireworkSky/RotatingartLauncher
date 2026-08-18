package com.app.ralaunch.core.extractor

import android.content.Context
import java.nio.file.Path
import kotlin.io.path.outputStream

object AssetExtractor {
    @JvmStatic
    fun copyAssetToFile(context: Context, assetFileName: String, targetFile: Path) {
        context.assets.open(assetFileName).use { input ->
            targetFile.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        }
    }
}