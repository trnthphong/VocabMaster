package com.example.vocabmaster.ui.study;

import android.content.res.ColorStateList;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.databinding.ActivityMiniGameBinding;
import com.example.vocabmaster.ui.common.MotionSystem;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MiniGameActivity extends AppCompatActivity {
    private static final String TAG = "MiniGameActivity";
    private ActivityMiniGameBinding binding;
    private FirebaseFirestore db;
    private MediaPlayer mediaPlayer;
    
    private List<Vocabulary> reviewVocabs = new ArrayList<>();
    private List<Question> questionList = new ArrayList<>();
    private List<Vocabulary> allVocabPool = new ArrayList<>();
    
    private int currentIdx = 0;
    private int score = 0;
    private final Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMiniGameBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        mediaPlayer = new MediaPlayer();

        String courseId = getIntent().getStringExtra("course_id");
        String theme = getIntent().getStringExtra("course_theme");

        setupUI();
        loadData(courseId, theme);
    }

    private void setupUI() {
        binding.btnClose.setOnClickListener(v -> finish());
        binding.btnCheckAnswer.setOnClickListener(v -> checkAnswer());
        MotionSystem.applyPressState(binding.btnCheckAnswer);
        
        MotionSystem.applyPressState(binding.btnOpt1);
        MotionSystem.applyPressState(binding.btnOpt2);
        MotionSystem.applyPressState(binding.btnOpt3);
        MotionSystem.applyPressState(binding.btnMOpt1);
        MotionSystem.applyPressState(binding.btnMOpt2);
        MotionSystem.applyPressState(binding.btnMOpt3);
    }

    private void loadData(String courseId, String theme) {
        binding.gameProgress.setIndeterminate(true);
        Task<QuerySnapshot> poolTask = db.collection("vocabularies").limit(100).get();
        Task<QuerySnapshot> reviewTask;
        if (theme != null) {
            reviewTask = db.collection("vocabularies").whereEqualTo("topic", theme.toLowerCase()).limit(5).get();
        } else {
            reviewTask = db.collection("vocabularies").limit(5).get();
        }

        Tasks.whenAllComplete(poolTask, reviewTask).addOnSuccessListener(tasks -> {
            binding.gameProgress.setIndeterminate(false);
            if (poolTask.isSuccessful()) {
                for (DocumentSnapshot doc : poolTask.getResult()) {
                    Vocabulary v = doc.toObject(Vocabulary.class);
                    if (v != null) allVocabPool.add(v);
                }
            }
            if (reviewTask.isSuccessful()) {
                for (DocumentSnapshot doc : reviewTask.getResult()) {
                    Vocabulary v = doc.toObject(Vocabulary.class);
                    if (v != null) reviewVocabs.add(v);
                }
            }
            if (reviewVocabs.isEmpty()) {
                Toast.makeText(this, "Chủ đề này chưa có từ vựng để ôn tập!", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            generateQuestions();
            displayQuestion();
        });
    }

    private void generateQuestions() {
        questionList.clear();
        for (Vocabulary v : reviewVocabs) {
            questionList.add(new Question(v, 0));
            questionList.add(new Question(v, 1));
            questionList.add(new Question(v, 2));
            questionList.add(new Question(v, 3));
        }
        Collections.shuffle(questionList);
    }

    private void displayQuestion() {
        if (currentIdx >= questionList.size()) {
            showFinalResult();
            return;
        }

        Question q = questionList.get(currentIdx);
        binding.gameProgress.setProgress(((currentIdx + 1) * 100) / questionList.size());
        
        hideAllLayouts();
        
        switch (q.type) {
            case 0: setupListenMCQ(q.vocab); break;
            case 1: setupListenWrite(q.vocab); break;
            case 2: setupMeaningMCQ(q.vocab); break;
            case 3: setupMeaningWrite(q.vocab); break;
        }
    }

    private void hideAllLayouts() {
        binding.layoutListenMcq.setVisibility(View.GONE);
        binding.layoutListenWrite.setVisibility(View.GONE);
        binding.layoutMeaningMcq.setVisibility(View.GONE);
        binding.layoutMeaningWrite.setVisibility(View.GONE);
        binding.btnCheckAnswer.setEnabled(true);
        binding.btnCheckAnswer.setText("CHECK");
        binding.editAnswerWrite.setText("");
        binding.editWordWrite.setText("");
        resetButtonStyles(binding.btnOpt1, binding.btnOpt2, binding.btnOpt3, binding.btnMOpt1, binding.btnMOpt2, binding.btnMOpt3);
    }

    private void setupListenMCQ(Vocabulary v) {
        binding.layoutListenMcq.setVisibility(View.VISIBLE);
        binding.btnPlayAudioMcq.setOnClickListener(view -> playAudio(v.getAnyAudioUrl()));
        playAudio(v.getAnyAudioUrl());
        List<String> options = generateOptions(v.getWord(), false);
        setupMCQButtons(options, binding.btnOpt1, binding.btnOpt2, binding.btnOpt3);
    }

    private void setupListenWrite(Vocabulary v) {
        binding.layoutListenWrite.setVisibility(View.VISIBLE);
        binding.btnPlayAudioWrite.setOnClickListener(view -> playAudio(v.getAnyAudioUrl()));
        playAudio(v.getAnyAudioUrl());
    }

    private void setupMeaningMCQ(Vocabulary v) {
        binding.layoutMeaningMcq.setVisibility(View.VISIBLE);
        binding.textTargetWord.setText(v.getWord());
        List<String> options = generateOptions(v.getDefinition(), true);
        setupMCQButtons(options, binding.btnMOpt1, binding.btnMOpt2, binding.btnMOpt3);
    }

    private void setupMeaningWrite(Vocabulary v) {
        binding.layoutMeaningWrite.setVisibility(View.VISIBLE);
        binding.textTargetDefinition.setText(v.getDefinition());
    }

    private void setupMCQButtons(List<String> options, Button... buttons) {
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setText(options.get(i));
            buttons[i].setOnClickListener(v -> {
                resetButtonStyles(buttons);
                v.setSelected(true);
            });
        }
    }

    private void checkAnswer() {
        Question q = questionList.get(currentIdx);
        boolean isCorrect = false;
        String userAns = "";
        Button[] currentButtons = null;
        String correctStr = "";

        switch (q.type) {
            case 0:
                currentButtons = new Button[]{binding.btnOpt1, binding.btnOpt2, binding.btnOpt3};
                userAns = getSelectedText(currentButtons);
                correctStr = q.vocab.getWord();
                isCorrect = userAns.equalsIgnoreCase(correctStr);
                break;
            case 1:
                userAns = binding.editAnswerWrite.getText().toString().trim();
                correctStr = q.vocab.getWord();
                isCorrect = userAns.equalsIgnoreCase(correctStr);
                break;
            case 2:
                currentButtons = new Button[]{binding.btnMOpt1, binding.btnMOpt2, binding.btnMOpt3};
                userAns = getSelectedText(currentButtons);
                correctStr = q.vocab.getDefinition();
                isCorrect = userAns.equalsIgnoreCase(correctStr);
                break;
            case 3:
                userAns = binding.editWordWrite.getText().toString().trim();
                correctStr = q.vocab.getWord();
                isCorrect = userAns.equalsIgnoreCase(correctStr);
                break;
        }

        showFeedback(isCorrect, currentButtons, correctStr, userAns);

        if (isCorrect) {
            score += 10;
            updateXPInFirestore(10);
            binding.textMiniScore.setText("Score: " + score);
            UiFeedback.performHaptic(this, 10);
        } else {
            UiFeedback.performHaptic(this, 50);
        }

        binding.btnCheckAnswer.setEnabled(false);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            currentIdx++;
            displayQuestion();
        }, 1500);
    }

    private void showFeedback(boolean isCorrect, Button[] buttons, String correctStr, String userAns) {
        if (buttons != null) {
            for (Button b : buttons) {
                String txt = b.getText().toString();
                if (txt.equalsIgnoreCase(correctStr)) {
                    b.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.success)));
                    b.setTextColor(ContextCompat.getColor(this, R.color.white));
                } else if (txt.equalsIgnoreCase(userAns) && !isCorrect) {
                    b.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.error)));
                    b.setTextColor(ContextCompat.getColor(this, R.color.white));
                }
            }
        }
        
        if (isCorrect) {
            Toast.makeText(this, "Chính xác! +10 XP", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Sai rồi! Đáp án đúng: " + correctStr, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateXPInFirestore(int amount) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        
        db.collection("users").document(uid)
                .update("xp", FieldValue.increment(amount))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update XP", e));
    }

    private List<String> generateOptions(String correct, boolean isDef) {
        List<String> opts = new ArrayList<>();
        opts.add(correct);
        List<Vocabulary> pool = new ArrayList<>(allVocabPool);
        Collections.shuffle(pool);
        for (Vocabulary v : pool) {
            String w = isDef ? v.getDefinition() : v.getWord();
            if (w != null && !w.equalsIgnoreCase(correct) && !opts.contains(w)) opts.add(w);
            if (opts.size() == 3) break;
        }
        Collections.shuffle(opts);
        return opts;
    }

    private String getSelectedText(Button... buttons) {
        for (Button b : buttons) if (b.isSelected()) return b.getText().toString();
        return "";
    }

    private void resetButtonStyles(Button... buttons) {
        for (Button b : buttons) {
            b.setSelected(false);
            b.setBackgroundTintList(null); // Reset tint
            b.setTextColor(ContextCompat.getColorStateList(this, R.color.selector_mcq_text));
        }
    }

    private void playAudio(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
        } catch (IOException e) { Log.e(TAG, "Audio error", e); }
    }

    private void showFinalResult() {
        Toast.makeText(this, "Hoàn thành! Tổng điểm: " + score, Toast.LENGTH_LONG).show();
        finish();
    }

    private static class Question {
        Vocabulary vocab; int type;
        Question(Vocabulary v, int t) { this.vocab = v; this.type = t; }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) mediaPlayer.release();
    }
}
