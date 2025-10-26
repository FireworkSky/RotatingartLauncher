// ComponentAdapter.java
package com.app.ralaunch.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.app.ralaunch.R;
import com.app.ralaunch.model.ComponentItem;
import java.util.List;

public class ComponentAdapter extends RecyclerView.Adapter<ComponentAdapter.ViewHolder> {
    private List<ComponentItem> componentList;

    public ComponentAdapter(List<ComponentItem> componentList) {
        this.componentList = componentList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_component, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ComponentItem component = componentList.get(position);

        holder.componentName.setText(component.getName());
        holder.componentDescription.setText(component.getDescription());
        holder.progressBar.setProgress(component.getProgress());

        if (component.isInstalled()) {
            holder.statusIcon.setImageResource(R.drawable.ic_check_circle);
            holder.statusIcon.setColorFilter(holder.itemView.getContext()
                    .getResources().getColor(R.color.card_background));
            holder.progressText.setText("已完成");
        } else {
            holder.statusIcon.setImageResource(R.drawable.ic_check_circle);
            holder.statusIcon.setColorFilter(holder.itemView.getContext()
                    .getResources().getColor(R.color.text_secondary));
            holder.progressText.setText(component.getProgress() + "%");
        }
    }

    @Override
    public int getItemCount() {
        return componentList.size();
    }

    public void updateComponent(int position, ComponentItem component) {
        componentList.set(position, component);
        notifyItemChanged(position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView componentName;
        public TextView componentDescription;
        public ProgressBar progressBar;
        public TextView progressText;
        public ImageView statusIcon;

        public ViewHolder(View view) {
            super(view);
            componentName = view.findViewById(R.id.componentName);
            componentDescription = view.findViewById(R.id.componentDescription);
            progressBar = view.findViewById(R.id.progressBar);
            progressText = view.findViewById(R.id.progressText);
            statusIcon = view.findViewById(R.id.statusIcon);
        }
    }
}