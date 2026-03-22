package com.example.vocabmaster.ui.study;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.data.repository.StudyScheduler;
import com.example.vocabmaster.databinding.ActivityStudyBinding;
import com.example.vocabmaster.ui.common.MotionSystem;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class StudyActivity extends AppCompatActivity {

    private ActivityStudyBinding binding;
    private List<Flashcard> flashcards = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isShowingFront = true;
    private FirebaseFirestore db;
    private int correctAnswers = 0;
    private int xpEarned = 0;
    private int heartsLost = 0;
    private StudyScheduler scheduler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Dynamic System Bars
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            binding.topBar.setPadding(binding.topBar.getPaddingLeft(), statusBarHeight, 
                                     binding.topBar.getPaddingRight(), binding.topBar.getPaddingBottom());
            return insets;
        });

        db = FirebaseFirestore.getInstance();
        scheduler = new StudyScheduler();
        
        setupInitialState();
        loadFromScheduler();
        setupListeners();
    }

    private void setupInitialState() {
        // Initial hidden state for entry animation
        binding.topBar.setTranslationY(-200f);
        binding.cardFlashcard.setScaleX(0.8f);
        binding.cardFlashcard.setScaleY(0.8f);
        binding.cardFlashcard.setAlpha(0f);
        binding.bottomControls.setTranslationY(300f);
    }

    private void runEntryAnimation() {
        binding.topBar.animate().translationY(0).setDuration(600).setInterpolator(new DecelerateInterpolator()).start();
        binding.cardFlashcard.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(800)
                .setInterpolator(new AnticipateOvershootInterpolator()).start();
        binding.bottomControls.animate().translationY(0).setDuration(700).setStartDelay(200)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void setupListeners() {
        MotionSystem.applyPressState(binding.btnHard);
        MotionSystem.applyPressState(binding.btnGood);
        MotionSystem.applyPressState(binding.btnClose);

        binding.cardFlashcard.setOnClickListener(v -> flipCard());
        binding.btnHard.setOnClickListener(v -> nextCard(false));
        binding.btnGood.setOnClickListener(v -> nextCard(true));
        binding.btnClose.setOnClickListener(v -> {
            UiFeedback.performHaptic(this, 10);
            finish();
        });
    }

    private void loadFromScheduler() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            openSummary();
            return;
        }
        
        // Show loading state/skeleton if needed (omitted for brevity, assume quick load)
        scheduler.seedIfEmpty(uid, () -> scheduler.loadDueFlashcards(uid, cards -> {
            flashcards.clear();
            flashcards.addAll(cards);
            if (flashcards.isEmpty()) {
                UiFeedback.showSnack(binding.getRoot(), "You're all caught up!");
                new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1500);
                return;
            }
            currentIndex = 0;
            updateUI();
            runEntryAnimation();
        }));
    }

    private void updateUI() {
        if (currentIndex < flashcards.size()) {
            Flashcard current = flashcards.get(currentIndex);
            binding.textTerm.setText(current.getTerm());
            binding.textDefinition.setText(current.getDefinition());
            
            // Reset card state for next one
            binding.cardFlashcard.setRotationY(0);
            binding.cardFront.setVisibility(View.VISIBLE);
            binding.cardBack.setVisibility(View.GONE);
            binding.cardBack.setRotationY(0); 
            isShowingFront = true;

            int progress = (int) (((float) (currentIndex + 1) / flashcards.size()) * 100);
            binding.studyProgress.setProgress(progress, true);
        } else {
            openSummary();
        }
    }

    private void flipCard() {
        UiFeedback.performHaptic(this, 20);
        float endRotation = isShowingFront ? 180f : 0f;
        
        binding.cardFlashcard.animate()
                .rotationY(endRotation)
                .setDuration(500)
                .setInterpolator(new AnticipateOvershootInterpolator(1.2f))
                .withStartAction(() -> {
                    // Logic to swap visibility mid-flip (at 90 degrees)
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (isShowingFront) {
                            binding.cardFront.setVisibility(View.GONE);
                            binding.cardBack.setVisibility(View.VISIBLE);
                            binding.cardBack.setRotationY(180f); // Keep text readable
                        } else {
                            binding.cardFront.setVisibility(View.VISIBLE);
                            binding.cardBack.setVisibility(View.GONE);
                        }
                        isShowingFront = !isShowingFront;
                    }, 250);
                })
                .start();
    }

    private void nextCard(boolean success) {
        Flashcard current = flashcards.get(currentIndex);
        float exitTranslationX = success ? 1000f : -1000f;
        
        // Motion: Fly away animation
        binding.cardFlashcard.animate()
                .translationX(exitTranslationX)
                .rotation(success ? 15f : -15f)
                .alpha(0f)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    processReviewResult(current, success);
                    currentIndex++;
                    
                    // Reset card for next entry
                    binding.cardFlashcard.setTranslationX(0);
                    binding.cardFlashcard.setRotation(0);
                    binding.cardFlashcard.setAlpha(0f);
                    updateUI();
                    
                    // Entry animation for next card
                    binding.cardFlashcard.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(300)
                            .start();
                })
                .start();
    }

    private void processReviewResult(Flashcard current, boolean success) {
        if (success) {
            current.setInterval(current.getInterval() * 2);
            correctAnswers++;
            xpEarned += 5;
            UiFeedback.performHaptic(this, 50);
            rewardXpAndStreak();
        } else {
            current.setInterval(1);
            heartsLost++;
            UiFeedback.performHaptic(this, 100);
            loseHeart();
        }
        current.setLastReviewTime(System.currentTimeMillis());
        
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            scheduler.persistReview(uid, current, success);
        }
    }

    private void rewardXpAndStreak() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        db.collection("users").document(uid).get().addOnSuccessListener(snapshot -> {
            Long xp = snapshot.getLong("xp");
            Long streak = snapshot.getLong("streak");
            db.collection("users").document(uid)
                    .update("xp", (xp == null ? 0 : xp) + 5, "streak", (streak == null ? 0 : streak) + 1);
            
            // Dynamic UI Feedback: Update streak text with animation
            binding.textStreak.setText(String.valueOf((streak == null ? 0 : streak) + 1));
            binding.textStreak.animate().scaleX(1.4f).scaleY(1.4f).setDuration(200)
                    .withEndAction(() -> binding.textStreak.animate().scaleX(1f).scaleY(1f).start()).start();
        });
    }

    private void loseHeart() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        db.collection("users").document(uid).get().addOnSuccessListener(snapshot -> {
            Long hearts = snapshot.getLong("hearts");
            long current = hearts == null ? 5 : hearts;
            db.collection("users").document(uid).update("hearts", Math.max(0, current - 1));
        });
    }

    private void openSummary() {
        Intent intent = new Intent(this, StudySummaryActivity.class);
        intent.putExtra("total", flashcards.size());
        intent.putExtra("correct", correctAnswers);
        intent.putExtra("xp", xpEarned);
        intent.putExtra("hearts_lost", heartsLost);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
