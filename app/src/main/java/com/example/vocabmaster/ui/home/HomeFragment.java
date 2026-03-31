package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.vocabmaster.R;

import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.databinding.FragmentHomeBinding;
import com.example.vocabmaster.ui.common.MotionSystem;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.example.vocabmaster.ui.library.CourseDetailActivity;
import com.example.vocabmaster.ui.study.MiniGameActivity;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private FirebaseFirestore db;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private User currentUser;

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
            Intent intent = new Intent(requireContext(), CourseDetailActivity.class);
            startActivity(intent);
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

    @Override
    public void onPause() {
        super.onPause();
        stopTimer();
    }

    private void loadStats() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        db.collection("users").document(uid).get().addOnSuccessListener(snapshot -> {
            if (binding == null || !isAdded()) return;
            
            currentUser = snapshot.toObject(User.class);
            if (currentUser == null) return;
            currentUser.setUid(uid);

            updateProfileUI();
            setupHeartTimer();
        });
    }


    private void updateProfileUI() {
        if (currentUser == null || binding == null) return;

        // Cập nhật Greeting và Tên User
        binding.textGreeting.setText(getGreeting());
        binding.textUserName.setText(currentUser.getName() != null ? currentUser.getName() : "Student");
        updateAvatarUI(currentUser.getAvatar());

        String heartCount = String.valueOf(currentUser.getHearts());
        binding.textHearts.setText(heartCount);

        if (binding.tvXpCount != null) {
            binding.tvXpCount.setText(String.valueOf(currentUser.getXp()));
        }
        if (binding.tvStreakCount != null) {
            binding.tvStreakCount.setText(String.valueOf(currentUser.getStreak()));
        }

        int dailyGoal = currentUser.getDailyGoal() <= 0 ? 20 : currentUser.getDailyGoal();
        int earned = (int) (currentUser.getXp() % (dailyGoal + 1));
        binding.progressCourse.setMax(dailyGoal);
        binding.progressCourse.setProgress(Math.min(earned, dailyGoal), true);
        binding.textCourseProgress.setText(String.format(Locale.getDefault(), "Tiến độ: %d/%d XP", earned, dailyGoal));
    }

    private String getGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 12) return "Good Morning,";
        if (hour >= 12 && hour < 18) return "Good Afternoon,";
        if (hour >= 18 && hour < 22) return "Good Evening,";
        return "Good Night,";
    }

    private void setupHeartTimer() {
        if (currentUser == null || currentUser.getHearts() >= 5) {
            binding.cardHeartRegen.setVisibility(View.GONE);
            stopTimer();
            return;
        }

        binding.cardHeartRegen.setVisibility(View.VISIBLE);
        startTimer();
    }

    private void startTimer() {
        stopTimer();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                updateTimerUI();
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    private void updateTimerUI() {
        if (currentUser == null || currentUser.getLastHeartRegen() == null || binding == null) return;

        long REGEN_TIME_MS = 5 * 60 * 1000;
        long now = System.currentTimeMillis();
        long lastRegen = currentUser.getLastHeartRegen().toDate().getTime();
        long diff = now - lastRegen;
        
        // Check if one or more hearts should have regenerated
        if (diff >= REGEN_TIME_MS) {
            int heartsToRegen = (int) (diff / REGEN_TIME_MS);
            int newHearts = Math.min(5, currentUser.getHearts() + heartsToRegen);
            
            // Calculate new lastRegen timestamp (carrying over remaining time)
            long remainingMs = diff % REGEN_TIME_MS;
            long newLastRegenTime = now - remainingMs;

            currentUser.setHearts(newHearts);
            currentUser.setLastHeartRegen(new Timestamp(new Date(newLastRegenTime)));
            
            // Sync with Firestore
            updateUserHeartsInFirestore(newHearts, currentUser.getLastHeartRegen());
            
            // Update UI immediately
            String heartCount = String.valueOf(newHearts);
            binding.textHearts.setText(heartCount);
            
            if (newHearts == 5) {
                binding.cardHeartRegen.setVisibility(View.GONE);
                stopTimer();
                return;
            }
            
            // Recalculate diff for smooth progress bar after update
            diff = remainingMs;
        }

        // UI Countdown
        long timeRemaining = REGEN_TIME_MS - (diff % REGEN_TIME_MS);
        int minutes = (int) (timeRemaining / 1000) / 60;
        int seconds = (int) (timeRemaining / 1000) % 60;
        binding.textHeartTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
        
        int progress = (int) ((diff * 100) / REGEN_TIME_MS);
        binding.regenProgress.setProgress(progress, true);
    }

    private void updateUserHeartsInFirestore(int hearts, Timestamp lastRegen) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        
        db.collection("users").document(uid)
                .update("hearts", hearts, "lastHeartRegen", lastRegen)
                .addOnFailureListener(e -> Log.e("HomeFragment", "Error updating hearts", e));
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
    }

    private void openMiniGame() {
        if (currentUser != null && currentUser.isPremium()) {
            MotionSystem.startScreen(requireActivity(), new Intent(requireContext(), MiniGameActivity.class));
        } else {
            UiFeedback.showErrorDialog(requireContext(), "Upgrade to Pro", "Mini games are a Pro feature. Level up your learning experience!");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopTimer();
        binding = null;
    }
}
