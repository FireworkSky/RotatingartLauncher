package com.app.ralaunch.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.app.ralaunch.R;
import com.app.ralaunch.model.ControlLayout;
import com.app.ralaunch.utils.ControlLayoutManager;
import java.util.List;

public class ControlLayoutAdapter extends RecyclerView.Adapter<ControlLayoutAdapter.ViewHolder> {

    private List<ControlLayout> layouts;
    private OnLayoutClickListener listener;
    private ControlLayoutManager layoutManager;

    public interface OnLayoutClickListener {
        void onLayoutClick(ControlLayout layout);
        void onLayoutDelete(ControlLayout layout);
        void onLayoutSetDefault(ControlLayout layout);
    }

    public ControlLayoutAdapter(List<ControlLayout> layouts, OnLayoutClickListener listener) {
        this.layouts = layouts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_control_layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ControlLayout layout = layouts.get(position);

        holder.layoutName.setText(layout.getName());
        holder.layoutInfo.setText(layout.getElements().size() + "个控件");

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onLayoutClick(layout);
            }
        });

        holder.btnSetDefault.setOnClickListener(v -> {
            if (listener != null) {
                listener.onLayoutSetDefault(layout);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onLayoutDelete(layout);
            }
        });
    }

    @Override
    public int getItemCount() {
        return layouts.size();
    }

    public void updateLayouts(List<ControlLayout> newLayouts) {
        this.layouts = newLayouts;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView layoutName;
        TextView layoutInfo;
        ImageButton btnSetDefault;
        ImageButton btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            layoutName = itemView.findViewById(R.id.layout_name);
            layoutInfo = itemView.findViewById(R.id.layout_info);
            btnSetDefault = itemView.findViewById(R.id.btn_set_default);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}