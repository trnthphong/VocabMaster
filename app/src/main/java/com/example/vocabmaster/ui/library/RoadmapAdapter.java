package com.example.vocabmaster.ui.library;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocabmaster.databinding.ItemRoadmapStepBinding;

import java.util.List;

public class RoadmapAdapter extends RecyclerView.Adapter<RoadmapAdapter.RoadmapViewHolder> {

    private final List<RoadmapStep> steps;

    public RoadmapAdapter(List<RoadmapStep> steps) {
        this.steps = steps;
    }

    @NonNull
    @Override
    public RoadmapViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRoadmapStepBinding binding = ItemRoadmapStepBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new RoadmapViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RoadmapViewHolder holder, int position) {
        RoadmapStep step = steps.get(position);
        holder.binding.textStepLevel.setText(step.getLevel());
        holder.binding.textStepTitle.setText(step.getTitle());
        holder.binding.textStepDesc.setText(step.getDescription());
        holder.binding.imageStepIcon.setImageResource(step.getIconRes());
    }

    @Override
    public int getItemCount() {
        return steps.size();
    }

    static class RoadmapViewHolder extends RecyclerView.ViewHolder {
        final ItemRoadmapStepBinding binding;

        public RoadmapViewHolder(ItemRoadmapStepBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
