package com.app.ralaunch.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.app.ralaunch.R;
import com.app.ralaunch.model.FileItem;

import java.util.List;

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
        this.selectedFilePath = filePath;
        notifyDataSetChanged();
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
            holder.fileInfo.setText("文件夹");
        } else {
            // 这里可以添加文件大小信息
            holder.fileInfo.setText("文件");
        }

        // 设置选中状态
        boolean isSelected = fileItem.getPath().equals(selectedFilePath);
        holder.itemView.setBackgroundColor(isSelected ?
                holder.itemView.getContext().getColor(R.color.selected_item_background) :
                holder.itemView.getContext().getColor(android.R.color.transparent));

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

    @Override
    public int getItemCount() {
        return fileList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView fileIcon;
        TextView fileName;
        TextView fileInfo;

        ViewHolder(View itemView) {
            super(itemView);
            fileIcon = itemView.findViewById(R.id.fileIcon);
            fileName = itemView.findViewById(R.id.fileName);
            fileInfo = itemView.findViewById(R.id.fileInfo);
        }
    }
}