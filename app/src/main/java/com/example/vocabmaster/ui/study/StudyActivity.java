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
    private CourseRepository repository;
    
    private boolean isFlashcardMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getUid();
        repository = new CourseRepository(getApplication());
        
        lessonId = getIntent().getStringExtra("lesson_id");
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
        } else {
            loadLessonData();
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
                // Glide load here if needed
            }
        }
    }

    private void saveToFirebaseLibrary() {
        if (currentVocab == null) return;
        
        Flashcard card = new Flashcard(currentVocab.getWord(), currentVocab.getDefinition());
        card.setImageUrl(currentVocab.getImageUrl());
        card.setAudioUrl(currentVocab.getAudioUrl());
        card.setExample(currentVocab.getExampleSentence());
        card.setTag(getIntent().getStringExtra("topic") != null ? getIntent().getStringExtra("topic") : "Journey");
        
        // Đồng bộ hóa: Sử dụng repository để đẩy lên Firebase (giống như ở TopicWordListActivity)
        repository.addPersonalFlashcard(card);
        
        Toast.makeText(this, "Đã đồng bộ vào thư viện Firebase", Toast.LENGTH_SHORT).show();
        UiFeedback.performHaptic(this, 10);
        binding.btnSaveToLibrary.setEnabled(false);
        binding.btnSaveToLibrary.setText("Đã lưu");
    }

    private void loadLessonData() {
        // ... (Keep existing lesson logic)
    }
}
