package com.example.vocabmaster.ui.study;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocabmaster.data.repository.FlashcardStudyRepository;
import com.example.vocabmaster.databinding.ItemSrsStudySetBinding;
import com.example.vocabmaster.ui.common.MotionSystem;

import java.util.ArrayList;
import java.util.List;

public class FlashcardStudySetAdapter extends RecyclerView.Adapter<FlashcardStudySetAdapter.ViewHolder> {
    public interface OnStudySetClickListener {
        void onStudySetClick(FlashcardStudyRepository.StudySetSummary summary);
    }

    private final OnStudySetClickListener listener;
    private final List<FlashcardStudyRepository.StudySetSummary> items = new ArrayList<>();

    public FlashcardStudySetAdapter(OnStudySetClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<FlashcardStudyRepository.StudySetSummary> summaries) {
        items.clear();
        if (summaries != null) items.addAll(summaries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSrsStudySetBinding binding = ItemSrsStudySetBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemSrsStudySetBinding binding;

        ViewHolder(ItemSrsStudySetBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            MotionSystem.applyPressState(binding.getRoot());
        }

        void bind(FlashcardStudyRepository.StudySetSummary summary, OnStudySetClickListener listener) {
            binding.textSetTitle.setText(summary.getTitle());
            binding.textSetSubtitle.setText(summary.getTotalCards() + " thẻ · " + summary.getSubtitle());
            binding.getRoot().setOnClickListener(v -> listener.onStudySetClick(summary));
        }
    }
}
