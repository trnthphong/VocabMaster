package com.example.vocabmaster.ui.study;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.data.repository.FlashcardStudyRepository;
import com.example.vocabmaster.data.repository.GamificationRepository;
import com.example.vocabmaster.data.srs.SpacedRepetitionCalculator;
import com.example.vocabmaster.data.srs.SpacedRepetitionConstants;
import com.example.vocabmaster.databinding.ActivityFlashcardStudyBinding;
import com.example.vocabmaster.ui.common.GamificationStatusBinder;
import com.example.vocabmaster.ui.common.MotionSystem;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class FlashcardStudyActivity extends AppCompatActivity {
    private ActivityFlashcardStudyBinding binding;
    private FlashcardStudyRepository studyRepository;
    private GamificationRepository gamificationRepository;
    private GamificationStatusBinder gamificationStatusBinder;
    private FlashcardStudySetAdapter studySetAdapter;

    private final ArrayDeque<FlashcardStudyRepository.StudyCard> queue = new ArrayDeque<>();
    private final Set<String> hardRequeuedCardIds = new HashSet<>();
    private final Set<String> reviewedCardIds = new HashSet<>();

    private String uid;
    private FlashcardStudyRepository.StudySetSummary selectedSummary;
    private FlashcardStudyRepository.StudyCard currentCard;
    private int completedSteps = 0;
    private int totalSteps = 0;
    private boolean showingBack = false;
    private boolean completionAwarded = false;
    private boolean showingStudySetPicker = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFlashcardStudyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        uid = FirebaseAuth.getInstance().getUid();
        studyRepository = new FlashcardStudyRepository(getApplication());
        gamificationRepository = new GamificationRepository(this);
        gamificationStatusBinder = new GamificationStatusBinder(this, binding.getRoot());
        gamificationStatusBinder.start(uid);

        setupListeners();
        setupStudySetList();
        loadStudySetPicker();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gamificationStatusBinder != null) {
            gamificationStatusBinder.stop();
        }
    }

    private void setupListeners() {
        binding.toolbar.setNavigationOnClickListener(v -> handleBack());
        binding.cardStudy.setOnClickListener(v -> flipToBack());
        binding.btnHard.setOnClickListener(v -> rateCurrentCard(SpacedRepetitionCalculator.Rating.HARD));
        binding.btnMedium.setOnClickListener(v -> rateCurrentCard(SpacedRepetitionCalculator.Rating.MEDIUM));
        binding.btnEasy.setOnClickListener(v -> rateCurrentCard(SpacedRepetitionCalculator.Rating.EASY));
        binding.btnStatePrimary.setOnClickListener(v -> {
            if (selectedSummary != null && !showingStudySetPicker) {
                loadStudySetPicker();
            } else {
                finish();
            }
        });

        MotionSystem.applyPressState(binding.cardStudy);
        MotionSystem.applyPressState(binding.btnHard);
        MotionSystem.applyPressState(binding.btnMedium);
        MotionSystem.applyPressState(binding.btnEasy);
        MotionSystem.applyPressState(binding.btnStatePrimary);
    }

    private void setupStudySetList() {
        studySetAdapter = new FlashcardStudySetAdapter(this::startStudySet);
        binding.recyclerStudySets.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerStudySets.setAdapter(studySetAdapter);
    }

    private void handleBack() {
        if (selectedSummary != null && !showingStudySetPicker) {
            loadStudySetPicker();
        } else {
            finish();
        }
    }

    private void loadStudySetPicker() {
        if (uid == null) {
            showState(false, "Vui lòng đăng nhập", "Bạn cần đăng nhập để lưu lịch ôn tập.", "Quay lại");
            return;
        }

        selectedSummary = null;
        currentCard = null;
        showingStudySetPicker = true;
        binding.toolbar.setTitle("Spaced Repetition");
        showStudySetLoading();

        studyRepository.loadAvailableStudySets(uid)
                .addOnSuccessListener(this::showStudySets)
                .addOnFailureListener(e -> showState(false, "Không tải được bộ thẻ", "Hãy kiểm tra kết nối và thử lại sau.", "Quay lại"));
    }

    private void showStudySets(List<FlashcardStudyRepository.StudySetSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            showState(false, "Chưa có bộ thẻ", "Các bộ thẻ đã lưu, tải về, tự tạo hoặc Personal Flashcards sẽ xuất hiện ở đây.", "Quay lại");
            return;
        }

        showingStudySetPicker = true;
        binding.layoutProgress.setVisibility(View.GONE);
        binding.layoutStudy.setVisibility(View.GONE);
        binding.layoutRating.setVisibility(View.GONE);
        binding.layoutState.setVisibility(View.VISIBLE);
        binding.progressLoading.setVisibility(View.GONE);
        binding.recyclerStudySets.setVisibility(View.VISIBLE);
        binding.textStateTitle.setText("Chọn bộ để ôn");
        binding.textStateMessage.setText("Các bộ thẻ đã lưu, tải về, tự tạo và Personal Flashcards.");
        binding.btnStatePrimary.setText("Quay lại");
        binding.btnStatePrimary.setVisibility(View.VISIBLE);
        studySetAdapter.submitList(summaries);
    }

    private void startStudySet(FlashcardStudyRepository.StudySetSummary summary) {
        selectedSummary = summary;
        showingStudySetPicker = false;
        binding.toolbar.setTitle(summary.getTitle());
        loadSession();
    }

    private void loadSession() {
        if (uid == null) {
            showState(false, "Vui lòng đăng nhập", "Bạn cần đăng nhập để lưu lịch ôn tập.", "Quay lại");
            return;
        }
        if (selectedSummary == null) {
            loadStudySetPicker();
            return;
        }

        showLoading();
        studyRepository.loadDueSession(uid, selectedSummary)
                .addOnSuccessListener(this::startSession)
                .addOnFailureListener(e -> showState(false, "Không tải được thẻ", "Hãy kiểm tra kết nối và thử lại sau.", "Chọn bộ khác"));
    }

    private void startSession(List<FlashcardStudyRepository.StudyCard> cards) {
        queue.clear();
        hardRequeuedCardIds.clear();
        reviewedCardIds.clear();
        completedSteps = 0;
        totalSteps = cards != null ? cards.size() : 0;
        completionAwarded = false;

        if (cards != null) {
            queue.addAll(cards);
        }

        if (queue.isEmpty()) {
            showState(false, "Chưa có thẻ cần ôn", "Bộ này chưa có thẻ mới hoặc thẻ đến hạn ôn lúc này.", "Chọn bộ khác");
            return;
        }

        binding.layoutState.setVisibility(View.GONE);
        binding.layoutProgress.setVisibility(View.VISIBLE);
        binding.layoutStudy.setVisibility(View.VISIBLE);
        showNextCard();
    }

    private void showNextCard() {
        currentCard = queue.poll();
        if (currentCard == null) {
            completeSession();
            return;
        }

        showingBack = false;
        bindCard(currentCard.getFlashcard());
        updateProgress();
        updateRatingButtonLabels();
        setRatingEnabled(true);
        binding.layoutRating.setVisibility(View.GONE);
        resetCardFace();

        binding.cardStudy.setAlpha(0f);
        binding.cardStudy.setTranslationY(28f);
        binding.cardStudy.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180)
                .start();
    }

    private void bindCard(Flashcard flashcard) {
        binding.textTerm.setText(nonEmpty(flashcard.getTerm(), "Flashcard"));
        binding.textDefinition.setText(nonEmpty(flashcard.getDefinition(), "Chưa có nghĩa"));

        String tag = flashcard.getTag();
        if (tag != null && !tag.trim().isEmpty()) {
            binding.textTag.setText(tag.trim());
            binding.textTag.setVisibility(View.VISIBLE);
        } else {
            binding.textTag.setVisibility(View.GONE);
        }

        String example = flashcard.getExample();
        if (example != null && !example.trim().isEmpty()) {
            binding.textExample.setText(example.trim());
            binding.textExample.setVisibility(View.VISIBLE);
        } else {
            binding.textExample.setVisibility(View.GONE);
        }

        String imageUrl = flashcard.getImageUrl();
        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            binding.imageCard.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.macdinh)
                    .error(R.drawable.macdinh)
                    .into(binding.imageCard);
        } else {
            binding.imageCard.setVisibility(View.GONE);
        }
    }

    private void flipToBack() {
        if (currentCard == null || showingBack) return;
        showingBack = true;

        float scale = getResources().getDisplayMetrics().density;
        binding.cardFront.setCameraDistance(8000f * scale);
        binding.cardBack.setCameraDistance(8000f * scale);

        binding.cardFront.setVisibility(View.VISIBLE);
        binding.cardBack.setVisibility(View.VISIBLE);
        binding.cardFront.setRotationY(0f);
        binding.cardBack.setRotationY(-180f);
        binding.cardFront.setAlpha(1f);
        binding.cardBack.setAlpha(0f);

        AnimatorSet frontAnimator = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.animator.front_animator);
        AnimatorSet backAnimator = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.animator.back_animator);
        frontAnimator.setTarget(binding.cardFront);
        backAnimator.setTarget(binding.cardBack);
        frontAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                binding.cardFront.setVisibility(View.GONE);
            }
        });
        frontAnimator.start();
        backAnimator.start();

        binding.layoutRating.setAlpha(0f);
        binding.layoutRating.setTranslationY(16f);
        binding.layoutRating.setVisibility(View.VISIBLE);
        binding.layoutRating.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(160)
                .setDuration(180)
                .start();
    }

    private void rateCurrentCard(SpacedRepetitionCalculator.Rating rating) {
        if (currentCard == null || !showingBack) return;
        setRatingEnabled(false);

        FlashcardStudyRepository.StudyCard reviewedCard = currentCard;
        studyRepository.saveReview(uid, reviewedCard, rating)
                .addOnSuccessListener(progress -> {
                    reviewedCardIds.add(reviewedCard.getCardId());
                    if (rating == SpacedRepetitionCalculator.Rating.HARD
                            && hardRequeuedCardIds.add(reviewedCard.getCardId())) {
                        queue.offer(studyRepository.withProgress(reviewedCard, progress));
                        totalSteps++;
                    }
                    completedSteps++;
                    animateToNextCard(rating);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Chưa lưu được kết quả ôn tập", Toast.LENGTH_SHORT).show();
                    setRatingEnabled(true);
                });
    }

    private void animateToNextCard(SpacedRepetitionCalculator.Rating rating) {
        float targetX;
        if (rating == SpacedRepetitionCalculator.Rating.HARD) {
            targetX = -binding.cardStudy.getWidth() * 0.18f;
        } else if (rating == SpacedRepetitionCalculator.Rating.EASY) {
            targetX = binding.cardStudy.getWidth() * 0.18f;
        } else {
            targetX = 0f;
        }

        binding.cardStudy.animate()
                .alpha(0f)
                .translationX(targetX)
                .setDuration(180)
                .withEndAction(() -> {
                    binding.cardStudy.setTranslationX(0f);
                    showNextCard();
                })
                .start();
    }

    private void completeSession() {
        int reviewedCount = reviewedCardIds.size();
        int xpEarned = reviewedCount * SpacedRepetitionConstants.XP_PER_REVIEWED_CARD;
        showState(false,
                "Hoàn thành phiên ôn tập",
                "Bạn đã ôn " + reviewedCount + " thẻ và nhận " + xpEarned + " XP.",
                "Chọn bộ khác");

        if (!completionAwarded && reviewedCount > 0 && uid != null) {
            completionAwarded = true;
            gamificationRepository.awardStudyCompletion(uid, xpEarned);
        }
    }

    private void showStudySetLoading() {
        binding.layoutProgress.setVisibility(View.GONE);
        binding.layoutStudy.setVisibility(View.GONE);
        binding.layoutRating.setVisibility(View.GONE);
        binding.layoutState.setVisibility(View.VISIBLE);
        binding.progressLoading.setVisibility(View.VISIBLE);
        binding.recyclerStudySets.setVisibility(View.GONE);
        binding.textStateTitle.setText("Đang tải bộ thẻ");
        binding.textStateMessage.setText("Tìm các bộ đã lưu, tải về, tự tạo và Personal Flashcards...");
        binding.btnStatePrimary.setVisibility(View.GONE);
    }

    private void showLoading() {
        binding.layoutProgress.setVisibility(View.GONE);
        binding.layoutStudy.setVisibility(View.GONE);
        binding.layoutRating.setVisibility(View.GONE);
        binding.layoutState.setVisibility(View.VISIBLE);
        binding.progressLoading.setVisibility(View.VISIBLE);
        binding.recyclerStudySets.setVisibility(View.GONE);
        binding.textStateTitle.setText("Đang chuẩn bị thẻ");
        binding.textStateMessage.setText("Sắp xếp các thẻ cần ôn hôm nay...");
        binding.btnStatePrimary.setVisibility(View.GONE);
    }

    private void showState(boolean loading, String title, String message, String buttonText) {
        binding.layoutProgress.setVisibility(View.GONE);
        binding.layoutStudy.setVisibility(View.GONE);
        binding.layoutRating.setVisibility(View.GONE);
        binding.layoutState.setVisibility(View.VISIBLE);
        binding.progressLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.recyclerStudySets.setVisibility(View.GONE);
        binding.textStateTitle.setText(title);
        binding.textStateMessage.setText(message);
        binding.btnStatePrimary.setText(buttonText);
        binding.btnStatePrimary.setVisibility(View.VISIBLE);
    }

    private void resetCardFace() {
        binding.cardFront.setVisibility(View.VISIBLE);
        binding.cardBack.setVisibility(View.GONE);
        binding.cardFront.setRotationY(0f);
        binding.cardBack.setRotationY(-180f);
        binding.cardFront.setAlpha(1f);
        binding.cardBack.setAlpha(0f);
    }

    private void updateProgress() {
        int currentPosition = Math.min(completedSteps + 1, Math.max(totalSteps, 1));
        binding.textProgress.setText(currentPosition + " / " + Math.max(totalSteps, 1));
        int progress = totalSteps > 0 ? Math.round((completedSteps * 100f) / totalSteps) : 0;
        binding.progressStudy.setProgress(progress);
    }

    private void updateRatingButtonLabels() {
        if (currentCard == null) return;
        long now = System.currentTimeMillis();
        binding.btnEasy.setText("Dễ (" + formatReviewDelay(SpacedRepetitionCalculator.Rating.EASY, now) + ")");
        binding.btnMedium.setText("Vừa (" + formatReviewDelay(SpacedRepetitionCalculator.Rating.MEDIUM, now) + ")");
        binding.btnHard.setText("Khó (" + formatReviewDelay(SpacedRepetitionCalculator.Rating.HARD, now) + ")");
    }

    private String formatReviewDelay(SpacedRepetitionCalculator.Rating rating, long nowMillis) {
        SpacedRepetitionCalculator.ReviewResult result =
                SpacedRepetitionCalculator.calculate(currentCard.getProgress(), rating, nowMillis);
        long delayMillis = Math.max(0L, result.getNextReviewMillis() - nowMillis);
        long minutes = Math.max(1L, TimeUnit.MILLISECONDS.toMinutes(delayMillis));
        if (minutes < 60L) {
            return minutes + "m";
        }

        long hours = TimeUnit.MILLISECONDS.toHours(delayMillis);
        if (hours < 24L) {
            return Math.max(1L, hours) + "h";
        }

        long days = TimeUnit.MILLISECONDS.toDays(delayMillis);
        return Math.max(1L, days) + "d";
    }

    private void setRatingEnabled(boolean enabled) {
        binding.btnHard.setEnabled(enabled);
        binding.btnMedium.setEnabled(enabled);
        binding.btnEasy.setEnabled(enabled);
    }

    private String nonEmpty(String value, String fallback) {
        return value != null && !value.trim().isEmpty() ? value.trim() : fallback;
    }
}
