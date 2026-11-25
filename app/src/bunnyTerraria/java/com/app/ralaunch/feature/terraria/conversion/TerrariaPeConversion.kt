package com.app.ralaunch.feature.terraria.conversion

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Public boundary between the launcher UI and the optional native PE converter. */
object TerrariaPeConversion {
    fun profileAvailable(context: Context): Boolean =
        MobileTerrariaPackage.targetTml(context) != null

    fun targetTml(context: Context): String? = MobileTerrariaPackage.targetTml(context)

    suspend fun convert(
        context: Context,
        destination: File,
        progress: (stage: String, current: Int, total: Int) -> Boolean
    ): File = withContext(Dispatchers.IO) {
        MobileTerrariaConverter.convertInstalled(
            context,
            destination,
            MobileTerrariaPackage.Progress(progress)
        )
        destination
    }
}
