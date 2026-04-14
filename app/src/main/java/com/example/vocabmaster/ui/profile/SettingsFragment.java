package com.example.vocabmaster.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.databinding.FragmentSettingsBinding;
import com.example.vocabmaster.ui.auth.LoginActivity;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;
    private FirebaseFirestore db;
    private User currentUser;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        binding.switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> saveSetting("darkMode", isChecked));
        binding.btnLogout.setOnClickListener(v -> logout());
        binding.btnSaveSettings.setOnClickListener(v -> {
            int goal = 20;
            try { goal = Integer.parseInt(binding.editDailyGoal.getText().toString().trim()); } catch (Exception ignored) {}
            saveSetting("dailyGoal", goal);
        });

        // Điều hướng Premium - Đã sửa lỗi crash bằng cách gọi trực tiếp ID destination
        binding.layoutBuyPlan.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.navigation_premium));

        binding.layoutManagePlans.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.navigation_manage_plans));

        loadSettings();
    }

    private void loadSettings() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        db.collection("users").document(uid).addSnapshotListener((snapshot, e) -> {
            if (snapshot != null && snapshot.exists() && isAdded()) {
                currentUser = snapshot.toObject(User.class);
                if (currentUser != null) {
                    binding.editDailyGoal.setText(String.valueOf(currentUser.getDailyGoal() == 0 ? 20 : currentUser.getDailyGoal()));
                    binding.switchDarkMode.setChecked(currentUser.isDarkMode());
                }
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
