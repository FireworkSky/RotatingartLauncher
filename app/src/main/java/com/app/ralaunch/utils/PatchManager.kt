package com.app.ralaunch.utils

import android.content.Context
import com.app.ralaunch.MainActivity
import com.app.ralaunch.models.PatchManifest
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/*******************************************************************************
 * RotatingArtLauncher - PatchManager
 * 
 * This file is part of the RotatingArtLauncher project.
 * 
 * Copyright (C) 2026 RotatingArtLauncher Contributors
 * 
 * Created by: eternalfuture-e38299 (2026/7/9)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

object PatchManager {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val patchStoragePath
        get() = File(MainActivity.context!!.getExternalFilesDir(null), "patches")


    fun installPatch(inputStream: InputStream): Boolean {
        return try {
            // 如果 inputStream 支持 reset，先 mark
            if (inputStream.markSupported()) {
                inputStream.mark(1024 * 1024 * 10) // 设置 mark 限制
            }

            val zipStream = ZipInputStream(inputStream)

            // 第一次读取：读取 manifest
            var manifest: PatchManifest? = null
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name == "patch.json") {
                    manifest = json.decodeFromString(zipStream.bufferedReader().readText())
                    break
                }
                entry = zipStream.nextEntry
            }

            manifest ?: run {
                Timber.w("Failed to read patch manifest")
                return false
            }

            val patchPath = patchStoragePath.resolve(manifest.id)

            // 删除旧目录
            if (patchPath.exists()) {
                Timber.i("Patch already exists, removing old directory: ${manifest.id}")
                if (!patchPath.deleteRecursively(patchStoragePath)) {
                    Timber.w("Failed to delete old patch directory")
                    return false
                }
            }

            // 创建新目录
            patchPath.mkdirs()

            // 重置流，重新读取
            inputStream.reset()
            val newZipStream = ZipInputStream(inputStream)

            // 第二次读取：解压所有文件
            var newEntry = newZipStream.nextEntry
            while (newEntry != null) {
                val targetFile = patchPath.resolve(newEntry.name)

                if (newEntry.isDirectory) {
                    // 创建目录
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile!!.mkdirs()
                    targetFile.outputStream().use { outputStream ->
                        newZipStream.copyTo(outputStream)
                    }
                }

                newZipStream.closeEntry()
                newEntry = newZipStream.nextEntry
            }

            Timber.i("Patch installed successfully: ${manifest.id}")
            true

        } catch (e: Exception) {
            Timber.e(e, "Patch installation failed")
            false
        }
    }

    fun installBuiltInPatches(context: Context) {
        try {
            val assetManager = context.assets
            val patchFiles = assetManager.list("patches") ?: emptyArray()

            patchFiles
                .filter { it.endsWith(".zip") }
                .forEach { patchFileName ->
                    Timber.i("Installing built-in patch: $patchFileName")
                    assetManager.open("patches/$patchFileName").use { inputStream ->
                        installPatch(inputStream)
                    }
                }
        } catch (e: Exception) {
            throw RuntimeException("Failed to install built-in patches", e)
        }
    }
}