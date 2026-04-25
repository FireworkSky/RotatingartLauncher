package com.app.ralaunch.feature.controls.editors.managers

import com.app.ralaunch.core.logging.AppLog
import android.view.View
import android.widget.FrameLayout
import com.app.ralaunch.feature.controls.ControlData
import com.app.ralaunch.feature.controls.ui.ControlLayout
import com.app.ralaunch.feature.controls.ui.ControlView

/**
 * 控件数据同步管理器
 * 统一管理 ControlData 到 ControlView 的同步逻辑
 */
object ControlDataSyncManager {
    private const val TAG = "ControlDataSyncManager"
    
    /**
     * 同步控件数据到视图
     * @param layout 控件布局
     * @param controlData 要同步的控件数据
     * @return 是否成功同步
     */
    @JvmStatic
    fun syncControlDataToView(layout: ControlLayout?, controlData: ControlData?): Boolean {
        if (layout == null || controlData == null) {
            AppLog.w(TAG, "syncControlDataToView: layout=$layout, controlData=$controlData")
            return false
        }
        
        AppLog.d(TAG, "syncControlDataToView: looking for controlData@${System.identityHashCode(controlData)} (name='${controlData.name}'), childCount=${layout.childCount}")

        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            if (child is ControlView) {
                val viewData: ControlData? = child.controlData

                // 使用严格的对象引用匹配（只匹配完全相同的对象实例）
                // 这确保即使多个控件有相同名称，也只更新正确的那个控件
                val isMatch = viewData === controlData

                AppLog.d(TAG, "  child[$i]: viewData@${System.identityHashCode(viewData)} (name='${viewData?.name}'), isMatch=$isMatch")

                if (isMatch) {
                    // Get screen dimensions from the layout
                    val screenWidth = layout.resources.displayMetrics.widthPixels
                    val screenHeight = layout.resources.displayMetrics.heightPixels

                    // Update layout parameters
                    // Convert fraction-based values to pixels
                    // Note: width and height are both relative to screen height
                    val layoutParams = child.layoutParams
                    if (layoutParams is FrameLayout.LayoutParams) {
                        layoutParams.width = (controlData.width * screenHeight).toInt()
                        layoutParams.height = (controlData.height * screenHeight).toInt()
                        layoutParams.leftMargin = (controlData.x * screenWidth).toInt()
                        layoutParams.topMargin = (controlData.y * screenHeight).toInt()
                        child.layoutParams = layoutParams
                    }

                    // 更新视觉属性
                    // 所有控件都不设置 View 级别的 alpha，因为它们的 Paint 已经独立控制透明度
                    // 如果设置 View alpha，会导致双重透明度效果（opacity * opacity）
                    child.alpha = 1.0f
                    child.visibility = if (controlData.isVisible) View.VISIBLE else View.INVISIBLE

                    // 同步所有字段到原始数据对象，确保数据一致性
                    syncDataFields(viewData, controlData)
                    
                    // 验证纹理是否同步成功
                    if (viewData is ControlData.Button && controlData is ControlData.Button) {
                        AppLog.i(TAG, "Button texture synced: path='${viewData.texture.normal.path}', enabled=${viewData.texture.normal.enabled}")
                    }

                    // 重新设置 controlData 以触发 initPaints() 更新颜色/透明度
                    // 这是必须的，因为直接修改字段不会触发 Paint 重新初始化
                    child.controlData = viewData

                    // 刷新控件绘制
                    child.invalidate()

                    AppLog.i(TAG, "syncControlDataToView: SUCCESS for '${controlData.name}'")
                    return true
                }
            }
        }

        AppLog.w(TAG, "syncControlDataToView: FAILED - no matching control found for '${controlData.name}'")
        return false
    }

    /**
     * 同步数据字段
     * 将源数据的所有字段同步到目标数据对象
     * 注意：如果 target 和 source 是同一个对象，则不需要同步（避免不必要的操作）
     */
    private fun syncDataFields(target: ControlData, source: ControlData) {
        // 如果 target 和 source 是同一个对象，不需要同步
        if (target === source) {
            return
        }

        // 同步基础字段
        target.name = source.name
        target.x = source.x
        target.y = source.y
        target.width = source.width
        target.height = source.height
        target.rotation = source.rotation
        target.opacity = source.opacity
        target.borderOpacity = source.borderOpacity
        target.textOpacity = source.textOpacity
        target.bgColor = source.bgColor
        target.strokeColor = source.strokeColor
        target.strokeWidth = source.strokeWidth
        target.cornerRadius = source.cornerRadius
        target.isVisible = source.isVisible
        target.isPassThrough = source.isPassThrough

        // 同步特定类型的字段
        when {
            source is ControlData.Button && target is ControlData.Button -> {
                target.mode = source.mode
                target.keycode = source.keycode
                target.isToggle = source.isToggle
                target.shape = source.shape
                // 同步纹理配置
                target.texture = source.texture.copy()
            }
            source is ControlData.Joystick && target is ControlData.Joystick -> {
                target.stickKnobSize = source.stickKnobSize
                target.stickOpacity = source.stickOpacity
                target.joystickKeys = source.joystickKeys.clone()
                target.mode = source.mode
                target.isRightStick = source.isRightStick
                // 同步纹理配置
                target.texture = source.texture.copy()
            }
            source is ControlData.Text && target is ControlData.Text -> {
                target.displayText = source.displayText
                target.shape = source.shape
                // 同步纹理配置
                target.texture = source.texture.copy()
            }
            source is ControlData.TouchPad && target is ControlData.TouchPad -> {
                // 同步纹理配置
                target.texture = source.texture.copy()
            }
            source is ControlData.MouseWheel && target is ControlData.MouseWheel -> {
                // 同步纹理配置
                target.texture = source.texture.copy()
            }
        }
    }
}
