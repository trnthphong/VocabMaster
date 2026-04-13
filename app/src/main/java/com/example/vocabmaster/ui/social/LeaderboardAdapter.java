package com.example.vocabmaster.ui.social;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.databinding.ItemLeaderboardBinding;

import java.util.Objects;

public class LeaderboardAdapter extends ListAdapter<User, LeaderboardAdapter.ViewHolder> {

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
                        Objects.equals(oldItem.getName(), newItem.getName());
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
        holder.bind(getItem(position), position + 1);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemLeaderboardBinding binding;

        public ViewHolder(ItemLeaderboardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(User user, int rank) {
            binding.textRank.setText(String.valueOf(rank));
            binding.textName.setText(user.getName() != null ? user.getName() : "Unknown");
            binding.textXp.setText(user.getXp() + " XP");
            
            String status = user.getCurrentUnitTitle();
            if (status == null || status.isEmpty()) {
                status = "Learning " + (user.getLanguage() != null ? user.getLanguage() : "Vocab");
            }
            binding.textStatus.setText(status);
        }
    }
}
