package com.app.ralaunch.feature.patch.data

import android.util.Log
import java.io.File  // Thay java.nio.file.*

data class Patch(
    val patchDir: File,
    val manifest: PatchManifest
) {
    fun getEntryAssemblyAbsolutePath(): File {
        return File(patchDir, manifest.entryAssemblyFile).canonicalFile
    }

    companion object {
        private const val TAG = "Patch"

        @JvmStatic
        fun fromPatchPath(patchDir: File): Patch? {
            val normalizedDir = patchDir.canonicalFile
            if (!normalizedDir.exists() || !normalizedDir.isDirectory) {
                Log.w(TAG, "fromPatchPath: Path does not exist or is not a directory: $normalizedDir")
                return null
            }

            val manifest = PatchManifest.fromJson(
                File(normalizedDir, PatchManifest.MANIFEST_FILE_NAME)
            )
            if (manifest == null) {
                Log.w(TAG, "fromPatchPath: Failed to load manifest from: $normalizedDir")
                return null
            }

            return Patch(normalizedDir, manifest)
        }
    }
}
