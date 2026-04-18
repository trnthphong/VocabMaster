package com.example.vocabmaster.ui.library;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocabmaster.R;
import com.example.vocabmaster.databinding.ItemRoadmapStepBinding;
import com.example.vocabmaster.databinding.ItemRoadmapTodayBinding;
import com.example.vocabmaster.ui.study.StudyActivity;

import java.util.ArrayList;
import java.util.List;

public class RoadmapAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int VIEW_TYPE_TODAY = 0;
    public static final int VIEW_TYPE_OVERVIEW = 1;

    private final List<RoadmapStep> steps;
    private final int viewType;
    private final int[] cardColors = {
            R.color.light_blue,
            R.color.light_yellow,
            R.color.light_pink,
            R.color.light_red,
            R.color.light_purple,
            R.color.light_orange
    };

    public RoadmapAdapter(List<RoadmapStep> steps, int viewType) {
        this.steps = steps;
        this.viewType = viewType;
    }

    @Override
    public int getItemViewType(int position) {
        return viewType;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_TODAY) {
            ItemRoadmapTodayBinding binding = ItemRoadmapTodayBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new TodayViewHolder(binding);
        } else {
            ItemRoadmapStepBinding binding = ItemRoadmapStepBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new RoadmapViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        RoadmapStep step = steps.get(position);

        if (holder instanceof TodayViewHolder) {
            TodayViewHolder todayHolder = (TodayViewHolder) holder;
            todayHolder.binding.textUnitTitle.setText(step.getLevel());
            todayHolder.binding.textLessonTitle.setText(step.getTitle());
            todayHolder.binding.textLessonStats.setText(step.getDescription());
            todayHolder.binding.imageLessonIcon.setImageResource(step.getIconRes());
            
            todayHolder.itemView.setOnClickListener(v -> startStudy(v, step));
            
        } else if (holder instanceof RoadmapViewHolder) {
            RoadmapViewHolder roadmapHolder = (RoadmapViewHolder) holder;
            
            // Hiển thị tiêu đề Unit
            String currentUnit = step.getLevel();
            boolean showHeader = false;
            
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
                roadmapHolder.binding.textUnitHeader.setVisibility(View.VISIBLE);
                roadmapHolder.binding.textUnitHeader.setText(currentUnit);
                roadmapHolder.binding.unitDivider.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
            } else {
                roadmapHolder.binding.textUnitHeader.setVisibility(View.GONE);
                roadmapHolder.binding.unitDivider.setVisibility(View.GONE);
            }

            int colorRes = cardColors[unitIndex % cardColors.length];
            roadmapHolder.binding.cardStep.setCardBackgroundColor(ContextCompat.getColor(roadmapHolder.itemView.getContext(), colorRes));

            roadmapHolder.binding.textStepTitle.setText(step.getTitle());
            roadmapHolder.binding.textStepDesc.setText(step.getDescription());
            roadmapHolder.binding.imageStepIcon.setImageResource(step.getIconRes());

            if (step.isLocked()) {
                roadmapHolder.binding.imageLock.setVisibility(View.VISIBLE);
                roadmapHolder.binding.cardStep.setAlpha(0.6f);
            } else {
                roadmapHolder.binding.imageLock.setVisibility(View.GONE);
                roadmapHolder.binding.cardStep.setAlpha(1.0f);
            }

            roadmapHolder.itemView.setOnClickListener(v -> startStudy(v, step));
        }
    }

    private void startStudy(View v, RoadmapStep step) {
        if (step.isLocked()) {
            Toast.makeText(v.getContext(), "Hoàn thành bài học trước để mở khóa!", Toast.LENGTH_SHORT).show();
        } else {
            Intent intent = new Intent(v.getContext(), StudyActivity.class);
            intent.putExtra("lesson_id", step.getId());
            intent.putExtra("lesson_title", step.getTitle());
            v.getContext().startActivity(intent);
        }
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

    static class TodayViewHolder extends RecyclerView.ViewHolder {
        final ItemRoadmapTodayBinding binding;
        public TodayViewHolder(ItemRoadmapTodayBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
