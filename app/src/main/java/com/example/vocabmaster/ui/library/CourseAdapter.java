package com.example.vocabmaster.ui.library;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.databinding.ItemCourseBinding;

public class CourseAdapter extends ListAdapter<Course, CourseAdapter.CourseViewHolder> {

    private final OnCourseClickListener listener;
    private final OnCourseClickListener longClickListener;

    public interface OnCourseClickListener {
        void onCourseClick(Course course);
    }

    protected CourseAdapter(OnCourseClickListener listener, OnCourseClickListener longClickListener) {
        super(new DiffUtil.ItemCallback<Course>() {
            @Override
            public boolean areItemsTheSame(@NonNull Course oldItem, @NonNull Course newItem) {
                return oldItem.getId() == newItem.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull Course oldItem, @NonNull Course newItem) {
                return oldItem.getTitle().equals(newItem.getTitle()) &&
                        oldItem.getLevel() == newItem.getLevel();
            }
        });
        this.listener = listener;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public CourseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCourseBinding binding = ItemCourseBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new CourseViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CourseViewHolder holder, int position) {
        Course course = getItem(position);
        holder.bind(course);
        holder.itemView.setOnClickListener(v -> listener.onCourseClick(course));
        holder.itemView.setOnLongClickListener(v -> {
            longClickListener.onCourseClick(course);
            return true;
        });
    }

    static class CourseViewHolder extends RecyclerView.ViewHolder {
        private final ItemCourseBinding binding;

        public CourseViewHolder(ItemCourseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Course course) {
            binding.textCourseTitle.setText(course.getTitle());
            binding.textFlashcardCount.setText(course.getFlashcardCount() + " cards");
            binding.textCourseLevel.setText("Lvl " + course.getLevel());
            
            // Guess flag from title
            int flagRes = guessFlagFromText(course.getTitle());
            binding.imgCourseFlag.setImageResource(flagRes);
        }

        private int guessFlagFromText(String text) {
            if (text == null) return R.drawable.vietnam;
            String lower = text.toLowerCase();
            if (lower.contains("anh") || lower.contains("english")) return R.drawable.eng;
            if (lower.contains("nhật") || lower.contains("japan") || lower.contains("japanese")) return R.drawable.japan;
            if (lower.contains("trung") || lower.contains("china") || lower.contains("chinese")) return R.drawable.china;
            if (lower.contains("nga") || lower.contains("russia") || lower.contains("russian")) return R.drawable.russia;
            return R.drawable.vietnam; // Default
        }
    }
}