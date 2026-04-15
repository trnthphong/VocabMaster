package com.example.vocabmaster.ui.social;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.databinding.ItemLeaderboardBinding;

import java.util.Objects;

public class LeaderboardAdapter extends ListAdapter<User, LeaderboardAdapter.ViewHolder> {

    private OnUserClickListener listener;
    private boolean showRank = true;
    private final String[] avatarValues = {"bear", "cat", "dog", "bird", "snake", "tiger", "rabbit"};
    private final int[] avatarResIds = {
            R.drawable.bear, R.drawable.cat, R.drawable.dog,
            R.drawable.bird, R.drawable.snake, R.drawable.tiger,
            R.drawable.rabbit
    };

    public interface OnUserClickListener {
        void onUserClick(User user);
    }

    public void setOnUserClickListener(OnUserClickListener listener) {
        this.listener = listener;
    }

    public void setShowRank(boolean showRank) {
        this.showRank = showRank;
        notifyDataSetChanged();
    }

    public LeaderboardAdapter() {
        super(new DiffUtil.ItemCallback<User>() {
            @Override
            public boolean areItemsTheSame(@NonNull User oldItem, @NonNull User newItem) {
                if (oldItem.getUid() == null || newItem.getUid() == null) return false;
                return oldItem.getUid().equals(newItem.getUid());
            }

            @Override
            public boolean areContentsTheSame(@NonNull User oldItem, @NonNull User newItem) {
                return oldItem.getXp() == newItem.getXp() &&
                        Objects.equals(oldItem.getName(), newItem.getName()) &&
                        Objects.equals(oldItem.getAvatar(), newItem.getAvatar());
            }
        });
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLeaderboardBinding binding = ItemLeaderboardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), position + 1, listener, avatarValues, avatarResIds, showRank);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemLeaderboardBinding binding;

        public ViewHolder(ItemLeaderboardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(User user, int rank, OnUserClickListener listener, String[] avatarValues, int[] avatarResIds, boolean showRank) {
            if (showRank) {
                binding.textRank.setVisibility(View.VISIBLE);
                binding.textRank.setText(String.valueOf(rank));
            } else {
                binding.textRank.setVisibility(View.GONE);
            }

            binding.textName.setText(user.getName() != null ? user.getName() : "Unknown");
            binding.textXp.setText(user.getXp() + " XP");
            
            String status = user.getCurrentUnitTitle();
            if (status == null || status.isEmpty()) {
                status = "Learning " + (user.getLanguage() != null ? user.getLanguage() : "Vocab");
            }
            binding.textStatus.setText(status);

            int resId = R.drawable.bear;
            String avatar = user.getAvatar();
            if (avatar != null) {
                for (int i = 0; i < avatarValues.length; i++) {
                    if (avatarValues[i].equals(avatar)) {
                        resId = avatarResIds[i];
                        break;
                    }
                }
            }
            binding.imageAvatar.setImageResource(resId);

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onUserClick(user);
            });
        }
    }
}
