package com.example.vocabmaster.ui.study;

import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.data.repository.CourseRepository;
import com.example.vocabmaster.databinding.ActivityStudyBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StudyActivity extends AppCompatActivity {
    private static final String TAG = "StudyActivity";
    private ActivityStudyBinding binding;
    private FirebaseFirestore db;
    private CourseRepository repository;
    private MediaPlayer mediaPlayer;

    private List<String> wordIds = new ArrayList<>();
    private int currentIndex = 0;
    private Vocabulary currentVocab;
    private String lessonId;
    
    private AnimatorSet frontAnim;
    private AnimatorSet backAnim;
    private boolean isFront = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        repository = new CourseRepository(getApplication());
        mediaPlayer = new MediaPlayer();

        lessonId = getIntent().getStringExtra("lesson_id");
        ArrayList<String> ids = getIntent().getStringArrayListExtra("word_ids");
        if (ids != null) {
            wordIds.addAll(ids);
        }
        
        currentIndex = getIntent().getIntExtra("start_index", 0);
        String lessonTitle = getIntent().getStringExtra("lesson_title");
        if (lessonTitle != null) binding.textHeaderTitle.setText(lessonTitle);

        setupAnimations();
        setupListeners();

        if (lessonId != null && wordIds.isEmpty()) {
            loadLessonData(lessonId);
        } else {
            loadCurrentWord();
        }
    }

    private void loadLessonData(String id) {
        binding.studyProgress.setIndeterminate(true);
        db.collection("lessons").document(id).get().addOnSuccessListener(doc -> {
            binding.studyProgress.setIndeterminate(false);
            if (doc.exists()) {
                List<String> words = (List<String>) doc.get("vocabWords");
                if (words != null && !words.isEmpty()) {
                    wordIds.addAll(words);
                    loadCurrentWord();
                } else {
                    Toast.makeText(this, "Bài học chưa có từ vựng", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }).addOnFailureListener(e -> {
            binding.studyProgress.setIndeterminate(false);
            finish();
        });
    }

    private void setupAnimations() {
        float scale = getResources().getDisplayMetrics().density;
        binding.cardFront.setCameraDistance(8000 * scale);
        binding.cardBack.setCameraDistance(8000 * scale);

        frontAnim = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.animator.front_animator);
        backAnim = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.animator.back_animator);
    }

    private void setupListeners() {
        binding.btnClose.setOnClickListener(v -> finish());
        binding.cardFlashcard.setOnClickListener(v -> flipCard());
        
        binding.btnNext.setOnClickListener(v -> handleNext());
        binding.btnSkip.setOnClickListener(v -> handleNext());

        binding.btnListen.setOnClickListener(v -> {
            if (currentVocab != null) playAudio(currentVocab.getAnyAudioUrl());
        });

        binding.btnAddFlashcard.setOnClickListener(v -> saveToPersonalLibrary());
    }

    private void handleNext() {
        if (currentIndex < wordIds.size() - 1) {
            currentIndex++;
            loadCurrentWord();
        } else {
            completeLesson();
        }
    }

    private void completeLesson() {
        if (lessonId == null) {
            finish();
            return;
        }

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            finish();
            return;
        }

        binding.btnNext.setEnabled(false);
        Toast.makeText(this, "Đang lưu tiến trình...", Toast.LENGTH_SHORT).show();

        // 1. Tìm tất cả challenges thuộc bài học này
        db.collection("challenges")
                .whereEqualTo("lessonId", lessonId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        // Nếu không có challenge, vẫn đóng activity
                        setResult(RESULT_OK);
                        finish();
                        return;
                    }

                    WriteBatch batch = db.batch();
                    for (DocumentSnapshot doc : querySnapshot) {
                        String challengeId = doc.getId();
                        Map<String, Object> progress = new HashMap<>();
                        progress.put("userId", uid);
                        progress.put("challengeId", challengeId);
                        progress.put("completed", true);
                        progress.put("completedAt", Timestamp.now());
                        
                        // Tạo hoặc cập nhật progress
                        batch.set(db.collection("challengeProgress").document(uid + "_" + challengeId), progress);
                    }
                    
                    batch.commit().addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Bài học đã hoàn thành!", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "Lỗi lưu tiến trình", e);
                        finish();
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Lỗi truy vấn challenges", e);
                    finish();
                });
    }

    private void loadCurrentWord() {
        if (wordIds.isEmpty() || currentIndex >= wordIds.size()) return;

        isFront = true;
        binding.cardFront.setAlpha(1f);
        binding.cardFront.setRotationY(0f);
        binding.cardBack.setAlpha(0f);
        binding.cardBack.setRotationY(180f); 

        String wordId = wordIds.get(currentIndex);
        updateProgressUI();

        db.collection("vocabularies").document(wordId).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    currentVocab = doc.toObject(Vocabulary.class);
                    displayVocab(currentVocab);
                }
            });
    }

    private void flipCard() {
        if (isFront) {
            frontAnim.setTarget(binding.cardFront);
            backAnim.setTarget(binding.cardBack);
            frontAnim.start();
            backAnim.start();
            isFront = false;
        } else {
            frontAnim.setTarget(binding.cardBack);
            backAnim.setTarget(binding.cardFront);
            frontAnim.start();
            backAnim.start();
            isFront = true;
        }
        UiFeedback.performHaptic(this, 5);
    }

    private void updateProgressUI() {
        int total = wordIds.size();
        int current = currentIndex + 1;
        int progress = (int) (((float) current / total) * 100);
        binding.studyProgress.setProgress(progress);
        binding.textHeaderTitle.setText("Từ " + current + " / " + total);
    }

    private void displayVocab(Vocabulary vocab) {
        if (vocab == null) return;
        binding.textTerm.setText(vocab.getWord());
        binding.textDefinition.setText(vocab.getDefinition());
        binding.textPhonetic.setText(vocab.getPhonetic());
        binding.textPhonetic.setVisibility(vocab.getPhonetic() != null ? View.VISIBLE : View.GONE);
        binding.btnListen.setVisibility(vocab.getAnyAudioUrl() != null ? View.VISIBLE : View.GONE);
        
        // Reset nút ngôi sao
        binding.btnAddFlashcard.setImageResource(android.R.drawable.btn_star_big_off);
    }

    private void saveToPersonalLibrary() {
        if (currentVocab == null) return;
        Flashcard card = new Flashcard(currentVocab.getWord(), currentVocab.getDefinition());
        card.setPhonetic(currentVocab.getPhonetic());
        card.setAudioUrl(currentVocab.getAnyAudioUrl());
        repository.addPersonalFlashcard(card);
        
        binding.btnAddFlashcard.setImageResource(android.R.drawable.btn_star_big_on);
        UiFeedback.showSnack(binding.getRoot(), "Đã lưu vào thư viện cá nhân");
    }

    private void playAudio(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
        } catch (IOException e) {
            Log.e(TAG, "Audio error", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) mediaPlayer.release();
    }
}
