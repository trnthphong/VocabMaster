package com.example.vocabmaster.ui.social;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.databinding.DialogSearchFriendBinding;
import com.example.vocabmaster.databinding.FragmentSocialBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SocialFragment extends Fragment {
    private FragmentSocialBinding binding;
    private FirebaseFirestore db;
    private LeaderboardAdapter adapter;
    private String currentUserId;
    private final List<String> followingIds = new ArrayList<>();

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
        loadFollowingList();
        setupSearch();

        loadLeaderboard();
    }

    private void setupRecyclerView() {
        adapter = new LeaderboardAdapter();
        binding.recyclerLeaderboard.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerLeaderboard.setAdapter(adapter);
    }

    private void setupTabs() {
        binding.socialTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    loadLeaderboard();
                } else {
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
        // Handle search button click
        binding.btnSearch.setOnClickListener(v -> performSearch());

        // Handle "Search" action on keyboard
        binding.editSearchEmail.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });
    }

    private void performSearch() {
        String email = binding.editSearchEmail.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(getContext(), "Please enter an email", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show a simple loading or just start query
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(qs -> {
                    if (!qs.isEmpty()) {
                        User foundUser = qs.getDocuments().get(0).toObject(User.class);
                        if (foundUser != null) {
                            if (foundUser.getUid() == null) {
                                foundUser.setUid(qs.getDocuments().get(0).getId());
                            }
                            showUserProfile(foundUser);
                        }
                    } else {
                        Toast.makeText(getContext(), "No user found with this email", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Search failed", Toast.LENGTH_SHORT).show());
    }

    private void showUserProfile(User user) {
        // Now we show the result in a BottomSheet, but only for the RESULT (Profile View)
        // This is where they can see details and click "Follow"
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        DialogSearchFriendBinding dialogBinding = DialogSearchFriendBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());

        // Hide the internal search box in the dialog since we searched from the main screen
        dialogBinding.layoutSearch.setVisibility(View.GONE);
        dialogBinding.btnSearch.setVisibility(View.GONE);

        // Fill data
        dialogBinding.itemSearchResult.getRoot().setVisibility(View.VISIBLE);
        dialogBinding.itemSearchResult.textRank.setVisibility(View.GONE);
        dialogBinding.itemSearchResult.textName.setText(user.getName() != null ? user.getName() : "Unknown");
        dialogBinding.itemSearchResult.textXp.setText(user.getXp() + " XP");
        dialogBinding.itemSearchResult.textStatus.setText(user.getCurrentUnitTitle());

        boolean isFollowing = followingIds.contains(user.getUid());
        boolean isMe = user.getUid().equals(currentUserId);

        if (isMe) {
            dialogBinding.btnFollow.setVisibility(View.GONE);
        } else {
            dialogBinding.btnFollow.setVisibility(View.VISIBLE);
            dialogBinding.btnFollow.setText(isFollowing ? "Unfollow" : "Follow");
            dialogBinding.btnFollow.setOnClickListener(v -> toggleFollow(user, dialogBinding));
        }

        dialog.show();
    }

    private void loadFollowingList() {
        if (currentUserId == null) return;
        db.collection("users").document(currentUserId).collection("following")
                .get()
                .addOnSuccessListener(qs -> {
                    followingIds.clear();
                    qs.getDocuments().forEach(doc -> followingIds.add(doc.getId()));
                });
    }

    private void loadLeaderboard() {
        showLoading(true);
        db.collection("users")
                .orderBy("xp", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener(qs -> {
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
                        binding.textLeaderboardEmpty.setText("Failed to load leaderboard");
                    }
                });
    }

    private void loadFriends() {
        if (currentUserId == null) return;
        
        showLoading(true);
        if (followingIds.isEmpty()) {
            adapter.submitList(new ArrayList<>());
            showLoading(false);
            if (binding != null) {
                binding.textLeaderboardEmpty.setText("You are not following anyone yet.");
            }
            return;
        }

        db.collection("users")
                .whereIn("uid", followingIds)
                .get()
                .addOnSuccessListener(qs -> {
                    List<User> users = qs.toObjects(User.class);
                    for (int i = 0; i < users.size(); i++) {
                        if (users.get(i).getUid() == null) {
                            users.get(i).setUid(qs.getDocuments().get(i).getId());
                        }
                    }
                    Collections.sort(users, (u1, u2) -> Long.compare(u2.getXp(), u1.getXp()));
                    adapter.submitList(users);
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    if (binding != null) {
                        binding.textLeaderboardEmpty.setText("Error loading friends");
                    }
                });
    }

    private void toggleFollow(User user, DialogSearchFriendBinding dialogBinding) {
        if (currentUserId == null || user.getUid() == null) return;
        
        boolean currentlyFollowing = followingIds.contains(user.getUid());
        
        if (currentlyFollowing) {
            db.collection("users").document(currentUserId)
                    .collection("following").document(user.getUid()).delete()
                    .addOnSuccessListener(aVoid -> {
                        followingIds.remove(user.getUid());
                        dialogBinding.btnFollow.setText("Follow");
                        Toast.makeText(getContext(), "Unfollowed " + user.getName(), Toast.LENGTH_SHORT).show();
                        if (binding != null && binding.socialTabs.getSelectedTabPosition() == 1) loadFriends();
                    });
        } else {
            db.collection("users").document(currentUserId)
                    .collection("following").document(user.getUid())
                    .set(new User()) 
                    .addOnSuccessListener(aVoid -> {
                        followingIds.add(user.getUid());
                        dialogBinding.btnFollow.setText("Unfollow");
                        Toast.makeText(getContext(), "Following " + user.getName(), Toast.LENGTH_SHORT).show();
                        if (binding != null && binding.socialTabs.getSelectedTabPosition() == 1) loadFriends();
                    });
        }
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

        if (rank != -1 && currentUser != null) {
            binding.cardMyRank.setVisibility(View.VISIBLE);
            binding.itemUserRank.textRank.setText(String.valueOf(rank));
            binding.itemUserRank.textName.setText((currentUser.getName() != null ? currentUser.getName() : "You") + " (You)");
            binding.itemUserRank.textXp.setText(currentUser.getXp() + " XP");
            binding.itemUserRank.textStatus.setText(currentUser.getCurrentUnitTitle());
        } else {
            binding.cardMyRank.setVisibility(View.GONE);
        }
    }

    private void showLoading(boolean loading) {
        if (binding == null) return;
        binding.emptyState.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.progressLeaderboard.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            binding.textLeaderboardEmpty.setText("Loading leaderboard...");
        } else {
            boolean isEmpty = adapter.getItemCount() == 0;
            binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            if (isEmpty) {
                if (binding.socialTabs.getSelectedTabPosition() == 1) {
                    binding.textLeaderboardEmpty.setText("You are not following anyone yet");
                } else {
                    binding.textLeaderboardEmpty.setText("No data yet");
                }
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
