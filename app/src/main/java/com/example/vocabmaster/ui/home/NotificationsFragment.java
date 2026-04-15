package com.example.vocabmaster.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Notification;
import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.databinding.FragmentNotificationsBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationsFragment extends Fragment {
    private static final String TAG = "NotificationsFragment";
    private FragmentNotificationsBinding binding;
    private FirebaseFirestore db;
    private String currentUserId;
    private NotificationAdapter adapter;
    private User currentUserData;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.toolbarNotifications.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        loadCurrentUserData();
        setupRecyclerView();
        loadNotifications();
        markAllAsRead();
    }

    private void loadCurrentUserData() {
        if (currentUserId == null) return;
        db.collection("users").document(currentUserId).get().addOnSuccessListener(snapshot -> {
            currentUserData = snapshot.toObject(User.class);
            if (currentUserData != null) currentUserData.setUid(currentUserId);
        });
    }

    private void setupRecyclerView() {
        adapter = new NotificationAdapter();
        binding.recyclerNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerNotifications.setAdapter(adapter);

        adapter.setOnNotificationActionListener(new NotificationAdapter.OnNotificationActionListener() {
            @Override
            public void onDelete(Notification notification) {
                deleteNotification(notification);
            }

            @Override
            public void onAction(Notification notification) {
                if ("follow".equals(notification.getType())) {
                    handleFollowBack(notification);
                }
            }
        });
    }

    private void loadNotifications() {
        if (currentUserId == null) return;
        db.collection("users").document(currentUserId).collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Listen failed.", e);
                        return;
                    }

                    if (snapshot != null) {
                        List<Notification> notifications = new ArrayList<>();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Notification notification = doc.toObject(Notification.class);
                            if (notification != null) {
                                notification.setId(doc.getId());
                                notifications.add(notification);
                            }
                        }

                        if (notifications.isEmpty()) {
                            binding.layoutEmptyNotifications.setVisibility(View.VISIBLE);
                            binding.recyclerNotifications.setVisibility(View.GONE);
                        } else {
                            binding.layoutEmptyNotifications.setVisibility(View.GONE);
                            binding.recyclerNotifications.setVisibility(View.VISIBLE);
                            adapter.setNotifications(notifications);
                        }
                    }
                });
    }

    private void markAllAsRead() {
        if (currentUserId == null) return;
        db.collection("users").document(currentUserId).collection("notifications")
                .whereEqualTo("read", false)
                .get()
                .addOnSuccessListener(qs -> {
                    if (!qs.isEmpty()) {
                        WriteBatch batch = db.batch();
                        for (DocumentSnapshot doc : qs.getDocuments()) {
                            batch.update(doc.getReference(), "read", true);
                        }
                        batch.commit();
                    }
                });
    }

    private void deleteNotification(Notification notification) {
        if (currentUserId == null || notification.getId() == null) return;
        db.collection("users").document(currentUserId)
                .collection("notifications").document(notification.getId())
                .delete()
                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Đã xóa thông báo", Toast.LENGTH_SHORT).show());
    }

    private void handleFollowBack(Notification notification) {
        String targetUid = notification.getFromUserId();
        if (currentUserId == null || targetUid == null) return;

        WriteBatch batch = db.batch();
        Map<String, Object> followData = new HashMap<>();
        followData.put("timestamp", FieldValue.serverTimestamp());

        // Update relationships
        batch.set(db.collection("users").document(currentUserId).collection("following").document(targetUid), followData);
        batch.set(db.collection("users").document(targetUid).collection("followers").document(currentUserId), followData);

        // Update friendsCount for both
        batch.update(db.collection("users").document(currentUserId), "friendsCount", FieldValue.increment(1));
        batch.update(db.collection("users").document(targetUid), "friendsCount", FieldValue.increment(1));

        // Update THIS notification
        Map<String, Object> updateNotif = new HashMap<>();
        updateNotif.put("type", "friend");
        updateNotif.put("title", "Bạn bè mới");
        updateNotif.put("message", "Bạn và " + notification.getFromUserName() + " đã trở thành bạn bè.");
        batch.update(db.collection("users").document(currentUserId).collection("notifications").document(notification.getId()), updateNotif);

        // Send a notification to the other user too
        if (currentUserData != null) {
            Notification otherNotif = new Notification(
                    "friend",
                    "Bạn bè mới",
                    "Bạn và " + currentUserData.getName() + " đã trở thành bạn bè.",
                    currentUserId,
                    currentUserData.getName()
            );
            otherNotif.setFromUserAvatar(currentUserData.getAvatar());
            batch.set(db.collection("users").document(targetUid).collection("notifications").document(), otherNotif);
        }

        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(getContext(), "Hai bạn đã trở thành bạn bè!", Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(e -> {
            Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }
}
