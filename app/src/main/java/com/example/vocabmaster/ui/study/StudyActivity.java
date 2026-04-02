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
import com.example.vocabmaster.databinding.ActivityStudyBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StudyActivity extends AppCompatActivity {
    private static final String TAG = "StudyActivity";
    private ActivityStudyBinding binding;
    private FirebaseFirestore db;
    private String lessonId;
    private String wordId;
    private String userId;
    private User currentUser;
    private Vocabulary currentVocab;
    
    private boolean isFlashcardMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getUid();
        
        lessonId = getIntent().getStringExtra("lesson_id");
        wordId = getIntent().getStringExtra("word_id");
        String lessonTitle = getIntent().getStringExtra("lesson_title");
        
        if (lessonTitle != null) binding.textHeaderTitle.setText(lessonTitle);

        // Kiểm tra xem có phải đang ở chế độ xem Flashcard từ Journey không
        if (wordId != null || getIntent().hasExtra("topic")) {
            isFlashcardMode = true;
            setupFlashcardOnlyUI();
        }

        setupListeners();
        
        if (isFlashcardMode) {
            loadSingleWordData();
        } else {
            // Logic học bài học bình thường (đã có sẵn của bạn)
            loadLessonData();
        }
    }

    private void setupFlashcardOnlyUI() {
        // 1. Ẩn tim và XP
        binding.layoutStats.setVisibility(View.GONE);
        
        // 2. Ẩn các nút điều khiển học tập
        binding.btnSkip.setVisibility(View.GONE);
        binding.btnNext.setVisibility(View.GONE);
        
        // 3. Hiện nút Lưu vào thư viện
        binding.btnSaveToLibrary.setVisibility(View.VISIBLE);
        
        // 4. Đảm bảo hiển thị Flashcard
        binding.cardFlashcard.setVisibility(View.VISIBLE);
        binding.dynamicTaskLayout.setVisibility(View.GONE);
        
        // 5. Ẩn thanh progress (hoặc để 100%)
        binding.studyProgress.setProgress(100);
    }

    private void setupListeners() {
        binding.btnClose.setOnClickListener(v -> finish());
        
        binding.cardFlashcard.setOnClickListener(v -> flipCard());
        
        binding.btnSaveToLibrary.setOnClickListener(v -> saveToLocalLibrary());
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
        
        // Tìm trong cả 2 collection vocabularies (en/ru)
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
        currentVocab = doc.toObject(Vocabulary.class);
        if (currentVocab != null) {
            binding.textTerm.setText(currentVocab.getWord());
            binding.textDefinition.setText(currentVocab.getDefinition());
            if (currentVocab.getImageUrl() != null && !currentVocab.getImageUrl().isEmpty()) {
                binding.imageVocab.setVisibility(View.VISIBLE);
                // Bạn có thể dùng Glide để load ảnh ở đây
            }
        }
    }

    private void saveToLocalLibrary() {
        if (currentVocab == null) return;
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            Flashcard card = new Flashcard(currentVocab.getWord(), currentVocab.getDefinition());
            card.setImageUrl(currentVocab.getImageUrl());
            card.setAudioUrl(currentVocab.getAudioUrl());
            AppDatabase.getDatabase(this).flashcardDao().insert(card);
            
            runOnUiThread(() -> {
                Toast.makeText(this, "Đã lưu vào thư viện cá nhân", Toast.LENGTH_SHORT).show();
                UiFeedback.performHaptic(this, 10);
                binding.btnSaveToLibrary.setEnabled(false);
                binding.btnSaveToLibrary.setText("Đã lưu");
            });
        });
    }

    // --- Giữ lại các hàm cũ của bạn để không làm hỏng logic học bài học ---
    private void loadLessonData() {
        // ... (Giữ nguyên logic cũ của bạn ở đây)
    }
}
