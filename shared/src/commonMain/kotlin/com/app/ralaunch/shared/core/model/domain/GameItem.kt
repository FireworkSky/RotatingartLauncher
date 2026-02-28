package com.app.ralaunch.shared.core.model.domain

import com.app.ralaunch.shared.core.data.repository.GameListStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File  // Thay kotlin.io.path.Path

@Serializable
data class GameItem(
    val id: String,
    var displayedName: String,
    var displayedDescription: String = "",
    var gameId: String,
    var gameExePathRelative: String,
    var iconPathRelative: String? = null,
    var modLoaderEnabled: Boolean = true,
    var rendererOverride: String? = null,
    var gameEnvVars: Map<String, String?> = emptyMap(),

    @Transient
    var gameListStorageParent: GameListStorage? = null
) {
    @Transient
    val storageRootPathRelative: String
        get() = id

    @Transient
    val storageRootPathFull: String?
        get() = gameListStorageParent?.getGameGlobalStorageDirFull()?.let {
            // Thay Path(it).resolve(id) bằng File(it, id)
            File(it, id).absolutePath
        }

    @Transient
    val gameExePathFull: String?
        get() = storageRootPathFull?.let {
            // Thay Path(it).resolve(gameExePathRelative)
            File(it, gameExePathRelative).absolutePath
        }

    @Transient
    val iconPathFull: String?
        get() = iconPathRelative?.let {
            storageRootPathFull?.let { base ->
                // Thay Path(base).resolve(it)
                File(base, it).absolutePath
            }
        }
}
