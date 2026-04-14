package com.example.vocabmaster.ui.study;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Challenge;
import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.data.repository.CourseRepository;
import com.example.vocabmaster.databinding.ActivityStudyBinding;
import com.example.vocabmaster.databinding.LayoutFlashcardTopicBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StudyActivity extends AppCompatActivity {
    private static final String TAG = "StudyActivity";
    private ActivityStudyBinding binding;
    private LayoutFlashcardTopicBinding topicBinding;
    private FirebaseFirestore db;
    private String lessonId;
    private String wordId;
    private List<Challenge> challenges = new ArrayList<>();
    private int currentChallengeIndex = 0;
    private Vocabulary currentVocab;
    private CourseRepository repository;
    private MediaPlayer mediaPlayer;
    
    private boolean isFlashcardMode = false;
    private boolean useTopicLayout = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        repository = new CourseRepository(getApplication());
        mediaPlayer = new MediaPlayer();
        
        lessonId = getIntent().getStringExtra("lesson_id");
        wordId = getIntent().getStringExtra("word_id");
        useTopicLayout = getIntent().getBooleanExtra("use_topic_layout", false);
        String lessonTitle = getIntent().getStringExtra("lesson_title");
        if (lessonTitle != null) binding.textHeaderTitle.setText(lessonTitle);

        if (wordId != null || getIntent().hasExtra("topic")) {
            isFlashcardMode = true;
            setupFlashcardOnlyUI();
            loadSingleWordData();
        } else if (lessonId != null) {
            loadLessonChallenges();
        }

        setupListeners();
    }

    private void setupFlashcardOnlyUI() {
        binding.layoutStats.setVisibility(View.GONE);
        
        if (useTopicLayout) {
            // Star New Journey: Chỉ hiện nút "Lưu vào thư viện", ẩn "Bỏ qua" và "Tiếp tục"
            binding.btnSkip.setVisibility(View.GONE);
            binding.btnNext.setVisibility(View.GONE);
            binding.btnSaveToLibrary.setVisibility(View.VISIBLE);
            
            binding.cardFlashcard.setVisibility(View.GONE);
            binding.dynamicTaskLayout.setVisibility(View.VISIBLE);
            binding.dynamicTaskLayout.removeAllViews();
            
            topicBinding = LayoutFlashcardTopicBinding.inflate(getLayoutInflater(), binding.dynamicTaskLayout, true);
            topicBinding.cardFlashcard.setOnClickListener(v -> flipTopicCard());
            topicBinding.btnAudio.setOnClickListener(v -> playCurrentAudio());
            topicBinding.btnDelete.setVisibility(View.GONE);
        } else {
            // Chế độ Flashcard thông thường
            binding.btnSkip.setVisibility(View.VISIBLE);
            binding.btnNext.setVisibility(View.VISIBLE);
            binding.btnSaveToLibrary.setVisibility(View.VISIBLE);
            binding.cardFlashcard.setVisibility(View.VISIBLE);
            binding.dynamicTaskLayout.setVisibility(View.GONE);
        }
        
        binding.studyProgress.setProgress(100);
    }

    private void setupListeners() {
        binding.btnClose.setOnClickListener(v -> finish());
        binding.cardFlashcard.setOnClickListener(v -> flipCard());
        binding.btnSaveToLibrary.setOnClickListener(v -> saveToFirebaseLibrary());
        
        binding.btnSkip.setOnClickListener(v -> finish());
        binding.btnNext.setOnClickListener(v -> finish());
        binding.btnListen.setOnClickListener(v -> playCurrentAudio());
    }

    private void playCurrentAudio() {
        if (currentVocab != null) {
            String url = currentVocab.getAnyAudioUrl();
            if (url != null && !url.trim().isEmpty()) {
                playAudio(url);
            } else {
                Toast.makeText(this, "Không có âm thanh cho từ này", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadLessonChallenges() {
        if (lessonId == null) return;

        db.collection("challenges")
                .whereEqualTo("lessonId", lessonId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    challenges = queryDocumentSnapshots.toObjects(Challenge.class);
                    if (challenges != null && !challenges.isEmpty()) {
                        Collections.sort(challenges, (c1, c2) -> Integer.compare(c1.getOrderNum(), c2.getOrderNum()));
                        displayChallenge();
                    } else {
                        Toast.makeText(this, "Không tìm thấy thử thách cho bài học này", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading challenges", e);
                    Toast.makeText(this, "Lỗi khi tải dữ liệu", Toast.LENGTH_SHORT).show();
                });
    }

    private void displayChallenge() {
        if (currentChallengeIndex >= challenges.size()) {
            Toast.makeText(this, "Chúc mừng! Bạn đã hoàn thành bài học", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Challenge challenge = challenges.get(currentChallengeIndex);
        binding.cardFlashcard.setVisibility(View.GONE);
        binding.dynamicTaskLayout.setVisibility(View.VISIBLE);
        binding.dynamicTaskLayout.removeAllViews();

        int progress = (int) (((float) (currentChallengeIndex + 1) / challenges.size()) * 100);
        binding.studyProgress.setProgress(progress);

        View challengeView = getLayoutInflater().inflate(R.layout.layout_challenge_select, binding.dynamicTaskLayout, false);
        TextView textQuestion = challengeView.findViewById(R.id.text_question);
        LinearLayout optionsContainer = challengeView.findViewById(R.id.options_container);

        textQuestion.setText(challenge.getQuestion());
        
        List<Challenge.ChallengeOption> options = challenge.getOptions();
        if (options != null) {
            for (Challenge.ChallengeOption option : options) {
                Button btnOption = new Button(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 16, 0, 16);
                btnOption.setLayoutParams(params);
                btnOption.setText(option.getText());
                btnOption.setAllCaps(false);
                btnOption.setBackgroundResource(R.drawable.button_primary);
                
                btnOption.setOnClickListener(v -> {
                    if (option.isCorrect()) {
                        Toast.makeText(this, "Chính xác!", Toast.LENGTH_SHORT).show();
                        nextChallenge();
                    } else {
                        UiFeedback.performHaptic(this, 20);
                        Toast.makeText(this, "Sai rồi, thử lại nhé!", Toast.LENGTH_SHORT).show();
                    }
                });
                optionsContainer.addView(btnOption);
            }
        }

        binding.dynamicTaskLayout.addView(challengeView);
    }

    private void nextChallenge() {
        currentChallengeIndex++;
        displayChallenge();
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

    private void flipTopicCard() {
        if (topicBinding == null) return;
        if (topicBinding.cardFront.getVisibility() == View.VISIBLE) {
            topicBinding.cardFront.setVisibility(View.GONE);
            topicBinding.cardBack.setVisibility(View.VISIBLE);
        } else {
            topicBinding.cardFront.setVisibility(View.VISIBLE);
            topicBinding.cardBack.setVisibility(View.GONE);
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
        if (currentVocab == null) return;

        if (useTopicLayout && topicBinding != null) {
            topicBinding.textTerm.setText(currentVocab.getWord());
            topicBinding.textDefinition.setText(currentVocab.getDefinition());
            topicBinding.textPhonetic.setText(currentVocab.getPhonetic());
            topicBinding.textPhonetic.setVisibility(currentVocab.getPhonetic() != null ? View.VISIBLE : View.GONE);
            topicBinding.labelTopic.setText(currentVocab.getTopic() != null ? currentVocab.getTopic().toUpperCase() : "VOCAB");
            topicBinding.textExample.setText(currentVocab.getExampleSentence());
            topicBinding.textExample.setVisibility(currentVocab.getExampleSentence() != null ? View.VISIBLE : View.GONE);
            
            String audioUrl = currentVocab.getAnyAudioUrl();
            topicBinding.btnAudio.setVisibility(audioUrl != null && !audioUrl.isEmpty() ? View.VISIBLE : View.GONE);
        } else {
            binding.textTerm.setText(currentVocab.getWord());
            binding.textDefinition.setText(currentVocab.getDefinition());
            
            String phonetic = currentVocab.getPhonetic();
            if (phonetic != null && !phonetic.trim().isEmpty()) {
                binding.textPhonetic.setText(phonetic);
                binding.textPhonetic.setVisibility(View.VISIBLE);
            } else {
                binding.textPhonetic.setVisibility(View.GONE);
            }

            String audioUrl = currentVocab.getAnyAudioUrl();
            if (audioUrl != null && !audioUrl.trim().isEmpty()) {
                binding.btnListen.setVisibility(View.VISIBLE);
            } else {
                binding.btnListen.setVisibility(View.GONE);
            }

            if (currentVocab.getImageUrl() != null && !currentVocab.getImageUrl().trim().isEmpty()) {
                binding.imageVocab.setVisibility(View.VISIBLE);
            }
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
