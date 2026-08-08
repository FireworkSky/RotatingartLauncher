package com.app.ralaunch.utils

import timber.log.Timber
import java.io.File

/*******************************************************************************
 * RotatingArtLauncher - Extensions
 * 
 * This file is part of the RotatingArtLauncher project.
 * 
 * Copyright (C) 2026 RotatingArtLauncher Contributors
 * 
 * Created by: eternalfuture-e38299 (2026/7/31)
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

// 在文件顶部或单独的 Extensions.kt 文件中定义扩展函数
fun File.deleteRecursively(rootPath: File): Boolean {
    // 安全检查：确保要删除的路径在 rootPath 内
    if (!this.absolutePath.startsWith(rootPath.absolutePath)) {
        Timber.w("Security: Attempted to delete path outside root: ${this.absolutePath}")
        return false
    }

    // 如果文件不存在，返回成功
    if (!this.exists()) return true

    // 如果是目录，递归删除子文件和子目录
    if (this.isDirectory) {
        // 先删除所有子文件
        this.listFiles()?.forEach { child ->
            if (!child.deleteRecursively(rootPath)) {
                Timber.w("Failed to delete child: ${child.absolutePath}")
                return false
            }
        }
    }

    // 删除当前文件/目录
    return this.delete()
}