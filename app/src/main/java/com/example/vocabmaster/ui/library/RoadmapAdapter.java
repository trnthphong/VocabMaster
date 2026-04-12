package com.example.vocabmaster.ui.library;

import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocabmaster.R;
import com.example.vocabmaster.databinding.ItemRoadmapStepBinding;
import com.example.vocabmaster.ui.study.StudyActivity;

import java.util.ArrayList;
import java.util.List;

public class RoadmapAdapter extends RecyclerView.Adapter<RoadmapAdapter.RoadmapViewHolder> {

    private final List<RoadmapStep> steps;
    private final int[] cardColors = {
            R.color.light_blue,
            R.color.light_yellow,
            R.color.light_pink,
            R.color.light_red,
            R.color.light_purple,
            R.color.light_orange
    };

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
        
        // Hiển thị tiêu đề Unit nếu là step đầu tiên hoặc khác unit với step trước
        String currentUnit = step.getLevel(); // VD: "Unit 1"
        boolean showHeader = false;
        
        // Xác định index của Unit để chọn màu
        int unitIndex = 0;
        List<String> uniqueUnits = new ArrayList<>();
        for (RoadmapStep s : steps) {
            String u = s.getLevel();
            if (u != null && !uniqueUnits.contains(u)) {
                uniqueUnits.add(u);
            }
        }
        unitIndex = uniqueUnits.indexOf(currentUnit);
        if (unitIndex < 0) unitIndex = 0;

        if (position == 0) {
            showHeader = true;
        } else {
            String prevUnit = steps.get(position - 1).getLevel();
            if (currentUnit != null && !currentUnit.equals(prevUnit)) {
                showHeader = true;
            }
        }

        if (showHeader) {
            holder.binding.textUnitHeader.setVisibility(View.VISIBLE);
            holder.binding.textUnitHeader.setText(currentUnit);
            holder.binding.unitDivider.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
        } else {
            holder.binding.textUnitHeader.setVisibility(View.GONE);
            holder.binding.unitDivider.setVisibility(View.GONE);
        }

        // Set màu sắc theo unit index
        int colorRes = cardColors[unitIndex % cardColors.length];
        holder.binding.cardStep.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), colorRes));

        holder.binding.textStepTitle.setText(step.getTitle());
        holder.binding.textStepDesc.setText(step.getDescription());
        holder.binding.imageStepIcon.setImageResource(step.getIconRes());

        // Hiển thị trạng thái khóa
        if (step.isLocked()) {
            holder.binding.imageLock.setVisibility(View.VISIBLE);
            holder.binding.cardStep.setAlpha(0.6f);
        } else {
            holder.binding.imageLock.setVisibility(View.GONE);
            holder.binding.cardStep.setAlpha(1.0f);
        }

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
