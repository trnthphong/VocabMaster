package com.example.vocabmaster.ui.study;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.databinding.ActivityMiniGameBinding;
import com.example.vocabmaster.data.model.MiniGameQuestion;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.ArrayList;

public class MiniGameActivity extends AppCompatActivity {

    private ActivityMiniGameBinding binding;
    private final Random random = new Random();
    private int score = 0;
    private CountDownTimer timer;
    private final List<MiniGameQuestion> remoteQuestions = new ArrayList<>();

    private final List<String> words = Arrays.asList("abundant", "brief", "accurate");
    private final List<String> defs = Arrays.asList(
            "existing in large quantities",
            "lasting a short time",
            "correct in all details"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMiniGameBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupTabs();
        loadQuestionPool();
        setupMatchGame();
        setupMcqGame();
        setupSpeedGame();
    }

    private void loadQuestionPool() {
        FirebaseFirestore.getInstance().collection("mini_game_questions").get().addOnSuccessListener(qs -> {
            remoteQuestions.clear();
            for (com.google.firebase.firestore.QueryDocumentSnapshot doc : qs) {
                MiniGameQuestion q = doc.toObject(MiniGameQuestion.class);
                if (q != null) remoteQuestions.add(q);
            }
            if (!remoteQuestions.isEmpty()) {
                applyRemoteQuestions();
            }
        });
    }

    private void applyRemoteQuestions() {
        for (MiniGameQuestion q : remoteQuestions) {
            if ("mcq".equalsIgnoreCase(q.getMode()) && q.getOptions() != null && q.getOptions().size() >= 3) {
                binding.textMcqQuestion.setText(q.getPrompt());
                binding.btnMcqA.setText(q.getOptions().get(0));
                binding.btnMcqB.setText(q.getOptions().get(1));
                binding.btnMcqC.setText(q.getOptions().get(2));
                break;
            }
        }
    }

    private void setupTabs() {
        binding.btnTabMatch.setOnClickListener(v -> switchMode(0));
        binding.btnTabMcq.setOnClickListener(v -> switchMode(1));
        binding.btnTabSpeed.setOnClickListener(v -> switchMode(2));
        binding.btnClose.setOnClickListener(v -> finish());
        switchMode(0);
    }

    private void switchMode(int mode) {
        binding.layoutMatch.setVisibility(mode == 0 ? View.VISIBLE : View.GONE);
        binding.layoutMcq.setVisibility(mode == 1 ? View.VISIBLE : View.GONE);
        binding.layoutSpeed.setVisibility(mode == 2 ? View.VISIBLE : View.GONE);
    }

    private void setupMatchGame() {
        binding.matchWord1.setText("abundant");
        binding.matchWord2.setText("brief");
        binding.matchDef1.setText("lasting a short time");
        binding.matchDef2.setText("existing in large quantities");

        binding.btnCheckMatch.setOnClickListener(v -> {
            boolean correct = binding.chk11.isChecked() && binding.chk22.isChecked();
            if (correct) {
                score += 10;
                UiFeedback.performHaptic(this, 30);
                UiFeedback.showSnack(binding.getRoot(), "Correct match! +10 XP");
            } else {
                UiFeedback.showErrorDialog(this, "Try again", "Your matching pairs are not correct yet.");
            }
            binding.textMiniScore.setText("Score: " + score);
        });
    }

    private void setupMcqGame() {
        binding.textMcqQuestion.setText("What does \"accurate\" mean?");
        binding.btnMcqA.setText("Correct in all details");
        binding.btnMcqB.setText("Very noisy");
        binding.btnMcqC.setText("Impossible to find");

        View.OnClickListener listener = v -> {
            String selected = v == binding.btnMcqA ? binding.btnMcqA.getText().toString()
                    : v == binding.btnMcqB ? binding.btnMcqB.getText().toString()
                    : binding.btnMcqC.getText().toString();
            String answer = "Correct in all details";
            for (MiniGameQuestion q : remoteQuestions) {
                if ("mcq".equalsIgnoreCase(q.getMode())) {
                    answer = q.getAnswer() == null ? answer : q.getAnswer();
                    break;
                }
            }
            boolean correct = selected.equalsIgnoreCase(answer);
            if (correct) {
                score += 10;
                UiFeedback.performHaptic(this, 30);
                UiFeedback.showSnack(binding.getRoot(), "Great! +10 XP");
            } else {
                UiFeedback.performHaptic(this, 100);
                UiFeedback.showSnack(binding.getRoot(), "Not quite. Correct answer: " + answer);
            }
            binding.textMiniScore.setText("Score: " + score);
        };
        binding.btnMcqA.setOnClickListener(listener);
        binding.btnMcqB.setOnClickListener(listener);
        binding.btnMcqC.setOnClickListener(listener);
    }

    private void setupSpeedGame() {
        binding.btnStartSpeed.setOnClickListener(v -> startSpeedRound());
        binding.btnSpeedSubmit.setOnClickListener(v -> {
            String answer = binding.editSpeedAnswer.getText() == null ? "" : binding.editSpeedAnswer.getText().toString().trim().toLowerCase();
            if ("brief".equals(answer)) {
                score += 15;
                UiFeedback.performHaptic(this, 30);
                UiFeedback.showSnack(binding.getRoot(), "Speed bonus! +15 XP");
            } else {
                UiFeedback.showSnack(binding.getRoot(), "Answer recorded. Keep going!");
            }
            binding.textMiniScore.setText("Score: " + score);
        });
    }

    private void startSpeedRound() {
        String prompt = defs.get(random.nextInt(words.size()));
        for (MiniGameQuestion q : remoteQuestions) {
            if ("speed".equalsIgnoreCase(q.getMode()) && q.getPrompt() != null) {
                prompt = q.getPrompt();
                break;
            }
        }
        binding.textSpeedPrompt.setText("Type the word for: " + prompt);
        binding.editSpeedAnswer.setText("");
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(10000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                binding.textSpeedTimer.setText("Time left: " + (millisUntilFinished / 1000) + "s");
            }

            @Override
            public void onFinish() {
                binding.textSpeedTimer.setText("Time left: 0s");
                UiFeedback.showSnack(binding.getRoot(), "Round ended");
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        if (timer != null) timer.cancel();
        super.onDestroy();
    }
}
