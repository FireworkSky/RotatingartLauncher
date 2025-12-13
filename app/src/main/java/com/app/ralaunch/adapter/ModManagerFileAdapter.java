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
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.text.DecimalFormat;
import java.util.List;

/**
 * 模组管理器文件适配器
 * 
 * 支持网格视图和横向列表视图
 */
public class ModManagerFileAdapter extends RecyclerView.Adapter<ModManagerFileAdapter.ViewHolder> {

    private static final int VIEW_TYPE_GRID = 0;
    private static final int VIEW_TYPE_LIST = 1;

    private List<FileItem> fileList;
    private FileBrowserAdapter.OnFileClickListener listener;
    private String selectedFilePath;
    private String viewMode; // "grid" or "list"

    public ModManagerFileAdapter(List<FileItem> fileList, FileBrowserAdapter.OnFileClickListener listener, String viewMode) {
        this.fileList = fileList;
        this.listener = listener;
        this.viewMode = viewMode;
    }
    
    public void setViewMode(String viewMode) {
        this.viewMode = viewMode;
    }

    public void setSelectedFile(String filePath) {
        String oldPath = this.selectedFilePath;
        this.selectedFilePath = filePath;

        // 只刷新受影响的项
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

    @Override
    public int getItemViewType(int position) {
        return "list".equals(viewMode) ? VIEW_TYPE_LIST : VIEW_TYPE_GRID;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = viewType == VIEW_TYPE_LIST ? R.layout.item_file : R.layout.item_file_grid;
        View view = LayoutInflater.from(parent.getContext())
                .inflate(layoutId, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileItem fileItem = fileList.get(position);

        // 设置图标
        holder.fileIcon.setImageResource(fileItem.getIconResId());

        // 设置文件名（支持2行显示）
        String fileName = fileItem.getName();
        holder.fileName.setText(fileName);

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
            int primaryColor = holder.itemView.getContext().getColor(R.color.accent_primary);
            holder.cardView.setStrokeColor(primaryColor);
            holder.cardView.setStrokeWidth(3);
            int primaryContainerColor = holder.itemView.getContext().getColor(R.color.md_theme_light_primaryContainer);
            holder.cardView.setCardBackgroundColor(primaryContainerColor);
            holder.cardView.setCardElevation(8f);
        } else {
            holder.cardView.setStrokeWidth(1);
            int surfaceColor = holder.itemView.getContext().getColor(R.color.md_theme_light_surface);
            holder.cardView.setCardBackgroundColor(surfaceColor);
            holder.cardView.setCardElevation(2f);
        }

        // 点击事件
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
    
    private String getFileExtension(Context context, String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toUpperCase();
        }
        return context.getString(R.string.filebrowser_file_type);
    }
    
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

