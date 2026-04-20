package com.example.vocabmaster.ui.topic;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.local.VocabularyDao;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.databinding.ActivityTopicLearnBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TopicLearnActivity extends AppCompatActivity {

    private ActivityTopicLearnBinding binding;
    private VocabularyDao vocabularyDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String topicId;
    private String topicTitle;
    private boolean isPersonal;

    List<Vocabulary> batch = new ArrayList<>();
    // Queue câu hỏi — sai sẽ đẩy lại vào cuối
    Queue<LearnStep> stepQueue = new LinkedList<>();
    int totalSteps = 0;
    int doneSteps = 0;
    int correctCount = 0;
    MediaPlayer mediaPlayer;

    static class LearnStep {
        enum Type { INTRO_ALL, WORD_TO_MEANING, MEANING_TO_WORD, WORD_TO_VIETNAMESE, VIETNAMESE_TO_WORD, LISTEN_CHOOSE, SUMMARY }
        Type type;
        Vocabulary vocab;
        List<Vocabulary> allBatch;
        int retryCount = 0;

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
        LearnStepFragment.sSharedMediaPlayer = mediaPlayer;

        String json = getIntent().getStringExtra("selected_words_json");
        if (json != null) {
            batch = new com.google.gson.Gson().fromJson(json,
                    new com.google.gson.reflect.TypeToken<List<Vocabulary>>(){}.getType());
            buildQueue();
            showCurrentStep();
        }
    }

    private void buildQueue() {
        stepQueue.clear();
        List<LearnStep> practiceSteps = new ArrayList<>();

        // Bước 1: Làm quen tất cả từ cùng lúc (1 màn hình duy nhất)
        stepQueue.add(new LearnStep(LearnStep.Type.INTRO_ALL, null, batch));

        // Bước 2+: Tạo câu hỏi luyện tập cho từng từ, mỗi từ 2-3 dạng
        for (Vocabulary v : batch) {
            practiceSteps.add(new LearnStep(LearnStep.Type.WORD_TO_MEANING, v, batch));
            practiceSteps.add(new LearnStep(LearnStep.Type.MEANING_TO_WORD, v, batch));
            if (v.getAnyAudioUrl() != null && !v.getAnyAudioUrl().isEmpty()) {
                practiceSteps.add(new LearnStep(LearnStep.Type.LISTEN_CHOOSE, v, batch));
            }
            // Thêm câu hỏi tiếng Việt nếu có
            String vi = v.getVietnamese_translation();
            if (vi != null && !vi.isEmpty()) {
                practiceSteps.add(new LearnStep(LearnStep.Type.WORD_TO_VIETNAMESE, v, batch));
                practiceSteps.add(new LearnStep(LearnStep.Type.VIETNAMESE_TO_WORD, v, batch));
            }
            // Thêm lần 2 word→meaning để củng cố
            practiceSteps.add(new LearnStep(LearnStep.Type.WORD_TO_MEANING, v, batch));
        }

        // Xáo trộn toàn bộ câu hỏi luyện tập
        Collections.shuffle(practiceSteps);
        stepQueue.addAll(practiceSteps);

        // Summary ở cuối
        stepQueue.add(new LearnStep(LearnStep.Type.SUMMARY, null, batch));

        totalSteps = stepQueue.size();
        updateProgress();
    }

    void showCurrentStep() {
        LearnStep step = stepQueue.peek();
        if (step == null) { showSummaryScreen(); return; }

        updateProgress();

        switch (step.type) {
            case INTRO_ALL:          showIntroAll(); break;
            case WORD_TO_MEANING:    showExercise(step, false); break;
            case MEANING_TO_WORD:    showExercise(step, false); break;
            case WORD_TO_VIETNAMESE: showExercise(step, false); break;
            case VIETNAMESE_TO_WORD: showExercise(step, false); break;
            case LISTEN_CHOOSE:      showExercise(step, false); break;
            case SUMMARY:            showSummaryScreen(); break;
        }
    }

    private void showIntroAll() {
        LearnStep step = stepQueue.poll(); // consume
        LearnStepFragment fragment = LearnStepFragment.newIntroAll(batch, mediaPlayer);
        fragment.setOnNextListener(ignored -> {
            doneSteps++;
            showCurrentStep();
        });
        commitFragment(fragment);
    }

    private void showExercise(LearnStep step, boolean isRetry) {
        LearnStepFragment fragment;
        switch (step.type) {
            case WORD_TO_MEANING:
                fragment = LearnStepFragment.newWordToMeaning(step.vocab, step.allBatch);
                break;
            case MEANING_TO_WORD:
                fragment = LearnStepFragment.newMeaningToWord(step.vocab, step.allBatch);
                break;
            case WORD_TO_VIETNAMESE:
                fragment = LearnStepFragment.newWordToVietnamese(step.vocab, step.allBatch);
                break;
            case VIETNAMESE_TO_WORD:
                fragment = LearnStepFragment.newVietnameseToWord(step.vocab, step.allBatch);
                break;
            case LISTEN_CHOOSE:
                fragment = LearnStepFragment.newListenChoose(step.vocab, step.allBatch, mediaPlayer);
                break;
            default:
                nextStep();
                return;
        }

        fragment.setOnNextListener(correct -> {
            stepQueue.poll(); // consume current
            doneSteps++;
            if (correct) {
                correctCount++;
            } else if (step.retryCount < 2) {
                // Sai → đẩy lại vào cuối queue (tối đa 2 lần retry)
                step.retryCount++;
                // Thêm vào trước SUMMARY
                List<LearnStep> remaining = new ArrayList<>(stepQueue);
                // Tìm vị trí trước SUMMARY
                int insertAt = remaining.size();
                for (int i = 0; i < remaining.size(); i++) {
                    if (remaining.get(i).type == LearnStep.Type.SUMMARY) {
                        insertAt = i;
                        break;
                    }
                }
                remaining.add(insertAt, step);
                stepQueue.clear();
                stepQueue.addAll(remaining);
                totalSteps++; // thêm 1 step vì retry
            }
            showCurrentStep();
        });
        commitFragment(fragment);
    }

    private void showSummaryScreen() {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            for (Vocabulary v : batch) {
                vocabularyDao.updateLearnStatus(v.getVocabularyId(), 2, now);
            }
            saveXp(batch.size() * 5);
        });

        LearnStepFragment fragment = LearnStepFragment.newSummary(batch.size(), correctCount);
        fragment.setOnNextListener(ignored -> finish());
        commitFragment(fragment);
        binding.studyProgress.setProgress(100);
    }

    private void nextStep() {
        stepQueue.poll();
        doneSteps++;
        showCurrentStep();
    }

    private void commitFragment(LearnStepFragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(binding.fragmentContainer.getId(), fragment)
                .commitNow();
    }

    private void updateProgress() {
        if (totalSteps <= 0) return;
        int progress = (int) ((doneSteps / (float) totalSteps) * 100);
        binding.studyProgress.setProgress(Math.min(progress, 99));
    }

    private void saveXp(int xp) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("xp", com.google.firebase.firestore.FieldValue.increment(xp));
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
