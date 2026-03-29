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
        binding.btnUpgradePremium.setOnClickListener(v -> upgradePremium());


        // Navigation to Social
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
            String email = snapshot.getString("email");
            Boolean premium = snapshot.getBoolean("premium");
            String role = snapshot.getString("role");
            Long dailyGoal = snapshot.getLong("dailyGoal");
            Boolean darkMode = snapshot.getBoolean("darkMode");
            Long streak = snapshot.getLong("streak");
            Long xp = snapshot.getLong("xp");
            Long hearts = snapshot.getLong("hearts");
            String avatar = snapshot.getString("avatar");
            String language = snapshot.getString("language");
            
            // Handle join year
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
            
            binding.textPremiumStatus.setText(Boolean.TRUE.equals(premium) ? "Premium" : "Free");
            
            // Update New Stats UI
            long xpVal = (xp == null ? 0 : xp);
            binding.textXpValue.setText(String.valueOf(xpVal));
            binding.textStreakValue.setText(String.valueOf(streak == null ? 0 : streak));
            binding.textHeartsValue.setText(String.valueOf(hearts == null ? 5 : hearts));
            
            binding.adminPanel.setVisibility("admin".equals(role) ? View.VISIBLE : View.GONE);
            
            updateAvatarUI(avatar);
            
            // Ưu tiên load từ courses collection để có thông tin mới nhất và chính xác nhất
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
                        // Nếu không tìm thấy khóa học nào, dùng thông tin từ user profile làm fallback
                        updateCourseUI(userLanguage);
                        return;
                    }
                    
                    DocumentSnapshot latestDoc = null;
                    Date latestDate = null;
                    
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Date updatedAt = doc.getDate("updatedAt");
                        if (updatedAt != null) {
                            if (latestDate == null || updatedAt.after(latestDate)) {
                                latestDate = updatedAt;
                                latestDoc = doc;
                            }
                        } else {
                            // Nếu không có updatedAt, thử dùng createdAt
                            Date createdAt = doc.getDate("createdAt");
                            if (createdAt != null && (latestDate == null || createdAt.after(latestDate))) {
                                latestDate = createdAt;
                                latestDoc = doc;
                            }
                        }
                    }
                    
                    if (latestDoc != null) {
                        String title = latestDoc.getString("title");
                        Long langId = latestDoc.getLong("targetLanguageId");
                        
                        Log.d(TAG, "Latest course found: " + title + ", langId: " + langId);


                        
                        // Cập nhật cờ
                        int flagRes = -1;
                        if (langId != null) {
                            flagRes = getFlagForLanguageId(langId.intValue());
                        } 
                        
                        if (flagRes == -1 && title != null) {
                            flagRes = guessFlagFromText(title);
                        }
                        
                        if (flagRes == -1 && userLanguage != null) {
                            flagRes = guessFlagFromText(userLanguage);
                        }

                        if (flagRes == -1) flagRes = R.drawable.vietnam;

                        binding.imageCourseFlag.setImageResource(flagRes);
                        binding.imageStatCourse.setImageResource(flagRes);
                    } else {
                        updateCourseUI(userLanguage);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading courses", e);
                    if (isAdded()) updateCourseUI(userLanguage);
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
        if (text == null) return -1;
        String lower = text.toLowerCase();
        if (lower.contains("anh") || lower.contains("english")) return R.drawable.eng;
        if (lower.contains("nhật") || lower.contains("japan") || lower.contains("japanese")) return R.drawable.japan;
        if (lower.contains("trung") || lower.contains("china") || lower.contains("chinese")) return R.drawable.china;
        if (lower.contains("nga") || lower.contains("russia") || lower.contains("russian")) return R.drawable.russia;
        return -1;
    }

    private void updateAvatarUI(String avatarValue) {
        int resId = R.drawable.bear; // default
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
        int flagRes;
        String courseName;

        if (language == null || language.isEmpty()) {
            flagRes = R.drawable.vietnam;
            courseName = "Chưa chọn khóa học";
        } else {
            courseName = language;
            flagRes = guessFlagFromText(language);
            if (flagRes == -1) flagRes = R.drawable.vietnam;
        }

        binding.imageCourseFlag.setImageResource(flagRes);
        binding.textCourseName.setText(courseName);
        binding.imageStatCourse.setImageResource(flagRes);
        binding.textStatsCourseTitle.setText(courseName);
    }

    private void showAvatarSelectionDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_avatar_selection, null);
        GridView gridView = dialogView.findViewById(R.id.grid_avatars);
        
        AvatarAdapter adapter = new AvatarAdapter(getLayoutInflater(), avatarResIds);
        gridView.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedAvatar = avatarValues[position];
            saveSetting("avatar", selectedAvatar);
            updateAvatarUI(selectedAvatar);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void loadAdminData() {
        db.collection("users").get().addOnSuccessListener(users -> {
            if (binding != null) binding.textAdminUsers.setText("Users: " + users.size());
        });
        db.collection("reports").get().addOnSuccessListener(reports -> {
            if (binding != null) binding.textAdminReports.setText("Pending reports: " + reports.size());
        });
        db.collection("subscriptions").get().addOnSuccessListener(subs -> {
            if (binding != null) binding.textAdminSubs.setText("Active subscriptions: " + subs.size());
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
        db.collection("users").document(uid).update(key, value)
                .addOnSuccessListener(unused -> {
                    if (isAdded()) UiFeedback.showSnack(binding.getRoot(), "Settings saved");
                });
    }

    private void upgradePremium() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        UiFeedback.showConfirmDialog(requireContext(), "Upgrade to Premium", "Start your monthly Premium plan now?", () -> {
            db.collection("users").document(uid).update("premium", true).addOnSuccessListener(unused -> {
                Map<String, Object> sub = new HashMap<>();
                sub.put("userId", uid);
                sub.put("plan", "monthly");
                sub.put("createdAt", System.currentTimeMillis());
                db.collection("subscriptions").add(sub);
                UiFeedback.showSnack(binding.getRoot(), "Premium activated");
                loadProfile();
            });
        });
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        startActivity(new Intent(requireContext(), LoginActivity.class));
        requireActivity().finish();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
