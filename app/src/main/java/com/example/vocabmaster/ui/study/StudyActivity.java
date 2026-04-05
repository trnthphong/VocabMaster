package com.example.vocabmaster.ui.study;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.model.Challenge;
import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.data.repository.CourseRepository;
import com.example.vocabmaster.databinding.ActivityStudyBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StudyActivity extends AppCompatActivity {
    private static final String TAG = "StudyActivity";
    private ActivityStudyBinding binding;
    private FirebaseFirestore db;
    private String wordId;
    private Vocabulary currentVocab;
    private CourseRepository repository;
    private MediaPlayer mediaPlayer;
    
    private boolean isFlashcardMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        repository = new CourseRepository(getApplication());
        mediaPlayer = new MediaPlayer();
        
        wordId = getIntent().getStringExtra("word_id");
        String lessonTitle = getIntent().getStringExtra("lesson_title");
        if (lessonTitle != null) binding.textHeaderTitle.setText(lessonTitle);

        if (wordId != null || getIntent().hasExtra("topic")) {
            isFlashcardMode = true;
            setupFlashcardOnlyUI();
        }

        setupListeners();
        
        if (isFlashcardMode) {
            loadSingleWordData();
        }
    }

    private void setupFlashcardOnlyUI() {
        binding.layoutStats.setVisibility(View.GONE);
        binding.btnSkip.setVisibility(View.GONE);
        binding.btnNext.setVisibility(View.GONE);
        binding.btnSaveToLibrary.setVisibility(View.VISIBLE);
        binding.cardFlashcard.setVisibility(View.VISIBLE);
        binding.dynamicTaskLayout.setVisibility(View.GONE);
        binding.studyProgress.setProgress(100);
    }

    private void setupListeners() {
        binding.btnClose.setOnClickListener(v -> finish());
        binding.cardFlashcard.setOnClickListener(v -> flipCard());
        binding.btnSaveToLibrary.setOnClickListener(v -> saveToFirebaseLibrary());
        
        binding.btnListen.setOnClickListener(v -> {
            if (currentVocab != null) {
                String url = currentVocab.getAnyAudioUrl();
                if (url != null && !url.trim().isEmpty()) {
                    playAudio(url);
                } else {
                    Toast.makeText(this, "Không có âm thanh cho từ này", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void playAudio(String url) {
        Log.d(TAG, "Playing audio: " + url);
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                return true;
            });
        } catch (IOException e) {
            Log.e(TAG, "Error playing audio", e);
        }
    }

    private void flipCard() {
        if (binding.cardFront.getVisibility() == View.VISIBLE) {
            binding.cardFront.setVisibility(View.GONE);
            binding.cardBack.setVisibility(View.VISIBLE);
        } else {
            binding.cardFront.setVisibility(View.VISIBLE);
            binding.cardBack.setVisibility(View.GONE);
        }
    }

    private void loadSingleWordData() {
        if (wordId == null) return;
        
        // Luôn tải từ Firebase để khớp dữ liệu mới nhất
        db.collection("vocabularies").document(wordId).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    displayVocab(doc);
                } else {
                    db.collection("russian_vocabularies").document(wordId).get()
                        .addOnSuccessListener(docRu -> {
                            if (docRu.exists()) displayVocab(docRu);
                        });
                }
            });
    }

    private void displayVocab(DocumentSnapshot doc) {
        // Mapping tự động từ Firebase sang Model mới đã cập nhật
        currentVocab = doc.toObject(Vocabulary.class);
        if (currentVocab == null) return;

        // Cập nhật UI
        binding.textTerm.setText(currentVocab.getWord());
        binding.textDefinition.setText(currentVocab.getDefinition());
        
        // HIỂN THỊ PHIÊN ÂM
        String phonetic = currentVocab.getPhonetic();
        if (phonetic != null && !phonetic.trim().isEmpty()) {
            binding.textPhonetic.setText(phonetic);
            binding.textPhonetic.setVisibility(View.VISIBLE);
        } else {
            binding.textPhonetic.setVisibility(View.GONE);
        }

        // HIỂN THỊ NÚT NGHE
        String audioUrl = currentVocab.getAnyAudioUrl();
        if (audioUrl != null && !audioUrl.trim().isEmpty()) {
            binding.btnListen.setVisibility(View.VISIBLE);
        } else {
            binding.btnListen.setVisibility(View.GONE);
        }

        if (currentVocab.getImageUrl() != null && !currentVocab.getImageUrl().trim().isEmpty()) {
            binding.imageVocab.setVisibility(View.VISIBLE);
            // Có thể dùng Glide để load ảnh ở đây
        }
    }

    private void saveToFirebaseLibrary() {
        if (currentVocab == null) return;
        
        Flashcard card = new Flashcard(currentVocab.getWord(), currentVocab.getDefinition());
        card.setImageUrl(currentVocab.getImageUrl());
        card.setAudioUrl(currentVocab.getAnyAudioUrl());
        card.setPhonetic(currentVocab.getPhonetic());
        card.setExample(currentVocab.getExampleSentence());
        card.setTag(getIntent().getStringExtra("topic") != null ? getIntent().getStringExtra("topic") : "Journey");
        
        repository.addPersonalFlashcard(card);
        
        Toast.makeText(this, "Đã lưu vào thư viện cá nhân", Toast.LENGTH_SHORT).show();
        UiFeedback.performHaptic(this, 10);
        binding.btnSaveToLibrary.setEnabled(false);
        binding.btnSaveToLibrary.setText("Đã lưu");
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
