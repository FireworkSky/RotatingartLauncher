package com.app.ralaunch.models

import kotlinx.serialization.Serializable

/*******************************************************************************
 * RotatingArtLauncher - PatchManifest
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

@Serializable
data class PatchManifest(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val version: String = "",
    val author: String = "",
    val targetGames: List<String>? = null,
    val dllFileName: String = "",
    val entryPoint: EntryPoint? = null,
    val priority: Int = 0,
    val enabled: Boolean = true,
    val dependencies: Dependencies? = null
)

@Serializable
data class EntryPoint(
    val typeName: String = "",
    val methodName: String = ""
)

@Serializable
data class Dependencies(
    val libs: List<String>? = null
)