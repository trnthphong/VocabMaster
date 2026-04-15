package com.example.vocabmaster.ui.social;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Notification;
import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.databinding.DialogSearchFriendBinding;
import com.example.vocabmaster.databinding.FragmentSocialBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SocialFragment extends Fragment {
    private static final String TAG = "SocialFragment";
    private FragmentSocialBinding binding;
    private FirebaseFirestore db;
    private LeaderboardAdapter adapter;
    private String currentUserId;
    private User currentUserData;
    private final List<String> followingIds = new ArrayList<>();
    private final List<String> followerIds = new ArrayList<>();
    private ListenerRegistration followingListener;
    private ListenerRegistration followerListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSocialBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        setupRecyclerView();
        setupTabs();
        loadCurrentUserData();
        startFollowingFollowerListeners();
        setupSearch();

        loadLeaderboard();
    }

    private void loadCurrentUserData() {
        if (currentUserId == null) return;
        db.collection("users").document(currentUserId).get().addOnSuccessListener(snapshot -> {
            currentUserData = snapshot.toObject(User.class);
            if (currentUserData != null) currentUserData.setUid(currentUserId);
        });
    }

    private void setupRecyclerView() {
        adapter = new LeaderboardAdapter();
        adapter.setOnUserClickListener(this::showUserActionDialog);
        binding.recyclerLeaderboard.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerLeaderboard.setAdapter(adapter);
    }

    private void setupTabs() {
        binding.socialTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    adapter.setShowRank(true);
                    loadLeaderboard();
                } else {
                    adapter.setShowRank(false);
                    loadFriends();
                }
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupSearch() {
        binding.btnSearch.setOnClickListener(v -> performSearch());
        binding.editSearchEmail.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });
    }

    private void performSearch() {
        String input = binding.editSearchEmail.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            Toast.makeText(getContext(), "Vui lòng nhập Email hoặc mã ID", Toast.LENGTH_SHORT).show();
            return;
        }

        Query query;
        if (input.matches("\\d{6}")) {
            query = db.collection("users").whereEqualTo("shortId", input);
        } else {
            query = db.collection("users").whereEqualTo("email", input);
        }

        query.get().addOnSuccessListener(qs -> {
            if (!qs.isEmpty()) {
                User foundUser = qs.getDocuments().get(0).toObject(User.class);
                if (foundUser != null) {
                    if (foundUser.getUid() == null) {
                        foundUser.setUid(qs.getDocuments().get(0).getId());
                    }
                    showUserActionDialog(foundUser);
                }
            } else {
                Toast.makeText(getContext(), "Không tìm thấy người dùng này", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> Toast.makeText(getContext(), "Lỗi tìm kiếm", Toast.LENGTH_SHORT).show());
    }

    private void showUserActionDialog(User user) {
        if (user.getUid().equals(currentUserId)) return;

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        DialogSearchFriendBinding dialogBinding = DialogSearchFriendBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());

        dialogBinding.layoutSearch.setVisibility(View.GONE);
        dialogBinding.btnSearch.setVisibility(View.GONE);

        dialogBinding.itemSearchResult.getRoot().setVisibility(View.VISIBLE);
        dialogBinding.itemSearchResult.textRank.setVisibility(View.GONE);
        dialogBinding.itemSearchResult.textName.setText(user.getName() != null ? user.getName() : "Unknown");
        dialogBinding.itemSearchResult.textXp.setText(user.getXp() + " XP");
        dialogBinding.itemSearchResult.textStatus.setText(user.getCurrentUnitTitle());

        updateDialogButtonState(user, dialogBinding);

        dialogBinding.btnFollow.setOnClickListener(v -> {
            toggleFollow(user);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateDialogButtonState(User user, DialogSearchFriendBinding dialogBinding) {
        boolean isFollowing = followingIds.contains(user.getUid());
        boolean isFollower = followerIds.contains(user.getUid());
        boolean isFriend = isFollowing && isFollower;

        dialogBinding.btnFollow.setVisibility(View.VISIBLE);
        if (isFriend) {
            dialogBinding.btnFollow.setText("Hủy kết bạn");
            dialogBinding.btnFollow.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.error));
        } else if (isFollowing) {
            dialogBinding.btnFollow.setText("Bỏ theo dõi");
            dialogBinding.btnFollow.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brand_primary));
        } else if (isFollower) {
            dialogBinding.btnFollow.setText("Theo dõi lại");
            dialogBinding.btnFollow.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brand_primary));
        } else {
            dialogBinding.btnFollow.setText("Theo dõi");
            dialogBinding.btnFollow.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brand_primary));
        }
    }

    private void startFollowingFollowerListeners() {
        if (currentUserId == null) return;
        
        // Listen to who I am following
        followingListener = db.collection("users").document(currentUserId).collection("following")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) return;
                    followingIds.clear();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            followingIds.add(doc.getId());
                        }
                    }
                    refreshCurrentTab();
                });

        // Listen to who is following me
        followerListener = db.collection("users").document(currentUserId).collection("followers")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) return;
                    followerIds.clear();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            followerIds.add(doc.getId());
                        }
                    }
                    refreshCurrentTab();
                });
    }

    private void refreshCurrentTab() {
        if (binding == null) return;
        if (binding.socialTabs.getSelectedTabPosition() == 0) {
            loadLeaderboard();
        } else {
            loadFriends();
        }
    }

    private void loadLeaderboard() {
        showLoading(true);
        db.collection("users")
                .orderBy("xp", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener(qs -> {
                    if (!isAdded()) return;
                    List<User> users = qs.toObjects(User.class);
                    for (int i = 0; i < users.size(); i++) {
                        if (users.get(i).getUid() == null) {
                            users.get(i).setUid(qs.getDocuments().get(i).getId());
                        }
                    }
                    adapter.submitList(users);
                    showLoading(false);
                    updateUserRankCard(users);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    if (binding != null) {
                        binding.textLeaderboardEmpty.setText("Không thể tải bảng xếp hạng");
                    }
                });
    }

    private void loadFriends() {
        if (currentUserId == null) return;
        
        showLoading(true);
        List<String> friendIds = new ArrayList<>();
        for (String id : followingIds) {
            if (followerIds.contains(id)) {
                friendIds.add(id);
            }
        }

        if (friendIds.isEmpty()) {
            adapter.submitList(new ArrayList<>());
            showLoading(false);
            if (binding != null) {
                binding.textLeaderboardEmpty.setText("Bạn chưa có bạn bè.");
            }
            // Still show my own rank card
            if (currentUserData != null) {
                updateUserRankCard(Collections.singletonList(currentUserData));
            }
            return;
        }

        // Include current user in fetch to calculate rank correctly
        List<String> fetchIds = new ArrayList<>(friendIds);
        if (!fetchIds.contains(currentUserId)) {
            fetchIds.add(currentUserId);
        }

        db.collection("users")
                .whereIn(FieldPath.documentId(), fetchIds)
                .get()
                .addOnSuccessListener(qs -> {
                    if (!isAdded()) return;
                    List<User> allUsers = qs.toObjects(User.class);
                    for (int i = 0; i < allUsers.size(); i++) {
                        if (allUsers.get(i).getUid() == null) {
                            allUsers.get(i).setUid(qs.getDocuments().get(i).getId());
                        }
                    }
                    allUsers.sort((u1, u2) -> Long.compare(u2.getXp(), u1.getXp()));
                    
                    // Update my sticky rank card using the list including me
                    updateUserRankCard(allUsers);
                    
                    // Filter out current user from the list that goes into the RecyclerView
                    List<User> onlyFriends = allUsers.stream()
                            .filter(u -> !u.getUid().equals(currentUserId))
                            .collect(Collectors.toList());
                    
                    adapter.submitList(onlyFriends);
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    if (binding != null) {
                        binding.textLeaderboardEmpty.setText("Lỗi tải danh sách bạn bè");
                    }
                });
    }

    private void toggleFollow(User targetUser) {
        if (currentUserId == null || targetUser.getUid() == null) return;
        
        boolean currentlyFollowing = followingIds.contains(targetUser.getUid());
        boolean isFollowerOfMe = followerIds.contains(targetUser.getUid());
        String targetUid = targetUser.getUid();

        WriteBatch batch = db.batch();

        if (currentlyFollowing) {
            // Unfollow
            batch.delete(db.collection("users").document(currentUserId).collection("following").document(targetUid));
            batch.delete(db.collection("users").document(targetUid).collection("followers").document(currentUserId));
            
            // If they were friends, decrement friendsCount
            if (isFollowerOfMe) {
                batch.update(db.collection("users").document(currentUserId), "friendsCount", FieldValue.increment(-1));
                batch.update(db.collection("users").document(targetUid), "friendsCount", FieldValue.increment(-1));
            }

            batch.commit().addOnSuccessListener(aVoid -> {
                Toast.makeText(getContext(), "Đã bỏ theo dõi " + targetUser.getName(), Toast.LENGTH_SHORT).show();
            });
        } else {
            // Follow
            Map<String, Object> data = new HashMap<>();
            data.put("timestamp", FieldValue.serverTimestamp());

            batch.set(db.collection("users").document(currentUserId).collection("following").document(targetUid), data);
            batch.set(db.collection("users").document(targetUid).collection("followers").document(currentUserId), data);
            
            boolean becomingFriends = isFollowerOfMe;
            if (becomingFriends) {
                batch.update(db.collection("users").document(currentUserId), "friendsCount", FieldValue.increment(1));
                batch.update(db.collection("users").document(targetUid), "friendsCount", FieldValue.increment(1));
            }

            batch.commit().addOnSuccessListener(aVoid -> {
                Toast.makeText(getContext(), becomingFriends ? "Hai bạn đã trở thành bạn bè!" : "Đang theo dõi " + targetUser.getName(), Toast.LENGTH_SHORT).show();
                sendFollowNotification(targetUser, becomingFriends);
            });
        }
    }

    private void sendFollowNotification(User targetUser, boolean isFriendship) {
        if (currentUserData == null) return;

        String type = isFriendship ? "friend" : "follow";
        String title = isFriendship ? "Bạn bè mới" : "Người theo dõi mới";
        String message = isFriendship ? 
                "Bạn và " + currentUserData.getName() + " đã trở thành bạn bè." : 
                currentUserData.getName() + " đã bắt đầu theo dõi bạn.";

        Notification notification = new Notification(
                type,
                title,
                message,
                currentUserId,
                currentUserData.getName()
        );
        notification.setFromUserAvatar(currentUserData.getAvatar());

        db.collection("users").document(targetUser.getUid())
                .collection("notifications")
                .add(notification)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to send notification", e));
    }

    private void updateUserRankCard(List<User> users) {
        if (currentUserId == null || binding == null) return;
        
        int rank = -1;
        User currentUser = null;
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            if (u.getUid() != null && u.getUid().equals(currentUserId)) {
                rank = i + 1;
                currentUser = u;
                break;
            }
        }

        if (rank != -1) {
            binding.cardMyRank.setVisibility(View.VISIBLE);
            binding.itemUserRank.textRank.setText(String.valueOf(rank));
            binding.itemUserRank.textName.setText((currentUser.getName() != null ? currentUser.getName() : "Bạn") + " (Bạn)");
            binding.itemUserRank.textXp.setText(currentUser.getXp() + " XP");
            binding.itemUserRank.textStatus.setText(currentUser.getCurrentUnitTitle());
            
            int resId = R.drawable.bear;
            String avatar = currentUser.getAvatar();
            if (avatar != null) {
                String[] avatarValues = {"bear", "cat", "dog", "bird", "snake", "tiger", "rabbit"};
                int[] avatarResIds = {R.drawable.bear, R.drawable.cat, R.drawable.dog, R.drawable.bird, R.drawable.snake, R.drawable.tiger, R.drawable.rabbit};
                for (int i = 0; i < avatarValues.length; i++) {
                    if (avatarValues[i].equals(avatar)) {
                        resId = avatarResIds[i];
                        break;
                    }
                }
            }
            binding.itemUserRank.imageAvatar.setImageResource(resId);
        } else {
            binding.cardMyRank.setVisibility(View.GONE);
        }
    }

    private void showLoading(boolean loading) {
        if (binding == null) return;
        binding.emptyState.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.progressLeaderboard.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            binding.textLeaderboardEmpty.setText("Đang tải dữ liệu...");
        } else {
            boolean isEmpty = adapter.getItemCount() == 0;
            binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            if (isEmpty) {
                if (binding.socialTabs.getSelectedTabPosition() == 1) {
                    binding.textLeaderboardEmpty.setText("Bạn chưa có bạn bè nào");
                } else {
                    binding.textLeaderboardEmpty.setText("Chưa có dữ liệu");
                }
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (followingListener != null) followingListener.remove();
        if (followerListener != null) followerListener.remove();
        binding = null;
    }
}
