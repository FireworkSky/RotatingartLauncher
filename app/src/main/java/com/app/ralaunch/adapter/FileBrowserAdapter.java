package com.app.ralaunch.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.app.ralaunch.R;
import com.app.ralaunch.model.FileItem;
import com.app.ralib.ui.AnimationHelper;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.text.DecimalFormat;
import java.util.List;

/**
 * 文件浏览器适配器
 * 
 * 用于文件浏览器界面显示文件和文件夹列表，提供：
 * - 文件和文件夹显示（带图标）
 * - 文件点击和长按事件
 * - 选中状态高亮显示
 * 
 * 支持文件和文件夹选择回调
 */
public class FileBrowserAdapter extends RecyclerView.Adapter<FileBrowserAdapter.ViewHolder> {

    private List<FileItem> fileList;
    private OnFileClickListener listener;
    private String selectedFilePath;

    public interface OnFileClickListener {
        void onFileClick(FileItem fileItem);
        void onFileLongClick(FileItem fileItem);
    }

    public FileBrowserAdapter(List<FileItem> fileList, OnFileClickListener listener) {
        this.fileList = fileList;
        this.listener = listener;
    }

    public void setSelectedFile(String filePath) {
        String oldPath = this.selectedFilePath;
        this.selectedFilePath = filePath;

        // 只刷新受影响的项，避免全局刷新
        if (oldPath != null) {
            for (int i = 0; i < fileList.size(); i++) {
                if (fileList.get(i).getPath().equals(oldPath)) {
                    notifyItemChanged(i);
                    break;
                }
            }
        }
        if (filePath != null) {
            for (int i = 0; i < fileList.size(); i++) {
                if (fileList.get(i).getPath().equals(filePath)) {
                    notifyItemChanged(i);
                    break;
                }
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileItem fileItem = fileList.get(position);

        // 设置图标
        holder.fileIcon.setImageResource(fileItem.getIconResId());

        // 设置文件名
        holder.fileName.setText(fileItem.getName());

        // 设置文件类型/大小信息
        if (fileItem.isDirectory()) {
            if (fileItem.isParentDirectory()) {
                holder.fileInfo.setText(holder.itemView.getContext().getString(R.string.filebrowser_parent_directory));
            } else {
                holder.fileInfo.setText(holder.itemView.getContext().getString(R.string.filebrowser_folder));
            }
            holder.fileSize.setVisibility(View.GONE);
        } else {
            // 显示文件大小
            File file = new File(fileItem.getPath());
            if (file.exists()) {
                holder.fileInfo.setText(getFileExtension(holder.itemView.getContext(), fileItem.getName()));
                holder.fileSize.setText(formatFileSize(file.length()));
                holder.fileSize.setVisibility(View.VISIBLE);
            } else {
                holder.fileInfo.setText(holder.itemView.getContext().getString(R.string.filebrowser_file));
                holder.fileSize.setVisibility(View.GONE);
            }
        }

        // 设置选中状态
        boolean isSelected = fileItem.getPath().equals(selectedFilePath);
        if (isSelected) {
            // 选中状态：紫色边框、背景色、更高阴影
            // 使用主题属性获取颜色，确保日间/夜间模式自动切换
            android.util.TypedValue typedValue = new android.util.TypedValue();
            holder.itemView.getContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorPrimary, typedValue, true);
            int primaryColor = typedValue.data;
            holder.cardView.setStrokeColor(primaryColor);
            holder.cardView.setStrokeWidth(3);
            
            // 使用 primaryContainer 颜色作为背景
            holder.itemView.getContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true);
            int primaryContainerColor = typedValue.data;
            holder.cardView.setCardBackgroundColor(primaryContainerColor);
            holder.cardView.setCardElevation(8f);
        } else {
            // 未选中状态：无边框、默认背景、低阴影
            holder.cardView.setStrokeWidth(0);
            
            // 使用 surface 颜色作为背景
            android.util.TypedValue typedValue = new android.util.TypedValue();
            holder.itemView.getContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorSurface, typedValue, true);
            int surfaceColor = typedValue.data;
            holder.cardView.setCardBackgroundColor(surfaceColor);
            holder.cardView.setCardElevation(2f);
        }

        // 点击事件 - 移除动画以提升响应速度
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFileClick(fileItem);
            }
        });

        // 长按事件
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onFileLongClick(fileItem);
            }
            return true;
        });
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(Context context, String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toUpperCase();
        }
        // 使用字符串资源
        return context.getString(R.string.filebrowser_file_type);
    }
    
    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    @Override
    public int getItemCount() {
        return fileList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        ImageView fileIcon;
        TextView fileName;
        TextView fileInfo;
        TextView fileSize;

        ViewHolder(View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            fileIcon = itemView.findViewById(R.id.fileIcon);
            fileName = itemView.findViewById(R.id.fileName);
            fileInfo = itemView.findViewById(R.id.fileInfo);
            fileSize = itemView.findViewById(R.id.fileSize);
        }
    }
}
