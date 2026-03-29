package com.example.vocabmaster.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.vocabmaster.databinding.FragmentSettingsBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;
    private FirebaseFirestore db;

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
        
        binding.btnSaveSettings.setOnClickListener(v -> {
            int goal = 20;
            try {
                goal = Integer.parseInt(binding.editDailyGoal.getText().toString().trim());
            } catch (Exception ignored) {}
            saveSetting("dailyGoal", goal);
        });

        loadSettings();
    }

    private void loadSettings() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        db.collection("users").document(uid).get().addOnSuccessListener(snapshot -> {
            if (!isAdded() || binding == null) return;
            
            Long dailyGoal = snapshot.getLong("dailyGoal");
            Boolean darkMode = snapshot.getBoolean("darkMode");

            binding.editDailyGoal.setText(String.valueOf(dailyGoal == null ? 20 : dailyGoal));
            binding.switchDarkMode.setChecked(Boolean.TRUE.equals(darkMode));
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
