package com.example.vocabmaster.ui.study;

import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.databinding.ActivityMiniGameBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.Locale;

public class MiniGameActivity extends AppCompatActivity {

    private static final String TAG = "MiniGameActivity";
    private ActivityMiniGameBinding binding;
    
    private TextToSpeech tts;
    private boolean isTtsReady = false;
    private MediaPlayer mediaPlayer;

    // AI Riddle Mode variables
    private boolean isAiRiddleMode = false;
    private int aiRiddleScore = 0;
    private Vocabulary currentRiddleWord;
    private CountDownTimer riddleTimer;
    private static final int RIDDLE_TIME_LIMIT = 30000; // 30 seconds
    private int currentPointsPossible = 5;
    
    private boolean hintEngShown = false;
    private boolean hintViShown = false;
    private boolean hint50Shown = false;
    private boolean hint75Shown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMiniGameBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initTTS();
        initMediaPlayer();
        setupClickListeners();
    }

    private void initTTS() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isTtsReady = true;
                }
            }
        });
    }

    private void initMediaPlayer() {
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        }
    }

    private void playSoundEffect(boolean isSuccess) {
        if (isSuccess) {
            UiFeedback.performHaptic(this, 30);
        } else {
            UiFeedback.performHaptic(this, 100);
        }
    }

    private void setupClickListeners() {
        binding.btnClose.setOnClickListener(v -> handleBack());
        binding.cardPlayAi.setOnClickListener(v -> showAiModes());
        binding.cardAiRiddle.setOnClickListener(v -> showAiRiddleLobby());
    }

    private void showAiModes() {
        binding.layoutModeSelection.setVisibility(View.GONE);
        binding.layoutAiModes.setVisibility(View.VISIBLE);
        binding.textTitle.setText("Chơi với máy");
    }

    // --- AI RIDDLE MODE ---
    private void showAiRiddleLobby() {
        isAiRiddleMode = true;
        binding.layoutAiModes.setVisibility(View.GONE);
        View lobby = getLayoutInflater().inflate(R.layout.layout_game_ai_riddle_lobby, binding.gameContainer, false);
        binding.gameContainer.removeAllViews();
        binding.gameContainer.setVisibility(View.VISIBLE);
        binding.gameContainer.addView(lobby);

        SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
        int highScore = prefs.getInt("ai_riddle_high_score", 0);
        String history = prefs.getString("ai_riddle_history", "Chưa có dữ liệu");

        ((TextView)lobby.findViewById(R.id.text_high_score)).setText("Kỉ lục: " + highScore + " điểm");
        ((TextView)lobby.findViewById(R.id.text_history)).setText(history);

        lobby.findViewById(R.id.btn_start_riddle).setOnClickListener(v -> startAiRiddleGame());
    }

    private void startAiRiddleGame() {
        aiRiddleScore = 0;
        updateScoreUI();
        fetchAndShowNextRiddle();
    }

    private void fetchAndShowNextRiddle() {
        new Thread(() -> {
            List<Vocabulary> randomVocabs = AppDatabase.getDatabase(this).vocabularyDao().getRandomVocabularies(1);
            if (!randomVocabs.isEmpty()) {
                currentRiddleWord = randomVocabs.get(0);
                runOnUiThread(this::displayRiddleQuestion);
            } else {
                runOnUiThread(() -> Toast.makeText(this, "Không tìm thấy dữ liệu từ vựng!", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void displayRiddleQuestion() {
        View view = getLayoutInflater().inflate(R.layout.layout_game_ai_riddle, binding.gameContainer, false);
        binding.gameContainer.removeAllViews();
        binding.gameContainer.addView(view);

        TextView textHint = view.findViewById(R.id.text_riddle_hint);
        TextView textEngHint = view.findViewById(R.id.text_english_hint);
        TextView textViHint = view.findViewById(R.id.text_vietnamese_hint);
        TextView textPlaceholders = view.findViewById(R.id.text_answer_placeholders);
        TextView textLetterCount = view.findViewById(R.id.text_letter_count);
        TextInputEditText editAnswer = view.findViewById(R.id.edit_answer);
        MaterialButton btnSubmit = view.findViewById(R.id.btn_submit_answer);
        TextView textTimer = view.findViewById(R.id.text_timer);
        ProgressBar progressTimer = view.findViewById(R.id.progress_timer);

        // Reset hint flags
        hintEngShown = false;
        hintViShown = false;
        hint50Shown = false;
        hint75Shown = false;
        currentPointsPossible = 5;

        // Base question: Use example sentence if available, else definition
        String question = currentRiddleWord.getExample_sentence();
        if (question == null || question.isEmpty()) question = currentRiddleWord.getDefinition();
        
        String word = currentRiddleWord.getWord().toLowerCase();
        question = question.replaceAll("(?i)" + word, "_______");
        textHint.setText(question);

        if (textEngHint != null) textEngHint.setVisibility(View.GONE);
        if (textViHint != null) textViHint.setVisibility(View.GONE);

        // Initial placeholders (0%)
        textPlaceholders.setText(getMaskedWord(word, 0));
        textLetterCount.setText("(" + word.length() + " ký tự)");

        btnSubmit.setOnClickListener(v -> {
            String ans = editAnswer.getText().toString().trim();
            if (ans.equalsIgnoreCase(word)) {
                if (riddleTimer != null) riddleTimer.cancel();
                aiRiddleScore += currentPointsPossible;
                updateScoreUI();
                playSoundEffect(true);
                UiFeedback.showSnack(binding.getRoot(), "Chính xác! +" + currentPointsPossible + " điểm");
                new Handler(Looper.getMainLooper()).postDelayed(this::fetchAndShowNextRiddle, 1000);
            } else {
                playSoundEffect(false);
                Toast.makeText(this, "Chưa đúng rồi, thử lại nhé!", Toast.LENGTH_SHORT).show();
            }
        });

        startRiddleTimer(textTimer, progressTimer, textEngHint, textViHint, textPlaceholders);
    }

    private String getMaskedWord(String word, int percent) {
        StringBuilder sb = new StringBuilder();
        int visibleCount = (int) Math.ceil(word.length() * (percent / 100.0));
        
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (!Character.isLetter(c)) {
                sb.append(c).append(" ");
            } else if (i < visibleCount) {
                sb.append(c).append(" ");
            } else {
                sb.append("_ ");
            }
        }
        return sb.toString().trim();
    }

    private void startRiddleTimer(TextView textTimer, ProgressBar progressTimer, TextView textEngHint, TextView textViHint, TextView textPlaceholders) {
        if (riddleTimer != null) riddleTimer.cancel();
        
        progressTimer.setMax(RIDDLE_TIME_LIMIT / 100);
        
        riddleTimer = new CountDownTimer(RIDDLE_TIME_LIMIT, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                textTimer.setText(String.valueOf(seconds));
                progressTimer.setProgress((int) (millisUntilFinished / 100));

                long elapsed = RIDDLE_TIME_LIMIT - millisUntilFinished;

                // 5s: English hint, +4 points
                if (elapsed >= 5000 && !hintEngShown) {
                    hintEngShown = true;
                    currentPointsPossible = 4;
                    if (textEngHint != null) {
                        textEngHint.setVisibility(View.VISIBLE);
                        textEngHint.setText("Hint (English): " + currentRiddleWord.getDefinition());
                    }
                    UiFeedback.showSnack(binding.getRoot(), "Gợi ý 1: Nghĩa tiếng Anh (+4 điểm)");
                }

                // 10s (5s more): Vietnamese hint, +3 points
                if (elapsed >= 10000 && !hintViShown) {
                    hintViShown = true;
                    currentPointsPossible = 3;
                    if (textViHint != null) {
                        textViHint.setVisibility(View.VISIBLE);
                        String vi = currentRiddleWord.getVietnamese_translation();
                        if (vi == null || vi.isEmpty()) vi = currentRiddleWord.getVietnameseTranslation();
                        textViHint.setText("Gợi ý (Tiếng Việt): " + vi);
                    }
                    UiFeedback.showSnack(binding.getRoot(), "Gợi ý 2: Nghĩa tiếng Việt (+3 điểm)");
                }

                // 15s (5s more): 50% characters hint, +2 points
                if (elapsed >= 15000 && !hint50Shown) {
                    hint50Shown = true;
                    currentPointsPossible = 2;
                    textPlaceholders.setText(getMaskedWord(currentRiddleWord.getWord().toLowerCase(), 50));
                    UiFeedback.showSnack(binding.getRoot(), "Gợi ý 3: 50% ký tự (+2 điểm)");
                }

                // 25s (10s more): 75% characters hint, +1 point
                if (elapsed >= 25000 && !hint75Shown) {
                    hint75Shown = true;
                    currentPointsPossible = 1;
                    textPlaceholders.setText(getMaskedWord(currentRiddleWord.getWord().toLowerCase(), 75));
                    UiFeedback.showSnack(binding.getRoot(), "Gợi ý 4: 75% ký tự (+1 điểm)");
                }
            }

            @Override
            public void onFinish() {
                textTimer.setText("0");
                progressTimer.setProgress(0);
                finishAiRiddleGame();
            }
        }.start();
    }

    private void finishAiRiddleGame() {
        if (riddleTimer != null) riddleTimer.cancel();
        
        SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
        int oldHighScore = prefs.getInt("ai_riddle_high_score", 0);
        boolean isNewRecord = aiRiddleScore > oldHighScore;
        
        if (isNewRecord) {
            prefs.edit().putInt("ai_riddle_high_score", aiRiddleScore).apply();
        }

        String history = prefs.getString("ai_riddle_history", "");
        String newEntry = "Điểm: " + aiRiddleScore + " - " + new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date());
        if (history.isEmpty() || history.equals("Chưa có dữ liệu")) history = newEntry;
        else {
            String[] lines = history.split("\n");
            StringBuilder sb = new StringBuilder(newEntry);
            for (int i = 0; i < Math.min(lines.length, 4); i++) {
                sb.append("\n").append(lines[i]);
            }
            history = sb.toString();
        }
        prefs.edit().putString("ai_riddle_history", history).apply();

        String msg = "Hết giờ! Bạn đạt được " + aiRiddleScore + " điểm.";
        if (isNewRecord && aiRiddleScore > 0) msg += "\n\nCHÚC MỪNG! BẠN ĐÃ PHÁ KỈ LỤC!";

        new AlertDialog.Builder(this)
                .setTitle("Game Over")
                .setMessage(msg)
                .setPositiveButton("Về sảnh", (dialog, which) -> showAiRiddleLobby())
                .setCancelable(false)
                .show();
    }

    private void updateScoreUI() {
        if (isAiRiddleMode) {
            binding.textScore.setText("Điểm: " + aiRiddleScore);
        } else {
            binding.textScore.setText("Minigames");
        }
    }

    private void handleBack() {
        if (riddleTimer != null) riddleTimer.cancel();
        
        if (binding.gameContainer.getVisibility() == View.VISIBLE) {
            showAiRiddleLobby();
        } else if (binding.layoutAiModes.getVisibility() == View.VISIBLE) {
            binding.layoutAiModes.setVisibility(View.GONE);
            binding.layoutModeSelection.setVisibility(View.VISIBLE);
            binding.textTitle.setText("Minigames");
        } else finish();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (riddleTimer != null) riddleTimer.cancel();
        super.onDestroy();
    }
}
