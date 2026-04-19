package com.example.vocabmaster.ui.social;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.databinding.ItemLeaderboardBinding;
import com.example.vocabmaster.databinding.ItemLeaderboardPodiumBinding;
import com.example.vocabmaster.databinding.ItemPodiumBinding;

import java.util.List;
import java.util.Objects;

public class LeaderboardAdapter extends ListAdapter<User, RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_PODIUM = 0;
    private static final int VIEW_TYPE_LIST = 1;

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

    @Override
    public int getItemViewType(int position) {
        if (showRank && position == 0) {
            return VIEW_TYPE_PODIUM;
        }
        return VIEW_TYPE_LIST;
    }

    @Override
    public int getItemCount() {
        List<User> list = getCurrentList();
        if (list.isEmpty()) return 0;
        if (!showRank) return list.size();
        
        if (list.size() <= 3) return 1;
        return 1 + (list.size() - 3);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_PODIUM) {
            ItemLeaderboardPodiumBinding binding = ItemLeaderboardPodiumBinding.inflate(inflater, parent, false);
            return new PodiumViewHolder(binding);
        } else {
            ItemLeaderboardBinding binding = ItemLeaderboardBinding.inflate(inflater, parent, false);
            return new ListViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PodiumViewHolder) {
            List<User> list = getCurrentList();
            User first = list.size() > 0 ? list.get(0) : null;
            User second = list.size() > 1 ? list.get(1) : null;
            User third = list.size() > 2 ? list.get(2) : null;
            ((PodiumViewHolder) holder).bind(first, second, third, listener, avatarValues, avatarResIds);
        } else {
            int actualIndex = showRank ? position + 2 : position;
            if (actualIndex >= 0 && actualIndex < getCurrentList().size()) {
                ((ListViewHolder) holder).bind(getCurrentList().get(actualIndex), actualIndex + 1, listener, avatarValues, avatarResIds, showRank);
            }
        }
    }

    static class PodiumViewHolder extends RecyclerView.ViewHolder {
        private final ItemLeaderboardPodiumBinding binding;

        public PodiumViewHolder(ItemLeaderboardPodiumBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(User first, User second, User third, OnUserClickListener listener, String[] avatarValues, int[] avatarResIds) {
            setupPodium(binding.podiumFirst, first, 1, listener, avatarValues, avatarResIds);
            setupPodium(binding.podiumSecond, second, 2, listener, avatarValues, avatarResIds);
            setupPodium(binding.podiumThird, third, 3, listener, avatarValues, avatarResIds);
        }

        private void setupPodium(ItemPodiumBinding podiumBinding, User user, int rank, OnUserClickListener listener, String[] avatarValues, int[] avatarResIds) {
            if (user == null) {
                podiumBinding.getRoot().setVisibility(View.INVISIBLE);
                return;
            }
            podiumBinding.getRoot().setVisibility(View.VISIBLE);
            podiumBinding.textName.setText(user.getName() != null ? user.getName() : "Unknown");
            podiumBinding.textXp.setText(user.getXp() + " XP");
            podiumBinding.textRankBadge.setText(String.valueOf(rank));

            int baseColor;
            int badgeColor;
            int height;
            float density = itemView.getContext().getResources().getDisplayMetrics().density;

            if (rank == 1) {
                baseColor = ContextCompat.getColor(itemView.getContext(), R.color.warning);
                badgeColor = ContextCompat.getColor(itemView.getContext(), R.color.warning);
                height = (int) (120 * density);
            } else if (rank == 2) {
                baseColor = ContextCompat.getColor(itemView.getContext(), R.color.gray_light);
                badgeColor = ContextCompat.getColor(itemView.getContext(), R.color.gray_light);
                height = (int) (90 * density);
            } else {
                baseColor = ContextCompat.getColor(itemView.getContext(), R.color.accent_orange);
                badgeColor = ContextCompat.getColor(itemView.getContext(), R.color.accent_orange);
                height = (int) (70 * density);
            }

            podiumBinding.viewPodiumColor.setBackgroundColor(baseColor);
            podiumBinding.textRankBadge.getBackground().setTint(badgeColor);
            
            ViewGroup.LayoutParams params = podiumBinding.cardPodiumBase.getLayoutParams();
            params.height = height;
            podiumBinding.cardPodiumBase.setLayoutParams(params);

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
            podiumBinding.imageAvatar.setImageResource(resId);
            podiumBinding.imageAvatar.setStrokeColor(ColorStateList.valueOf(badgeColor));

            podiumBinding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onUserClick(user);
            });
        }
    }

    static class ListViewHolder extends RecyclerView.ViewHolder {
        private final ItemLeaderboardBinding binding;

        public ListViewHolder(ItemLeaderboardBinding binding) {
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
