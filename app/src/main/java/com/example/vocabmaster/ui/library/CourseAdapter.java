package com.example.vocabmaster.ui.library;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.databinding.ItemCourseBinding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CourseAdapter extends ListAdapter<Course, CourseAdapter.CourseViewHolder> {

    private final OnCourseClickListener listener;
    private final OnCourseClickListener longClickListener;
    private final OnSelectionChangeListener selectionChangeListener;
    
    private boolean isSelectionMode = false;
    private final Set<String> selectedCourseIds = new HashSet<>();

    public interface OnCourseClickListener {
        void onCourseClick(Course course);
    }

    public interface OnSelectionChangeListener {
        void onSelectionChanged(int count);
    }

    protected CourseAdapter(OnCourseClickListener listener, 
                          OnCourseClickListener longClickListener,
                          OnSelectionChangeListener selectionChangeListener) {
        super(new DiffUtil.ItemCallback<Course>() {
            @Override
            public boolean areItemsTheSame(@NonNull Course oldItem, @NonNull Course newItem) {
                return oldItem.getFirestoreId() != null && oldItem.getFirestoreId().equals(newItem.getFirestoreId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull Course oldItem, @NonNull Course newItem) {
                return oldItem.getTitle().equals(newItem.getTitle()) &&
                        oldItem.getLevel() == newItem.getLevel();
            }
        });
        this.listener = listener;
        this.longClickListener = longClickListener;
        this.selectionChangeListener = selectionChangeListener;
    }

    public void setSelectionMode(boolean enabled) {
        if (this.isSelectionMode != enabled) {
            this.isSelectionMode = enabled;
            if (!enabled) selectedCourseIds.clear();
            notifyDataSetChanged();
            if (selectionChangeListener != null) {
                selectionChangeListener.onSelectionChanged(selectedCourseIds.size());
            }
        }
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public List<Course> getSelectedCourses() {
        List<Course> selected = new ArrayList<>();
        for (Course course : getCurrentList()) {
            if (selectedCourseIds.contains(course.getFirestoreId())) {
                selected.add(course);
            }
        }
        return selected;
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
        holder.bind(course, isSelectionMode, selectedCourseIds.contains(course.getFirestoreId()));
        
        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(course);
            } else {
                listener.onCourseClick(course);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode) {
                setSelectionMode(true);
                toggleSelection(course);
                return true;
            }
            return false;
        });
        
        holder.binding.iconMore.setOnClickListener(v -> {
            if (!isSelectionMode) {
                longClickListener.onCourseClick(course);
            }
        });
    }

    private void toggleSelection(Course course) {
        String id = course.getFirestoreId();
        if (selectedCourseIds.contains(id)) {
            selectedCourseIds.remove(id);
        } else {
            selectedCourseIds.add(id);
        }
        notifyItemChanged(getCurrentList().indexOf(course));
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedCourseIds.size());
        }
    }

    static class CourseViewHolder extends RecyclerView.ViewHolder {
        private final ItemCourseBinding binding;

        public CourseViewHolder(ItemCourseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Course course, boolean isSelectionMode, boolean isSelected) {
            binding.textCourseTitle.setText(course.getTitle());
            binding.textFlashcardCount.setText(course.getFlashcardCount() + " cards");
            binding.textCourseLevel.setText("Lvl " + course.getLevel());
            
            // Selection UI
            binding.getRoot().setChecked(isSelected);
            binding.getRoot().setStrokeColor(isSelected ? 
                binding.getRoot().getContext().getColor(R.color.brand_primary) : 
                binding.getRoot().getContext().getColor(R.color.card_border));
            
            binding.iconMore.setVisibility(isSelectionMode ? View.INVISIBLE : View.VISIBLE);

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
