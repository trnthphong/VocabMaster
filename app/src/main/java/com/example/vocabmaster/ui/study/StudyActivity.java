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
    
    // Animation lật thẻ
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

        // Lấy dữ liệu từ intent
        lessonId = getIntent().getStringExtra("lesson_id");
        ArrayList<String> ids = getIntent().getStringArrayListExtra("word_ids");
        if (ids != null) {
            wordIds.addAll(ids);
        } else {
            String singleId = getIntent().getStringExtra("word_id");
            if (singleId != null) wordIds.add(singleId);
        }
        
        currentIndex = getIntent().getIntExtra("start_index", 0);
        if (currentIndex >= wordIds.size()) currentIndex = 0;

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
                    Toast.makeText(this, "Bài học này chưa có từ vựng", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }).addOnFailureListener(e -> {
            binding.studyProgress.setIndeterminate(false);
            Toast.makeText(this, "Lỗi tải bài học", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void setupAnimations() {
        // Thiết lập camera distance để hiệu ứng lật không bị "vỡ" hình
        float scale = getResources().getDisplayMetrics().density;
        binding.cardFront.setCameraDistance(8000 * scale);
        binding.cardBack.setCameraDistance(8000 * scale);

        try {
            frontAnim = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.animator.front_animator);
            backAnim = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.animator.back_animator);
        } catch (Exception e) {
            Log.e(TAG, "Error loading animators", e);
        }
    }

    private void setupListeners() {
        binding.btnClose.setOnClickListener(v -> finish());
        
        binding.cardFlashcard.setOnClickListener(v -> flipCard());
        
        binding.btnNext.setOnClickListener(v -> {
            if (currentIndex < wordIds.size() - 1) {
                currentIndex++;
                loadCurrentWord();
            } else {
                completeLesson();
            }
        });

        binding.btnSkip.setOnClickListener(v -> {
            if (currentIndex < wordIds.size() - 1) {
                currentIndex++;
                loadCurrentWord();
            } else {
                completeLesson();
            }
        });

        binding.btnListen.setOnClickListener(v -> {
            if (currentVocab != null) {
                playAudio(currentVocab.getAnyAudioUrl());
            }
        });

        binding.btnSaveToLibrary.setOnClickListener(v -> saveToPersonalLibrary());
    }

    private void completeLesson() {
        if (lessonId == null) {
            Toast.makeText(this, "Chúc mừng! Bạn đã hoàn thành lộ trình này.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        binding.btnNext.setEnabled(false);
        binding.btnSkip.setEnabled(false);
        Toast.makeText(this, "Đang lưu tiến trình...", Toast.LENGTH_SHORT).show();

        // Tìm tất cả challenges của bài học này và đánh dấu hoàn thành
        db.collection("challenges")
                .whereEqualTo("lessonId", lessonId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    WriteBatch batch = db.batch();
                    for (DocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> progress = new HashMap<>();
                        progress.put("userId", uid);
                        progress.put("challengeId", doc.getId());
                        progress.put("completed", true);
                        progress.put("completedAt", Timestamp.now());
                        
                        // ID duy nhất: userId + challengeId
                        batch.set(db.collection("challengeProgress").document(uid + "_" + doc.getId()), progress);
                    }
                    
                    batch.commit().addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Tuyệt vời! Bài học đã hoàn thành.", Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK);
                        finish();
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "Batch failed", e);
                        finish();
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Query challenges failed", e);
                    finish();
                });
    }

    private void loadCurrentWord() {
        if (wordIds.isEmpty() || currentIndex >= wordIds.size()) return;

        // Reset trạng thái mặt thẻ về mặt trước
        if (!isFront) {
            binding.cardFront.setVisibility(View.VISIBLE);
            binding.cardBack.setVisibility(View.GONE);
            binding.cardFront.setAlpha(1f);
            binding.cardBack.setAlpha(0f);
            binding.cardFront.setRotationY(0);
            binding.cardBack.setRotationY(-180);
            isFront = true;
        } else {
            // Đảm bảo trạng thái ban đầu đúng
            binding.cardFront.setVisibility(View.VISIBLE);
            binding.cardBack.setVisibility(View.GONE);
            binding.cardFront.setAlpha(1f);
            binding.cardBack.setAlpha(0f);
            binding.cardFront.setRotationY(0);
            binding.cardBack.setRotationY(-180);
        }

        String wordId = wordIds.get(currentIndex);
        updateProgressUI();

        binding.btnNext.setEnabled(false); // Disable cho đến khi load xong

        db.collection("vocabularies").document(wordId).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    displayVocab(doc.toObject(Vocabulary.class));
                } else {
                    db.collection("russian_vocabularies").document(wordId).get()
                        .addOnSuccessListener(docRu -> {
                            if (docRu.exists()) displayVocab(docRu.toObject(Vocabulary.class));
                        });
                }
                binding.btnNext.setEnabled(true);
            })
            .addOnFailureListener(e -> {
                binding.btnNext.setEnabled(true);
                Toast.makeText(this, "Lỗi tải dữ liệu", Toast.LENGTH_SHORT).show();
            });
    }

    private void updateProgressUI() {
        int progress = (int) (((float)(currentIndex + 1) / wordIds.size()) * 100);
        binding.studyProgress.setProgress(progress);
        binding.textHeaderTitle.setText("Từ " + (currentIndex + 1) + " / " + wordIds.size());
    }

    private void displayVocab(Vocabulary vocab) {
        currentVocab = vocab;
        if (vocab == null) return;

        binding.textTerm.setText(vocab.getWord());
        binding.textDefinition.setText(vocab.getDefinition());
        
        if (vocab.getPhonetic() != null && !vocab.getPhonetic().isEmpty()) {
            binding.textPhonetic.setText(vocab.getPhonetic());
            binding.textPhonetic.setVisibility(View.VISIBLE);
        } else {
            binding.textPhonetic.setVisibility(View.GONE);
        }
        
        binding.btnListen.setVisibility(vocab.getAnyAudioUrl() != null ? View.VISIBLE : View.GONE);
        
        binding.btnSaveToLibrary.setEnabled(true);
        binding.btnSaveToLibrary.setText("Lưu vào thư viện");
        binding.btnSaveToLibrary.setVisibility(View.VISIBLE);
        
        binding.btnNext.setVisibility(View.VISIBLE);
        binding.btnSkip.setVisibility(View.VISIBLE);
    }

    private void flipCard() {
        if (frontAnim == null || backAnim == null) return;
        
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

    private void saveToPersonalLibrary() {
        if (currentVocab == null) return;
        Flashcard card = new Flashcard(currentVocab.getWord(), currentVocab.getDefinition());
        card.setPhonetic(currentVocab.getPhonetic());
        card.setAudioUrl(currentVocab.getAnyAudioUrl());
        card.setImageUrl(currentVocab.getImageUrl());
        card.setTag(getIntent().getStringExtra("topic") != null ? getIntent().getStringExtra("topic") : "Journey");
        
        repository.addPersonalFlashcard(card);
        
        binding.btnSaveToLibrary.setEnabled(false);
        binding.btnSaveToLibrary.setText("Đã lưu");
        UiFeedback.showSnack(binding.getRoot(), "Đã thêm vào bộ sưu tập cá nhân");
        UiFeedback.performHaptic(this, 10);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
