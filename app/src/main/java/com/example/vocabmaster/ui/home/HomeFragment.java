package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.vocabmaster.R;
import com.example.vocabmaster.databinding.FragmentHomeBinding;
import com.example.vocabmaster.ui.common.MotionSystem;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.example.vocabmaster.ui.study.MiniGameActivity;
import com.example.vocabmaster.ui.study.StudyActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
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
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        setupInitialAnimations();
        setupListeners();
        runEntranceAnimation();
    }

    private void setupInitialAnimations() {
        binding.headerSection.setAlpha(0f);
        binding.headerSection.setTranslationY(-50f);
        binding.statsGrid.setAlpha(0f);
        binding.statsGrid.setScaleX(0.9f);
        binding.labelActions.setAlpha(0f);
        binding.btnStartFlashcards.setTranslationX(100f);
        binding.btnStartFlashcards.setAlpha(0f);
        binding.btnPlayMiniGame.setTranslationX(100f);
        binding.btnPlayMiniGame.setAlpha(0f);
    }

    private void runEntranceAnimation() {
        binding.headerSection.animate().alpha(1f).translationY(0).setDuration(600).start();
        binding.statsGrid.animate().alpha(1f).scaleX(1f).setDuration(700).setStartDelay(200).setInterpolator(new DecelerateInterpolator()).start();
        binding.labelActions.animate().alpha(1f).setDuration(500).setStartDelay(400).start();
        
        binding.btnStartFlashcards.animate().translationX(0).alpha(1f).setDuration(600).setStartDelay(500).start();
        binding.btnPlayMiniGame.animate().translationX(0).alpha(1f).setDuration(600).setStartDelay(600).start();
    }

    private void setupListeners() {
        MotionSystem.applyPressState(binding.btnStartFlashcards);
        MotionSystem.applyPressState(binding.btnPlayMiniGame);
        MotionSystem.applyPressState(binding.fabAdd);

        binding.btnStartFlashcards.setOnClickListener(v -> {
            UiFeedback.performHaptic(requireContext(), 10);
            MotionSystem.startScreen(requireActivity(), new Intent(requireContext(), StudyActivity.class));
        });

        binding.btnPlayMiniGame.setOnClickListener(v -> openMiniGame());

        binding.fabAdd.setOnClickListener(v -> {
            UiFeedback.performHaptic(requireContext(), 20);
            Intent intent = new Intent(requireContext(), CreateCourseFlowActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadStats();
    }

    private void loadStats() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        db.collection("users").document(uid).get().addOnSuccessListener(snapshot -> {
            if (binding == null || !isAdded()) return;
            
            String name = snapshot.getString("name"); // Changed from displayName to name to match ProfileFragment
            if (name != null && !name.isEmpty()) {
                binding.textUsername.setText(name + "!");
            }

            Long xp = snapshot.getLong("xp");
            Long streak = snapshot.getLong("streak");
            Long hearts = snapshot.getLong("hearts");
            Long goal = snapshot.getLong("dailyGoal");
            Boolean premium = snapshot.getBoolean("premium");
            String avatar = snapshot.getString("avatar");

            binding.textXp.setText(String.valueOf(xp == null ? 0 : xp));
            binding.textStreak.setText(String.valueOf(streak == null ? 0 : streak));
            binding.textHearts.setText(String.valueOf(hearts == null ? 5 : hearts));

            int dailyGoal = goal == null ? 20 : goal.intValue();
            int currentXp = (xp == null ? 0 : xp.intValue());
            int earned = currentXp % (dailyGoal + 1); 
            
            binding.progressDaily.setMax(dailyGoal);
            binding.progressDaily.setProgress(earned, true);
            binding.textGoalProgress.setText(earned + "/" + dailyGoal + " XP today");
            
            binding.textPremiumBadge.setVisibility(Boolean.TRUE.equals(premium) ? View.VISIBLE : View.GONE);
            
            updateAvatarUI(avatar);
        });
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
        binding.imgProfile.setImageResource(resId);
        binding.imgProfile.setPadding(8, 8, 8, 8); // Add padding like in profile to look consistent
    }

    private void openMiniGame() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        db.collection("users").document(uid).get().addOnSuccessListener(snapshot -> {
            Boolean premium = snapshot.getBoolean("premium");
            if (Boolean.TRUE.equals(premium)) {
                MotionSystem.startScreen(requireActivity(), new Intent(requireContext(), MiniGameActivity.class));
            } else {
                UiFeedback.showErrorDialog(requireContext(), "Upgrade to Pro", "Mini games are a Pro feature. Level up your learning experience!");
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
