package com.app.ralaunch.controls.editor.manager;

import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.app.ralaunch.controls.ControlData;

/**
 * SeekBar 设置项统一管理器
 * 统一管理所有 SeekBar 类型的设置项，避免重复代码
 */
public class ControlEditDialogSeekBarManager {
    
    /**
     * SeekBar 配置接口
     */
    public interface SeekBarConfig {
        /**
         * 获取当前值（0.0-1.0 或具体数值）
         */
        float getCurrentValue();
        
        /**
         * 设置新值
         */
        void setValue(float value);
        
        /**
         * 获取显示文本
         */
        String getDisplayText(float value);
        
        /**
         * 获取最大值（用于百分比类型，通常是100）
         */
        int getMaxValue();
        
        /**
         * 是否需要通知更新
         */
        void notifyUpdate();
    }
    
    /**
     * 绑定 SeekBar 设置项
     * @param view 父视图
     * @param seekBarId SeekBar 的 ID
     * @param textViewId 显示值的 TextView 的 ID
     * @param config 配置接口
     */
    public static void bindSeekBarSetting(@NonNull View view, 
                                         int seekBarId, 
                                         int textViewId,
                                         @NonNull SeekBarConfig config) {
        SeekBar seekBar = view.findViewById(seekBarId);
        TextView textView = view.findViewById(textViewId);
        
        if (seekBar == null || textView == null) {
            return;
        }
        
        // 清除旧的监听器，避免重复绑定导致的问题
        seekBar.setOnSeekBarChangeListener(null);
        
        // 设置最大值
        seekBar.setMax(config.getMaxValue());
        
        // 设置初始值（从配置中获取当前值，确保使用最新的数据）
        float currentValue = config.getCurrentValue();
        int progress = (int) (currentValue * config.getMaxValue());
        seekBar.setProgress(progress);
        textView.setText(config.getDisplayText(currentValue));
        
        // 设置监听器
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float value = progress / (float) config.getMaxValue();
                    textView.setText(config.getDisplayText(value));
                    config.setValue(value);
                    config.notifyUpdate();
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }
    
    /**
     * 填充 SeekBar 设置项的值
     * @param view 父视图
     * @param seekBarId SeekBar 的 ID
     * @param textViewId 显示值的 TextView 的 ID
     * @param config 配置接口
     */
    public static void fillSeekBarSetting(@NonNull View view,
                                         int seekBarId,
                                         int textViewId,
                                         @NonNull SeekBarConfig config) {
        SeekBar seekBar = view.findViewById(seekBarId);
        TextView textView = view.findViewById(textViewId);
        
        if (seekBar == null || textView == null) {
            return;
        }
        
        float currentValue = config.getCurrentValue();
        int progress = (int) (currentValue * config.getMaxValue());
        seekBar.setProgress(progress);
        textView.setText(config.getDisplayText(currentValue));
    }
    
    /**
     * 创建百分比类型的配置（0.0-1.0 映射到 0-100）
     */
    public static SeekBarConfig createPercentConfig(@NonNull ControlData data,
                                                   @NonNull ValueSetter setter,
                                                   @NonNull UpdateNotifier notifier) {
        return new SeekBarConfig() {
            @Override
            public float getCurrentValue() {
                return setter.get(data);
            }
            
            @Override
            public void setValue(float value) {
                setter.set(data, value);
            }
            
            @Override
            public String getDisplayText(float value) {
                return (int) (value * 100) + "%";
            }
            
            @Override
            public int getMaxValue() {
                return 100;
            }
            
            @Override
            public void notifyUpdate() {
                notifier.notifyUpdate();
            }
        };
    }
    
    /**
     * 创建整数类型的配置（直接使用整数值）
     */
    public static SeekBarConfig createIntConfig(@NonNull ControlData data,
                                               @NonNull IntValueSetter setter,
                                               @NonNull UpdateNotifier notifier,
                                               int maxValue) {
        return new SeekBarConfig() {
            @Override
            public float getCurrentValue() {
                return setter.get(data);
            }
            
            @Override
            public void setValue(float value) {
                setter.set(data, (int) value);
            }
            
            @Override
            public String getDisplayText(float value) {
                return String.valueOf((int) value);
            }
            
            @Override
            public int getMaxValue() {
                return maxValue;
            }
            
            @Override
            public void notifyUpdate() {
                notifier.notifyUpdate();
            }
        };
    }
    
    /**
     * 值获取和设置接口（百分比类型）
     */
    public interface ValueSetter {
        float get(ControlData data);
        void set(ControlData data, float value);
    }
    
    /**
     * 值获取和设置接口（整数类型）
     */
    public interface IntValueSetter {
        int get(ControlData data);
        void set(ControlData data, int value);
    }
    
    /**
     * 更新通知接口
     */
    public interface UpdateNotifier {
        void notifyUpdate();
    }
}


