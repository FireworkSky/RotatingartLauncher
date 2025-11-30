package com.app.ralaunch.ui.model

data class ComponentItem(
    val name: String,
    val description: String,
    val fileName: String,
    var progress: Int = 0,
    var status: String = "等待安装",
    var isInstalled: Boolean = false
)