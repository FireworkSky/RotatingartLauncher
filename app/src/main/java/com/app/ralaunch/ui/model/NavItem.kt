package com.app.ralaunch.ui.model

import androidx.compose.ui.graphics.vector.ImageVector

data class NavItem(val title: String, val icon: ImageVector, val route: String, val selectedIcon: ImageVector? = null)