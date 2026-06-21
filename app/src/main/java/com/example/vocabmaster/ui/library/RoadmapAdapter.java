package com.example.vocabmaster.ui.library;

import android.content.res.ColorStateList;
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
import com.example.vocabmaster.databinding.ItemRoadmapTodayBinding;
import com.example.vocabmaster.ui.study.StudyActivity;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class RoadmapAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int VIEW_TYPE_TODAY = 0;
    public static final int VIEW_TYPE_OVERVIEW = 1;

    private final List<RoadmapStep> steps;
    private final int viewType;
    private String courseId;
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

    public void setCourseId(String courseId) {
        this.courseId = courseId;
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
            bindTodayCompletionState(todayHolder, step);
            
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
            bindOverviewCompletionState(roadmapHolder, step);

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

    private void bindTodayCompletionState(TodayViewHolder holder, RoadmapStep step) {
        MaterialCardView rootCard = (MaterialCardView) holder.binding.getRoot();
        MaterialCardView iconCard = (MaterialCardView) holder.binding.imageLessonIcon.getParent();
        if (step.isCompleted()) {
            holder.binding.imageLessonIcon.setImageResource(R.drawable.ic_check);
            holder.binding.imageLessonIcon.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.white));
            iconCard.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.success));
            rootCard.setStrokeColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.success));
            rootCard.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.light_green));
        } else {
            holder.binding.imageLessonIcon.clearColorFilter();
            iconCard.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_primary_light));
            rootCard.setStrokeColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_primary_light));
            rootCard.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.white));
        }
    }

    private void bindOverviewCompletionState(RoadmapViewHolder holder, RoadmapStep step) {
        if (step.isCompleted()) {
            holder.binding.imageStepIcon.setImageResource(R.drawable.trophy);
            // Light up with Gold color
            holder.binding.imageStepIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#FFD700")));
            holder.binding.imageStepIcon.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFF8E1")));
            holder.binding.imageStepIcon.setScaleX(1.15f);
            holder.binding.imageStepIcon.setScaleY(1.15f);
        } else {
            holder.binding.imageStepIcon.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.white)));
            holder.binding.imageStepIcon.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.blue)));
            holder.binding.imageStepIcon.setScaleX(1.0f);
            holder.binding.imageStepIcon.setScaleY(1.0f);
        }
    }

    private void startStudy(View v, RoadmapStep step) {
        if (step.isLocked()) {
            Toast.makeText(v.getContext(), "Hoàn thành bài học trước để mở khóa!", Toast.LENGTH_SHORT).show();
        } else {
            Intent intent = new Intent(v.getContext(), StudyActivity.class);
            intent.putExtra("lesson_id", step.getId());
            intent.putExtra("lesson_title", step.getTitle());
            intent.putExtra("course_id", courseId);

            // Tìm bài học tiếp theo
            int currentIndex = -1;
            for (int i = 0; i < steps.size(); i++) {
                if (steps.get(i).getId().equals(step.getId())) {
                    currentIndex = i;
                    break;
                }
            }

            if (currentIndex >= 0 && currentIndex < steps.size() - 1) {
                RoadmapStep nextStep = steps.get(currentIndex + 1);
                intent.putExtra("next_lesson_id", nextStep.getId());
                intent.putExtra("next_lesson_title", nextStep.getTitle());
            }

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
