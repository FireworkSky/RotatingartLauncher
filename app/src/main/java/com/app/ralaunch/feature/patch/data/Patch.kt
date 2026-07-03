package com.app.ralaunch.feature.patch.data

import android.util.Log
import java.io.File

/**
 * 补丁数据类
 */
data class Patch(
    val patchDir: File,
    val manifest: PatchManifest
) {
    fun getEntryAssemblyAbsolutePath(): File {
        // CHANGED: Use absoluteFile instead of canonicalFile to prevent IOException during launch flow
        return File(patchDir, manifest.entryAssemblyFile).absoluteFile
    }

    companion object {
        private const val TAG = "Patch"

        @JvmStatic
        fun fromPatchPath(patchDir: File): Patch? {
            // CHANGED: Use absoluteFile to prevent IOException during patch discovery gracefully
            val normalizedDir = patchDir.absoluteFile
            
            if (!normalizedDir.exists() || !normalizedDir.isDirectory) {
                Log.w(TAG, "fromPatchPath: Patch path does not exist or is not a directory: $normalizedDir")
                return null
            }

            val manifest = PatchManifest.fromJson(
                File(normalizedDir, PatchManifest.MANIFEST_FILE_NAME)
            )
            if (manifest == null) {
                Log.w(TAG, "fromPatchPath: Failed to load manifest from path: $normalizedDir")
                return null
            }

            return Patch(normalizedDir, manifest)
        }
    }
}
