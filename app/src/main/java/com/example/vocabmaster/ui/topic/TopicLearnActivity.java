package com.example.vocabmaster.ui.topic;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.local.VocabularyDao;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.databinding.ActivityTopicLearnBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TopicLearnActivity extends AppCompatActivity {

    public static final int BATCH_SIZE = 7; // 5-10 từ mỗi lần

    private ActivityTopicLearnBinding binding;
    private VocabularyDao vocabularyDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String topicId;
    private String topicTitle;
    private boolean isPersonal;

    private List<Vocabulary> batch = new ArrayList<>();
    private List<LearnStep> steps = new ArrayList<>();
    private int currentStepIndex = 0;
    private int learnedCount = 0;
    private MediaPlayer mediaPlayer;

    // Represents one exercise step
    static class LearnStep {
        enum Type { INTRO, WORD_TO_MEANING, MEANING_TO_WORD, LISTEN_CHOOSE, SUMMARY }
        Type type;
        Vocabulary vocab;
        List<Vocabulary> allBatch; // for generating wrong options

        LearnStep(Type type, Vocabulary vocab, List<Vocabulary> allBatch) {
            this.type = type;
            this.vocab = vocab;
            this.allBatch = allBatch;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTopicLearnBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        topicId = getIntent().getStringExtra("topic_id");
        topicTitle = getIntent().getStringExtra("topic_title");
        isPersonal = getIntent().getBooleanExtra("is_personal_topic", false);

        binding.toolbar.setTitle("Học: " + (topicTitle != null ? topicTitle : ""));
        binding.toolbar.setNavigationOnClickListener(v -> confirmExit());

        vocabularyDao = AppDatabase.getDatabase(this).vocabularyDao();
        mediaPlayer = new MediaPlayer();

        // Nhận list từ đã chọn từ TopicWordPickActivity
        String json = getIntent().getStringExtra("selected_words_json");
        if (json != null) {
            batch = new com.google.gson.Gson().fromJson(json,
                    new com.google.gson.reflect.TypeToken<List<Vocabulary>>(){}.getType());
            buildSteps();
            showCurrentStep();
        } else {
            // fallback: tự load nếu không có json (không nên xảy ra)
            loadBatch();
        }
    }

    private void loadBatch() {
        String key = isPersonal ? topicId : topicId.toLowerCase();
        binding.progressBar.setVisibility(View.GONE);

        executor.execute(() -> {
            // Priority: new words first, then learning words
            List<Vocabulary> newWords = vocabularyDao.getNewWordsByTopic(key);
            List<Vocabulary> batchList = new ArrayList<>(newWords);

            if (batchList.size() < BATCH_SIZE) {
                List<Vocabulary> learning = vocabularyDao.getLearnedWordsByTopic(key);
                // filter only status=1 (learning)
                for (Vocabulary v : learning) {
                    if (v.getLearnStatus() == 1 && batchList.size() < BATCH_SIZE) {
                        batchList.add(v);
                    }
                }
            }

            mainHandler.post(() -> {
                binding.progressBar.setVisibility(View.GONE);
                if (batchList.isEmpty()) {
                    Toast.makeText(this, "Không còn từ mới để học!", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                batch = batchList;
                buildSteps();
                showCurrentStep();
            });
        });
    }

    private void buildSteps() {
        steps.clear();
        for (Vocabulary v : batch) {
            // Step 1: Intro (làm quen)
            steps.add(new LearnStep(LearnStep.Type.INTRO, v, batch));
            // Step 2: Word → Meaning (chọn nghĩa)
            steps.add(new LearnStep(LearnStep.Type.WORD_TO_MEANING, v, batch));
            // Step 3: Meaning → Word (ghi từ)
            steps.add(new LearnStep(LearnStep.Type.MEANING_TO_WORD, v, batch));
            // Step 4: Listen → Choose (chỉ thêm nếu có audio)
            if (v.getAnyAudioUrl() != null && !v.getAnyAudioUrl().isEmpty()) {
                steps.add(new LearnStep(LearnStep.Type.LISTEN_CHOOSE, v, batch));
            }
        }
        // Final summary step
        steps.add(new LearnStep(LearnStep.Type.SUMMARY, null, batch));

        updateProgress();
    }

    private void showCurrentStep() {
        if (currentStepIndex >= steps.size()) {
            showSummary();
            return;
        }
        LearnStep step = steps.get(currentStepIndex);
        updateProgress();

        switch (step.type) {
            case INTRO:        showIntro(step.vocab); break;
            case WORD_TO_MEANING: showWordToMeaning(step.vocab, step.allBatch); break;
            case MEANING_TO_WORD: showMeaningToWord(step.vocab, step.allBatch); break;
            case LISTEN_CHOOSE:   showListenChoose(step.vocab, step.allBatch); break;
            case SUMMARY:         showSummary(); break;
        }
    }

    private void showIntro(Vocabulary v) {
        LearnStepFragment fragment = LearnStepFragment.newIntro(v);
        fragment.setOnNextListener(skipped -> {
            if (!skipped) markLearning(v);
            nextStep();
        });
        getSupportFragmentManager().beginTransaction()
                .replace(binding.fragmentContainer.getId(), fragment)
                .commitNow();
    }

    private void showWordToMeaning(Vocabulary v, List<Vocabulary> all) {
        LearnStepFragment fragment = LearnStepFragment.newWordToMeaning(v, all);
        fragment.setOnNextListener(correct -> {
            if (correct) learnedCount++;
            nextStep();
        });
        getSupportFragmentManager().beginTransaction()
                .replace(binding.fragmentContainer.getId(), fragment)
                .commitNow();
    }

    private void showMeaningToWord(Vocabulary v, List<Vocabulary> all) {
        LearnStepFragment fragment = LearnStepFragment.newMeaningToWord(v, all);
        fragment.setOnNextListener(correct -> {
            if (correct) learnedCount++;
            nextStep();
        });
        getSupportFragmentManager().beginTransaction()
                .replace(binding.fragmentContainer.getId(), fragment)
                .commitNow();
    }

    private void showListenChoose(Vocabulary v, List<Vocabulary> all) {
        LearnStepFragment fragment = LearnStepFragment.newListenChoose(v, all, mediaPlayer);
        fragment.setOnNextListener(correct -> {
            if (correct) learnedCount++;
            nextStep();
        });
        getSupportFragmentManager().beginTransaction()
                .replace(binding.fragmentContainer.getId(), fragment)
                .commitNow();
    }

    private void showSummary() {
        // Mark all batch words as learned in DB
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            for (Vocabulary v : batch) {
                vocabularyDao.updateLearnStatus(v.getVocabularyId(), 2, now);
            }
            // Save XP to Firestore (no heart deduction)
            int xpGained = batch.size() * 5;
            saveXp(xpGained);
        });

        LearnStepFragment fragment = LearnStepFragment.newSummary(batch.size(), learnedCount);
        fragment.setOnNextListener(ignored -> finish());
        getSupportFragmentManager().beginTransaction()
                .replace(binding.fragmentContainer.getId(), fragment)
                .commitNow();

        binding.studyProgress.setProgress(100);
    }

    private void markLearning(Vocabulary v) {
        if (v.getLearnStatus() == 0) {
            executor.execute(() ->
                vocabularyDao.updateLearnStatus(v.getVocabularyId(), 1, System.currentTimeMillis()));
        }
    }

    private void saveXp(int xp) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("xp", com.google.firebase.firestore.FieldValue.increment(xp));
    }

    private void nextStep() {
        currentStepIndex++;
        showCurrentStep();
    }

    private void updateProgress() {
        int total = steps.size();
        int progress = total > 0 ? (int) ((currentStepIndex / (float) total) * 100) : 0;
        binding.studyProgress.setProgress(progress);
        binding.textStepCount.setText(Math.min(currentStepIndex + 1, total) + "/" + total);
    }

    private void confirmExit() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Thoát bài học?")
                .setMessage("Tiến độ bài học này sẽ không được lưu.")
                .setPositiveButton("Thoát", (d, w) -> finish())
                .setNegativeButton("Tiếp tục học", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) { mediaPlayer.release(); mediaPlayer = null; }
    }
}
