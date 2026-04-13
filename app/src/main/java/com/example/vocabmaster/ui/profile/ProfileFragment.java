package com.example.vocabmaster.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.vocabmaster.R;
import com.example.vocabmaster.ui.auth.LoginActivity;
import com.example.vocabmaster.databinding.FragmentProfileBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ProfileFragment extends Fragment {
    private static final String TAG = "ProfileFragment";
    private FragmentProfileBinding binding;
    private FirebaseFirestore db;

    private final String[] avatarValues = {"bear", "cat", "dog", "bird", "snake", "tiger", "rabbit"};
    private final int[] avatarResIds = {
            R.drawable.bear, R.drawable.cat, R.drawable.dog,
            R.drawable.bird, R.drawable.snake, R.drawable.tiger,
            R.drawable.rabbit
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        binding.btnSettings.setOnClickListener(v -> 
                NavHostFragment.findNavController(this).navigate(R.id.action_profile_to_settings));
        
        binding.cardAvatar.setOnClickListener(v -> showAvatarSelectionDialog());
        binding.btnSaveProfile.setOnClickListener(v -> saveProfile());
        
        binding.cardPremiumBanner.setOnClickListener(v -> 
                NavHostFragment.findNavController(this).navigate(R.id.action_profile_to_premium));

        View.OnClickListener toSocial = v -> {
            NavController navController = NavHostFragment.findNavController(this);
            navController.navigate(R.id.navigation_social);
        };
        binding.btnAddFriendAction.setOnClickListener(toSocial);
        binding.btnQrCode.setOnClickListener(toSocial);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProfile();
    }

    private void loadProfile() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        db.collection("users").document(uid).get().addOnSuccessListener(snapshot -> {
            if (!isAdded() || binding == null) return;
            
            String name = snapshot.getString("name");
            Boolean premium = snapshot.getBoolean("premium");
            String role = snapshot.getString("role");
            Long streak = snapshot.getLong("streak");
            Long xp = snapshot.getLong("xp");
            Long hearts = snapshot.getLong("hearts");
            String avatar = snapshot.getString("avatar");
            String language = snapshot.getString("language");
            
            Object createdAtObj = snapshot.get("createdAt");
            int joinYear = 2024;
            if (createdAtObj instanceof Long) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis((Long) createdAtObj);
                joinYear = cal.get(Calendar.YEAR);
            } else if (FirebaseAuth.getInstance().getCurrentUser() != null && FirebaseAuth.getInstance().getCurrentUser().getMetadata() != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(FirebaseAuth.getInstance().getCurrentUser().getMetadata().getCreationTimestamp());
                joinYear = cal.get(Calendar.YEAR);
            }

            binding.editName.setText(name == null ? "" : name);
            binding.textDisplayName.setText(name == null || name.isEmpty() ? "User" : name);
            binding.textJoinedYear.setText("Thành viên từ " + joinYear);
            
            boolean isPremium = Boolean.TRUE.equals(premium);
            binding.textPremiumStatus.setText(isPremium ? "Premium" : "Free");
            binding.cardPremiumBanner.setVisibility(isPremium ? View.GONE : View.VISIBLE);
            
            binding.textXpValue.setText(String.valueOf(xp == null ? 0 : xp));
            binding.textStreakValue.setText(String.valueOf(streak == null ? 0 : streak));
            binding.textHeartsValue.setText(String.valueOf(hearts == null ? 5 : hearts));
            
            binding.adminPanel.setVisibility("admin".equals(role) ? View.VISIBLE : View.GONE);
            updateAvatarUI(avatar);
            
            loadLatestCourse(uid, language);
        });
        binding.btnAdminRefresh.setOnClickListener(v -> loadAdminData());
    }

    private void loadLatestCourse(String uid, String userLanguage) {
        db.collection("courses")
                .whereEqualTo("creatorId", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded() || binding == null) return;
                    
                    if (querySnapshot.isEmpty()) {
                        updateCourseUI(null);
                        return;
                    }
                    
                    DocumentSnapshot latestDoc = null;
                    Date latestDate = null;
                    
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Date updatedAt = doc.getDate("updatedAt");
                        if (updatedAt == null) updatedAt = doc.getDate("createdAt");
                        
                        if (updatedAt != null && (latestDate == null || updatedAt.after(latestDate))) {
                            latestDate = updatedAt;
                            latestDoc = doc;
                        }
                    }
                    
                    if (latestDoc != null) {
                        String title = latestDoc.getString("title");
                        Long langId = latestDoc.getLong("targetLanguageId");
                        int flagRes = (langId != null) ? getFlagForLanguageId(langId.intValue()) : -1;
                        
                        if (flagRes == -1) flagRes = guessFlagFromText(title != null ? title : "");
                        if (flagRes == -1) flagRes = R.drawable.vietnam;

                        String displayTitle = title;
                        if (displayTitle == null || displayTitle.isEmpty() || displayTitle.equalsIgnoreCase("en")) displayTitle = "Tiếng Anh";
                        else if (displayTitle.equalsIgnoreCase("ru")) displayTitle = "Tiếng Nga";

                        binding.imageCourseFlag.setImageResource(flagRes);
                        binding.textCourseName.setText(displayTitle);
                        binding.imageStatCourse.setImageResource(flagRes);
                        binding.textStatsCourseTitle.setText(displayTitle);
                    } else {
                        updateCourseUI(null);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) updateCourseUI(null);
                });
    }

    private int getFlagForLanguageId(int langId) {
        switch (langId) {
            case 1: return R.drawable.eng;
            case 2: return R.drawable.japan;
            case 4: return R.drawable.china;
            case 5: return R.drawable.russia;
            default: return -1;
        }
    }

    private int guessFlagFromText(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("en") || lower.contains("anh")) return R.drawable.eng;
        if (lower.contains("ru") || lower.contains("nga")) return R.drawable.russia;
        return -1;
    }

    private void updateAvatarUI(String avatarValue) {
        int resId = R.drawable.bear;
        if (avatarValue != null) {
            for (int i = 0; i < avatarValues.length; i++) {
                if (avatarValues[i].equals(avatarValue)) {
                    resId = avatarResIds[i];
                    break;
                }
            }
        }
        binding.imageAvatar.setImageResource(resId);
    }

    private void updateCourseUI(String language) {
        binding.imageCourseFlag.setImageResource(R.drawable.vietnam);
        binding.textCourseName.setText("No courses");
        binding.imageStatCourse.setImageResource(R.drawable.vietnam);
        binding.textStatsCourseTitle.setText("No courses");
    }

    private void showAvatarSelectionDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_avatar_selection, null);
        GridView gridView = dialogView.findViewById(R.id.grid_avatars);
        AvatarAdapter adapter = new AvatarAdapter(getLayoutInflater(), avatarResIds);
        gridView.setAdapter(adapter);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(dialogView).create();
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            saveSetting("avatar", avatarValues[position]);
            updateAvatarUI(avatarValues[position]);
            dialog.dismiss();
        });
        dialog.show();
    }

    private void loadAdminData() {
        db.collection("users").get().addOnSuccessListener(users -> {
            if (binding != null) binding.textAdminUsers.setText("Users: " + users.size());
        });
    }

    private void saveProfile() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        String newName = binding.editName.getText().toString().trim();
        db.collection("users").document(uid).update("name", newName)
                .addOnSuccessListener(unused -> {
                    if (isAdded()) {
                        UiFeedback.showSnack(binding.getRoot(), "Profile updated");
                        binding.textDisplayName.setText(newName);
                    }
                });
    }

    private void saveSetting(String key, Object value) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        db.collection("users").document(uid).update(key, value);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
