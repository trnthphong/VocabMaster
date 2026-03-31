package com.example.vocabmaster.ui.library;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocabmaster.databinding.ItemRoadmapStepBinding;
import com.example.vocabmaster.ui.study.StudyActivity;

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

        // Hiển thị trạng thái khóa/mở khóa
        if (step.isLocked()) {
            holder.itemView.setAlpha(0.5f);
            holder.binding.imageStepIcon.setAlpha(0.5f);
        } else {
            holder.itemView.setAlpha(1.0f);
            holder.binding.imageStepIcon.setAlpha(1.0f);
        }

        // Xử lý sự kiện click
        holder.itemView.setOnClickListener(v -> {
            if (step.isLocked()) {
                Toast.makeText(v.getContext(), "Hoàn thành bài học trước để mở khóa!", Toast.LENGTH_SHORT).show();
            } else {
                Intent intent = new Intent(v.getContext(), StudyActivity.class);
                intent.putExtra("lesson_id", step.getId());
                intent.putExtra("lesson_title", step.getTitle());
                v.getContext().startActivity(intent);
            }
        });
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
