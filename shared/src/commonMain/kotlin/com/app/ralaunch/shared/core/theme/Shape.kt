package com.app.ralaunch.shared.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 应用形状定义 - 跨平台共享
 */
val AppShapes = Shapes(
    // 超小圆角 - 用于小按钮、标签
    extraSmall = RoundedCornerShape(4.dp),
    
    // 小圆角 - 用于输入框、小卡片
    small = RoundedCornerShape(8.dp),
    
    // 中等圆角 - 用于普通卡片、对话框
    medium = RoundedCornerShape(12.dp),
    
    // 大圆角 - 用于浮动按钮、模态框
    large = RoundedCornerShape(16.dp),
    
    // 超大圆角 - 用于全屏对话框、底部弹窗
    extraLarge = RoundedCornerShape(24.dp)
)
