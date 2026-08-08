package com.app.ralaunch.models

import kotlinx.serialization.Serializable

/*******************************************************************************
 * RotatingArtLauncher - AnnouncementModels
 * 
 * This file is part of the RotatingArtLauncher project.
 * 
 * Copyright (C) 2026 RotatingArtLauncher Contributors
 * 
 * Created by: eternalfuture-e38299 (2026/7/5)
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
data class AnnouncementResponse(
    val version: Int = 1,
    val announcements: List<AnnouncementEntry>
)

@Serializable
data class AnnouncementEntry(
    val id: String,
    val publishedAt: String,
    val meta: Map<String, AnnouncementMeta>
)

@Serializable
data class AnnouncementMeta(
    val title: String,
    val tags: List<String> = emptyList()
)

data class AnnouncementItem(
    val id: String,
    val title: String,
    val content: String = "",
    val publishedAt: String,
    val tags: List<String> = emptyList()
)

data class AnnouncementUiState(
    val announcements: List<AnnouncementItem> = emptyList(),
    val selectedId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)