package com.app.ralaunch.controls.editors.managers

import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.app.ralaunch.controls.data.ControlData
import com.app.ralaunch.controls.views.ControlLayout
import com.app.ralaunch.controls.views.ControlView

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
            Log.w(TAG, "syncControlDataToView: layout=$layout, controlData=$controlData")
            return false
        }
        
        Log.d(TAG, "syncControlDataToView: looking for '${controlData.name}', childCount=${layout.childCount}")

        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            if (child is ControlView) {
                val viewData: ControlData? = child.controlData

                // 使用名称匹配，支持 Activity 重建后的反序列化对象
                val isMatch = viewData != null && (
                    viewData === controlData ||  // 引用相同（优先）
                    viewData.name == controlData.name  // 名称相同（备用）
                )
                
                Log.d(TAG, "  child[$i]: '${viewData?.name}', isMatch=$isMatch")

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
                    // 对于摇杆，不设置 View 级别的 alpha，因为它们有独立的透明度控制
                    if (controlData is ControlData.Joystick) {
                        // 摇杆：View alpha 始终为 1.0，让内部的 Paint alpha 独立控制
                        child.alpha = 1.0f
                    } else {
                        // 按钮和文本：可以使用 View 级别的 alpha
                        child.alpha = controlData.opacity
                    }
                    child.visibility = if (controlData.isVisible) View.VISIBLE else View.INVISIBLE

                    // 同步所有字段到原始数据对象，确保数据一致性
                    syncDataFields(viewData, controlData)
                    
                    // 验证纹理是否同步成功
                    if (viewData is ControlData.Button && controlData is ControlData.Button) {
                        Log.i(TAG, "Button texture synced: path='${viewData.texture.normal.path}', enabled=${viewData.texture.normal.enabled}")
                    }

                    // 重新设置 controlData 以触发 initPaints() 更新颜色/透明度
                    // 这是必须的，因为直接修改字段不会触发 Paint 重新初始化
                    child.controlData = viewData

                    // 刷新控件绘制
                    child.invalidate()

                    Log.i(TAG, "syncControlDataToView: SUCCESS for '${controlData.name}'")
                    return true
                }
            }
        }

        Log.w(TAG, "syncControlDataToView: FAILED - no matching control found for '${controlData.name}'")
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
        }
    }
}

