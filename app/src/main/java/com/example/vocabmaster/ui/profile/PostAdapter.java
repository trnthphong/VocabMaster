package com.example.vocabmaster.ui.profile;

import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.Post;
import com.example.vocabmaster.databinding.ItemPostBinding;
import com.example.vocabmaster.ui.library.CourseDetailActivity;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class PostAdapter extends ListAdapter<Post, PostAdapter.PostViewHolder> {

    private OnPostActionListener listener;

    public interface OnPostActionListener {
        void onLikeClick(Post post);
        void onCommentClick(Post post);
        void onCopyCourseClick(Post post);
    }

    public void setOnPostActionListener(OnPostActionListener listener) {
        this.listener = listener;
    }

    protected PostAdapter() {
        super(new DiffUtil.ItemCallback<Post>() {
            @Override
            public boolean areItemsTheSame(@NonNull Post oldItem, @NonNull Post newItem) {
                return oldItem.getId().equals(newItem.getId());
            }
            @Override
            public boolean areContentsTheSame(@NonNull Post oldItem, @NonNull Post newItem) {
                return oldItem.getLikes().size() == newItem.getLikes().size() && 
                       oldItem.getCommentCount() == newItem.getCommentCount();
            }
        });
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPostBinding binding = ItemPostBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new PostViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        private final ItemPostBinding binding;
        private final SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault());

        public PostViewHolder(ItemPostBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Post post, OnPostActionListener listener) {
            binding.textUserName.setText(post.getUserName());
            binding.textTime.setText(post.getCreatedAt() != null ? sdf.format(post.getCreatedAt()) : "");
            binding.textContent.setText(post.getContent());
            binding.textCourseTitle.setText(post.getCourseTitle());
            binding.textFlashcardCount.setText(post.getFlashcardCount() + " cards");
            
            // Like state
            String currentUid = FirebaseAuth.getInstance().getUid();
            boolean isLiked = post.getLikes().contains(currentUid);
            binding.imageLike.setImageResource(isLiked ? R.drawable.heart : R.drawable.heart); // Use fill/outline if available
            binding.imageLike.setColorFilter(isLiked ? Color.RED : Color.GRAY);
            binding.textLikeCount.setText(String.valueOf(post.getLikes().size()));
            binding.textCommentCount.setText(String.valueOf(post.getCommentCount()));

            // Avatar
            int resId = R.drawable.bear;
            if ("cat".equals(post.getUserAvatar())) resId = R.drawable.cat;
            else if ("dog".equals(post.getUserAvatar())) resId = R.drawable.dog;
            binding.imageAvatar.setImageResource(resId);

            // Events
            binding.layoutLike.setOnClickListener(v -> { if(listener != null) listener.onLikeClick(post); });
            binding.layoutComment.setOnClickListener(v -> { if(listener != null) listener.onCommentClick(post); });
            binding.btnCopyCourse.setOnClickListener(v -> { if(listener != null) listener.onCopyCourseClick(post); });
            
            binding.cardCoursePreview.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), CourseDetailActivity.class);
                intent.putExtra("course_id", post.getCourseId());
                intent.putExtra("course_title", post.getCourseTitle());
                intent.putExtra("is_personal", false); // Viewing shared course
                v.getContext().startActivity(intent);
            });
        }
    }
}
