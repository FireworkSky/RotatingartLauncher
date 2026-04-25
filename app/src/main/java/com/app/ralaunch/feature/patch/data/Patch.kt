package com.app.ralaunch.feature.patch.data

import com.app.ralaunch.core.logging.AppLog
import java.nio.file.Files
import java.nio.file.Path

/**
 * 补丁数据类
 */
data class Patch(
    val patchPath: Path,
    val manifest: PatchManifest
) {
    fun getEntryAssemblyAbsolutePath(): Path {
        return patchPath.resolve(manifest.entryAssemblyFile).toAbsolutePath().normalize()
    }

    companion object {
        private const val TAG = "Patch"

        @JvmStatic
        fun fromPatchPath(patchPath: Path): Patch? {
            val normalizedPath = patchPath.normalize()
            if (!Files.exists(normalizedPath) || !Files.isDirectory(normalizedPath)) {
                AppLog.w(TAG, "fromPatchPath: Patch path does not exist or is not a directory: $normalizedPath")
                return null
            }

            val manifest = PatchManifest.fromJson(normalizedPath.resolve(PatchManifest.MANIFEST_FILE_NAME))
            if (manifest == null) {
                AppLog.w(TAG, "fromPatchPath: Failed to load manifest from path: $normalizedPath")
                return null
            }

            return Patch(normalizedPath, manifest)
        }
    }
}
