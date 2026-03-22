package com.example.vocabmaster.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.vocabmaster.ui.auth.LoginActivity;
import com.example.vocabmaster.databinding.FragmentProfileBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileFragment extends Fragment {
    private FragmentProfileBinding binding;
    private FirebaseFirestore db;

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
        binding.btnSaveProfile.setOnClickListener(v -> saveProfile());
        binding.btnUpgradePremium.setOnClickListener(v -> upgradePremium());
        binding.switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> saveSetting("darkMode", isChecked));
        binding.btnSaveGoal.setOnClickListener(v -> {
            int goal = 20;
            try {
                goal = Integer.parseInt(binding.editDailyGoal.getText().toString().trim());
            } catch (Exception ignored) {}
            saveSetting("dailyGoal", goal);
        });
        binding.btnLogout.setOnClickListener(v -> logout());
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
            String name = snapshot.getString("name");
            String email = snapshot.getString("email");
            Boolean premium = snapshot.getBoolean("premium");
            String role = snapshot.getString("role");
            Long dailyGoal = snapshot.getLong("dailyGoal");
            Boolean darkMode = snapshot.getBoolean("darkMode");
            Long streak = snapshot.getLong("streak");
            Long xp = snapshot.getLong("xp");
            Long hearts = snapshot.getLong("hearts");

            binding.editName.setText(name == null ? "" : name);
            binding.textEmail.setText(email == null ? "" : email);
            binding.textPremiumStatus.setText(Boolean.TRUE.equals(premium) ? "Premium" : "Free");
            binding.editDailyGoal.setText(String.valueOf(dailyGoal == null ? 20 : dailyGoal));
            binding.switchDarkMode.setChecked(Boolean.TRUE.equals(darkMode));
            binding.textStats.setText("XP: " + (xp == null ? 0 : xp) + " | Streak: " + (streak == null ? 0 : streak) + " | Hearts: " + (hearts == null ? 5 : hearts));
            binding.textRole.setText("Role: " + (role == null ? "user" : role));
            binding.adminPanel.setVisibility("admin".equals(role) ? View.VISIBLE : View.GONE);
        });
        binding.btnAdminRefresh.setOnClickListener(v -> loadAdminData());
    }

    private void loadAdminData() {
        db.collection("users").get().addOnSuccessListener(users -> binding.textAdminUsers.setText("Users: " + users.size()));
        db.collection("reports").get().addOnSuccessListener(reports -> binding.textAdminReports.setText("Pending reports: " + reports.size()));
        db.collection("subscriptions").get().addOnSuccessListener(subs -> binding.textAdminSubs.setText("Active subscriptions: " + subs.size()));
    }

    private void saveProfile() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        db.collection("users").document(uid).update("name", binding.editName.getText().toString().trim())
                .addOnSuccessListener(unused -> UiFeedback.showSnack(binding.getRoot(), "Profile updated"));
    }

    private void saveSetting(String key, Object value) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        db.collection("users").document(uid).update(key, value)
                .addOnSuccessListener(unused -> UiFeedback.showSnack(binding.getRoot(), "Settings saved"));
    }

    private void upgradePremium() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        UiFeedback.showConfirmDialog(requireContext(), "Upgrade to Premium", "Start your monthly Premium plan now?", () -> {
            db.collection("users").document(uid).update("premium", true).addOnSuccessListener(unused -> {
                java.util.Map<String, Object> sub = new java.util.HashMap<>();
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