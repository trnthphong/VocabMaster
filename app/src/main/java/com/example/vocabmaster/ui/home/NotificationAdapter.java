package com.example.vocabmaster.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Notification;
import com.example.vocabmaster.databinding.ItemNotificationBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {
    private List<Notification> notifications = new ArrayList<>();
    private OnNotificationActionListener actionListener;

    public interface OnNotificationActionListener {
        void onDelete(Notification notification);
        void onAction(Notification notification);
    }

    public void setOnNotificationActionListener(OnNotificationActionListener listener) {
        this.actionListener = listener;
    }

    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNotificationBinding binding = ItemNotificationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Notification notification = notifications.get(position);
        holder.bind(notification);
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemNotificationBinding binding;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm, dd/MM/yyyy", Locale.getDefault());

        ViewHolder(ItemNotificationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Notification notification) {
            binding.textNotifMessage.setText(notification.getMessage());
            
            if (notification.getTimestamp() != null) {
                binding.textNotifTime.setText(dateFormat.format(notification.getTimestamp().toDate()));
            }

            int avatarRes = R.drawable.bear;
            if (notification.getFromUserAvatar() != null) {
                String avatar = notification.getFromUserAvatar();
                String[] avatarValues = {"bear", "cat", "dog", "bird", "snake", "tiger", "rabbit"};
                int[] avatarResIds = {R.drawable.bear, R.drawable.cat, R.drawable.dog, R.drawable.bird, R.drawable.snake, R.drawable.tiger, R.drawable.rabbit};
                for (int i = 0; i < avatarValues.length; i++) {
                    if (avatarValues[i].equals(avatar)) {
                        avatarRes = avatarResIds[i];
                        break;
                    }
                }
            }
            binding.imgNotifUser.setImageResource(avatarRes);

            if ("follow".equals(notification.getType())) {
                binding.btnNotifAction.setVisibility(View.VISIBLE);
                binding.btnNotifAction.setText("Theo dõi lại");
            } else {
                binding.btnNotifAction.setVisibility(View.GONE);
            }

            binding.btnNotifDelete.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onDelete(notification);
            });

            binding.btnNotifAction.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onAction(notification);
            });
            
            // Highlight unread notifications
            if (!notification.isRead()) {
                binding.cardNotification.setCardBackgroundColor(binding.getRoot().getContext().getColor(R.color.brand_primary_light));
            } else {
                binding.cardNotification.setCardBackgroundColor(binding.getRoot().getContext().getColor(R.color.white));
            }
        }
    }
}
