package com.example.vocabmaster.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Post;
import com.example.vocabmaster.databinding.ItemPostBinding;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class PostAdapter extends ListAdapter<Post, PostAdapter.PostViewHolder> {

    protected PostAdapter() {
        super(new DiffUtil.ItemCallback<Post>() {
            @Override
            public boolean areItemsTheSame(@NonNull Post oldItem, @NonNull Post newItem) {
                return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull Post oldItem, @NonNull Post newItem) {
                return oldItem.getContent().equals(newItem.getContent());
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
        holder.bind(getItem(position));
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        private final ItemPostBinding binding;
        private final SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault());

        public PostViewHolder(ItemPostBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Post post) {
            binding.textUserName.setText(post.getUserName());
            binding.textContent.setText(post.getContent());
            binding.textCourseTitle.setText(post.getCourseTitle());
            binding.textFlashcardCount.setText(post.getFlashcardCount() + " cards");
            if (post.getCreatedAt() != null) {
                binding.textTime.setText(sdf.format(post.getCreatedAt()));
            }

            int resId = R.drawable.bear;
            if (post.getUserAvatar() != null) {
                String avatar = post.getUserAvatar();
                if (avatar.equals("cat")) resId = R.drawable.cat;
                else if (avatar.equals("dog")) resId = R.drawable.dog;
                else if (avatar.equals("bird")) resId = R.drawable.bird;
                else if (avatar.equals("snake")) resId = R.drawable.snake;
                else if (avatar.equals("tiger")) resId = R.drawable.tiger;
                else if (avatar.equals("rabbit")) resId = R.drawable.rabbit;
            }
            binding.imageAvatar.setImageResource(resId);
        }
    }
}
