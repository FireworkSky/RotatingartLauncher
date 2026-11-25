package com.app.ralaunch.feature.terraria.conversion

import android.content.Context
import java.io.File

/** Safe no-converter implementation used by ordinary builds without a PE profile. */
object TerrariaPeConversion {
    fun profileAvailable(context: Context): Boolean = false
    fun targetTml(context: Context): String? = null

    suspend fun convert(
        context: Context,
        destination: File,
        progress: (stage: String, current: Int, total: Int) -> Boolean
    ): File = error("Este APK não foi compilado com o conversor Terraria PE.")
}
