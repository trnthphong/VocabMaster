package com.example.vocabmaster.ui.profile;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Comment;
import com.example.vocabmaster.databinding.ItemCommentBinding;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class CommentAdapter extends ListAdapter<Comment, CommentAdapter.CommentViewHolder> {

    protected CommentAdapter() {
        super(new DiffUtil.ItemCallback<Comment>() {
            @Override
            public boolean areItemsTheSame(@NonNull Comment oldItem, @NonNull Comment newItem) {
                return oldItem.getId().equals(newItem.getId());
            }
            @Override
            public boolean areContentsTheSame(@NonNull Comment oldItem, @NonNull Comment newItem) {
                return oldItem.getContent().equals(newItem.getContent());
            }
        });
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCommentBinding binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new CommentViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {
        private final ItemCommentBinding binding;
        private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm, dd MMM", Locale.getDefault());

        public CommentViewHolder(ItemCommentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Comment comment) {
            binding.textUserName.setText(comment.getUserName());
            binding.textContent.setText(comment.getContent());
            binding.textTime.setText(comment.getCreatedAt() != null ? sdf.format(comment.getCreatedAt()) : "");
            
            int resId = R.drawable.bear;
            if ("cat".equals(comment.getUserAvatar())) resId = R.drawable.cat;
            else if ("dog".equals(comment.getUserAvatar())) resId = R.drawable.dog;
            binding.imageAvatar.setImageResource(resId);
        }
    }
}
