package com.example.vocabmaster.ui.study;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.databinding.ActivityMiniGameBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MiniGameActivity extends AppCompatActivity {

    private static final String TAG = "MiniGameActivity";
    private ActivityMiniGameBinding binding;
    private List<Flashcard> myFlashcards = new ArrayList<>();
    private List<Flashcard> gamePool = new ArrayList<>();
    private List<ChallengeTask> challengeTasks = new ArrayList<>();
    private Set<String> masteredWordIds = new HashSet<>();
    private Map<String, Integer> wordErrorCount = new HashMap<>();
    
    private int currentTaskIndex = 0;
    private int score = 0;
    private TextToSpeech tts;
    private boolean isTtsReady = false;
    private MediaPlayer mediaPlayer;

    // AI Riddle Mode variables
    private boolean isAiRiddleMode = false;
    private int aiRiddleScore = 0;
    private Vocabulary currentRiddleWord;
    private CountDownTimer riddleTimer;
    private static final int RIDDLE_TIME_LIMIT = 15000; // 15 seconds

    // For Match Game (Click logic)
    private MaterialCardView selectedTermCard = null;
    private String selectedTermId = null;
    private MaterialCardView selectedDefCard = null;
    private String selectedDefId = null;

    private enum ChallengeType { LISTEN, MCQ_MEANING, TYPING, MATCH }

    private static class ChallengeTask {
        ChallengeType type;
        Flashcard word;
        List<Flashcard> matchWords;

        ChallengeTask(ChallengeType type, Flashcard word) {
            this.type = type;
            this.word = word;
        }

        ChallengeTask(ChallengeType type, List<Flashcard> matchWords) {
            this.type = type;
            this.matchWords = matchWords;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMiniGameBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initTTS();
        initMediaPlayer();
        initData();
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
                    Log.d(TAG, "TTS Initialized Successfully");
                } else {
                    Log.e(TAG, "TTS Language not supported or missing data");
                }
            } else {
                Log.e(TAG, "TTS Initialization failed with status: " + status);
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

    private void initData() {
        AppDatabase.getDatabase(this).flashcardDao().getPersonalFlashcards().observe(this, flashcards -> {
            if (flashcards != null) {
                this.myFlashcards = flashcards;
                masteredWordIds.clear();
                for (Flashcard f : flashcards) {
                    if (f.isMastered()) masteredWordIds.add(f.getFirestoreId());
                }
            }
        });
    }

    private void setupClickListeners() {
        binding.btnClose.setOnClickListener(v -> handleBack());
        binding.cardPlayAi.setOnClickListener(v -> showAiModes());
        binding.cardVocabularyChallenge.setOnClickListener(v -> showDashboard());
        binding.cardAiRiddle.setOnClickListener(v -> showAiRiddleLobby());
    }

    private void showAiModes() {
        binding.layoutModeSelection.setVisibility(View.GONE);
        binding.layoutAiModes.setVisibility(View.VISIBLE);
        binding.textTitle.setText("Chơi với máy");
    }

    private void showDashboard() {
        View dashboard = getLayoutInflater().inflate(R.layout.layout_game_dashboard, binding.gameContainer, false);
        binding.gameContainer.removeAllViews();
        binding.gameContainer.setVisibility(View.VISIBLE);
        binding.gameContainer.addView(dashboard);
        binding.layoutAiModes.setVisibility(View.GONE);

        TextView textCount = dashboard.findViewById(R.id.text_mastered_count);
        ProgressBar progress = dashboard.findViewById(R.id.progress_mastery);
        
        int total = myFlashcards.size();
        int mastered = masteredWordIds.size();
        textCount.setText(mastered + "/" + total);
        if (total > 0) progress.setProgress((mastered * 100) / total);

        dashboard.findViewById(R.id.btn_start_game).setOnClickListener(v -> startVocabChallenge());
    }

    private void startVocabChallenge() {
        isAiRiddleMode = false;
        List<Flashcard> unmasteredPool = new ArrayList<>();
        for (Flashcard f : myFlashcards) {
            if (!f.isMastered()) unmasteredPool.add(f);
        }

        if (unmasteredPool.size() < 4) {
            UiFeedback.showErrorDialog(this, "Thông báo", "Bạn đã hoàn thành hết từ vựng rồi!");
            return;
        }

        Collections.shuffle(unmasteredPool);
        gamePool = new ArrayList<>(unmasteredPool.size() > 10 ? unmasteredPool.subList(0, 10) : unmasteredPool);
        
        wordErrorCount.clear();
        challengeTasks.clear();
        for (Flashcard f : gamePool) {
            challengeTasks.add(new ChallengeTask(ChallengeType.LISTEN, f));
            challengeTasks.add(new ChallengeTask(ChallengeType.MCQ_MEANING, f));
            challengeTasks.add(new ChallengeTask(ChallengeType.TYPING, f));
        }

        for (int i = 0; i < gamePool.size(); i += 4) {
            int end = Math.min(i + 4, gamePool.size());
            List<Flashcard> group = new ArrayList<>(gamePool.subList(i, end));
            if (group.size() >= 2) challengeTasks.add(new ChallengeTask(ChallengeType.MATCH, group));
        }

        Collections.shuffle(challengeTasks);
        currentTaskIndex = 0;
        score = 0;
        updateScoreUI();
        showNextTask();
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
        TextView textPlaceholders = view.findViewById(R.id.text_answer_placeholders);
        TextView textLetterCount = view.findViewById(R.id.text_letter_count);
        TextInputEditText editAnswer = view.findViewById(R.id.edit_answer);
        MaterialButton btnSubmit = view.findViewById(R.id.btn_submit_answer);
        TextView textTimer = view.findViewById(R.id.text_timer);
        ProgressBar progressTimer = view.findViewById(R.id.progress_timer);

        // Hint: Use example sentence or definition
        String hint = currentRiddleWord.getExample_sentence();
        if (hint == null || hint.isEmpty()) hint = currentRiddleWord.getDefinition();
        
        // Hide the word in the hint if it exists
        String word = currentRiddleWord.getWord().toLowerCase();
        hint = hint.replaceAll("(?i)" + word, "_______");
        textHint.setText(hint);

        // Placeholders
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            if (Character.isLetter(word.charAt(i))) sb.append("_ ");
            else sb.append(word.charAt(i)).append(" ");
        }
        textPlaceholders.setText(sb.toString().trim());
        textLetterCount.setText("(" + word.length() + " letters)");

        btnSubmit.setOnClickListener(v -> {
            String ans = editAnswer.getText().toString().trim();
            if (ans.equalsIgnoreCase(word)) {
                if (riddleTimer != null) riddleTimer.cancel();
                aiRiddleScore++;
                updateScoreUI();
                playSoundEffect(true);
                UiFeedback.showSnack(binding.getRoot(), "Chính xác! +1 điểm");
                new Handler(Looper.getMainLooper()).postDelayed(this::fetchAndShowNextRiddle, 1000);
            } else {
                playSoundEffect(false);
                Toast.makeText(this, "Chưa đúng rồi, thử lại nhé!", Toast.LENGTH_SHORT).show();
            }
        });

        startRiddleTimer(textTimer, progressTimer);
    }

    private void startRiddleTimer(TextView textTimer, ProgressBar progressTimer) {
        if (riddleTimer != null) riddleTimer.cancel();
        
        riddleTimer = new CountDownTimer(RIDDLE_TIME_LIMIT, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                textTimer.setText(String.valueOf(seconds));
                progressTimer.setProgress((int) (millisUntilFinished / 100));
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

        // Update history (save last 5 games)
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

    // --- END AI RIDDLE MODE ---

    private void showNextTask() {
        if (currentTaskIndex >= challengeTasks.size()) {
            finishVocabChallenge();
            return;
        }

        ChallengeTask task = challengeTasks.get(currentTaskIndex);
        if (task.type == ChallengeType.MATCH) {
            showMatchGame(task.matchWords);
        } else if (task.type == ChallengeType.TYPING) {
            showTypeGame(task.word);
        } else {
            showMcqGame(task.word, task.type == ChallengeType.LISTEN);
        }
    }

    private void showMcqGame(Flashcard word, boolean isListen) {
        View view = getLayoutInflater().inflate(R.layout.layout_game_vocab_mcq, binding.gameContainer, false);
        binding.gameContainer.removeAllViews();
        binding.gameContainer.addView(view);

        TextView typeText = view.findViewById(R.id.text_question_type);
        TextView contentText = view.findViewById(R.id.text_question_content);
        View btnPlay = view.findViewById(R.id.btn_play_audio);

        if (isListen) {
            typeText.setText("NGHE PHÁT ÂM");
            contentText.setText("???");
            btnPlay.setVisibility(View.VISIBLE);
            btnPlay.setOnClickListener(v -> playVocabSound(word));
            new Handler(Looper.getMainLooper()).postDelayed(() -> playVocabSound(word), 1000);
        } else {
            typeText.setText("CHỌN NGHĨA ĐÚNG");
            contentText.setText(word.getTerm());
            btnPlay.setVisibility(View.GONE);
        }

        String correct = isListen ? word.getTerm() : word.getDefinition();
        List<String> opts = new ArrayList<>();
        opts.add(correct);
        List<Flashcard> others = new ArrayList<>(myFlashcards);
        others.remove(word);
        Collections.shuffle(others);
        for (int i = 0; i < 3 && i < others.size(); i++) {
            opts.add(isListen ? others.get(i).getTerm() : others.get(i).getDefinition());
        }
        Collections.shuffle(opts);

        int[] ids = {R.id.btn_option_1, R.id.btn_option_2, R.id.btn_option_3, R.id.btn_option_4};
        for (int i = 0; i < 4; i++) {
            MaterialButton b = view.findViewById(ids[i]);
            if (i < opts.size()) {
                String val = opts.get(i);
                b.setText(val);
                b.setOnClickListener(v -> checkResult(word, val, correct, b));
            } else b.setVisibility(View.GONE);
        }
    }

    private void showTypeGame(Flashcard word) {
        View view = getLayoutInflater().inflate(R.layout.layout_game_vocab_type, binding.gameContainer, false);
        binding.gameContainer.removeAllViews();
        binding.gameContainer.addView(view);
        
        TextView meaningTv = view.findViewById(R.id.text_meaning);
        com.google.android.material.textfield.TextInputEditText editAns = view.findViewById(R.id.edit_answer);
        View btnSubmit = view.findViewById(R.id.btn_submit);
        
        meaningTv.setText(word.getDefinition());
        btnSubmit.setOnClickListener(v -> {
            String ans = editAns.getText().toString().trim();
            checkResult(word, ans, word.getTerm(), editAns);
        });
    }

    private void showMatchGame(List<Flashcard> words) {
        View view = getLayoutInflater().inflate(R.layout.layout_game_vocab_match, binding.gameContainer, false);
        binding.gameContainer.removeAllViews();
        binding.gameContainer.addView(view);

        ((TextView)view.findViewById(R.id.text_match_title)).setText("CHỌN CẶP TỪ VÀ NGHĨA");

        LinearLayout colTerms = view.findViewById(R.id.column_terms);
        LinearLayout colDefinitions = view.findViewById(R.id.column_definitions);

        List<Flashcard> termsList = new ArrayList<>(words);
        List<Flashcard> defsList = new ArrayList<>(words);
        Collections.shuffle(termsList);
        Collections.shuffle(defsList);

        selectedTermCard = null;
        selectedTermId = null;
        selectedDefCard = null;
        selectedDefId = null;

        for (Flashcard f : termsList) {
            colTerms.addView(createMatchClickCard(f.getTerm(), f.getFirestoreId(), true));
        }

        for (Flashcard f : defsList) {
            colDefinitions.addView(createMatchClickCard(f.getDefinition(), f.getFirestoreId(), false));
        }
    }

    private MaterialCardView createMatchClickCard(String text, String id, boolean isTerm) {
        MaterialCardView card = (MaterialCardView) getLayoutInflater().inflate(R.layout.item_match_card, null);
        TextView tv = card.findViewById(R.id.text_card);
        tv.setText(text);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 12, 0, 12);
        card.setLayoutParams(lp);

        card.setOnClickListener(v -> {
            if (!card.isEnabled()) return;

            if (isTerm) {
                if (selectedTermCard != null) {
                    selectedTermCard.setStrokeColor(ContextCompat.getColor(this, R.color.card_border));
                    selectedTermCard.setStrokeWidth(2);
                }
                selectedTermCard = card;
                selectedTermId = id;
                card.setStrokeColor(ContextCompat.getColor(this, R.color.brand_primary));
                card.setStrokeWidth(6);
            } else {
                if (selectedDefCard != null) {
                    selectedDefCard.setStrokeColor(ContextCompat.getColor(this, R.color.card_border));
                    selectedDefCard.setStrokeWidth(2);
                }
                selectedDefCard = card;
                selectedDefId = id;
                card.setStrokeColor(ContextCompat.getColor(this, R.color.brand_primary));
                card.setStrokeWidth(6);
            }

            if (selectedTermCard != null && selectedDefCard != null) {
                if (selectedTermId.equals(selectedDefId)) {
                    handleMatchResult(true);
                } else {
                    handleMatchResult(false);
                }
            }
        });

        return card;
    }

    private void handleMatchResult(boolean isCorrect) {
        final MaterialCardView termCard = selectedTermCard;
        final MaterialCardView defCard = selectedDefCard;

        if (isCorrect) {
            playSoundEffect(true);
            termCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.success));
            termCard.setStrokeColor(ContextCompat.getColor(this, R.color.success));
            ((TextView)termCard.findViewById(R.id.text_card)).setTextColor(ContextCompat.getColor(this, R.color.white));
            termCard.setEnabled(false);

            defCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.success));
            defCard.setStrokeColor(ContextCompat.getColor(this, R.color.success));
            ((TextView)defCard.findViewById(R.id.text_card)).setTextColor(ContextCompat.getColor(this, R.color.white));
            defCard.setEnabled(false);

            UiFeedback.showSnack(binding.getRoot(), "Chính xác!");
            checkMatchComplete((ViewGroup)termCard.getParent());
        } else {
            playSoundEffect(false);
            termCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.error));
            defCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.error));

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                termCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.white));
                termCard.setStrokeColor(ContextCompat.getColor(this, R.color.card_border));
                termCard.setStrokeWidth(2);
                
                defCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.white));
                defCard.setStrokeColor(ContextCompat.getColor(this, R.color.card_border));
                defCard.setStrokeWidth(2);
            }, 600);
        }

        selectedTermCard = null;
        selectedTermId = null;
        selectedDefCard = null;
        selectedDefId = null;
    }

    private void checkMatchComplete(ViewGroup container) {
        int disabledCount = 0;
        for (int i = 0; i < container.getChildCount(); i++) {
            if (!container.getChildAt(i).isEnabled()) disabledCount++;
        }
        if (disabledCount == container.getChildCount()) {
            currentTaskIndex++;
            new Handler(Looper.getMainLooper()).postDelayed(this::showNextTask, 1200);
        }
    }

    private void checkResult(Flashcard word, String input, String correct, View feedbackView) {
        boolean isCorrect = input.equalsIgnoreCase(correct);
        if (isCorrect) {
            playSoundEffect(true);
            if (feedbackView instanceof MaterialButton) {
                ((MaterialButton)feedbackView).setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.success)));
                ((MaterialButton)feedbackView).setTextColor(ContextCompat.getColor(this, R.color.white));
            } else if (feedbackView instanceof TextView) {
                feedbackView.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.success)));
                ((TextView)feedbackView).setTextColor(ContextCompat.getColor(this, R.color.white));
            }
            UiFeedback.showSnack(binding.getRoot(), "Chính xác!");
        } else {
            playSoundEffect(false);
            wordErrorCount.put(word.getFirestoreId(), wordErrorCount.getOrDefault(word.getFirestoreId(), 0) + 1);
            if (feedbackView instanceof MaterialButton) {
                ((MaterialButton)feedbackView).setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.error)));
                ((MaterialButton)feedbackView).setTextColor(ContextCompat.getColor(this, R.color.white));
            }
            Toast.makeText(this, "Sai rồi! Đáp án: " + correct, Toast.LENGTH_SHORT).show();
        }
        
        currentTaskIndex++;
        new Handler(Looper.getMainLooper()).postDelayed(this::showNextTask, 1200);
    }

    private void finishVocabChallenge() {
        for (Flashcard f : gamePool) {
            if (!wordErrorCount.containsKey(f.getFirestoreId())) {
                f.setMastered(true);
                new Thread(() -> AppDatabase.getDatabase(this).flashcardDao().update(f)).start();
            }
        }
        binding.gameContainer.setVisibility(View.GONE);
        binding.layoutAiModes.setVisibility(View.VISIBLE);
        UiFeedback.showErrorDialog(this, "Hoàn thành", "Bạn đã hoàn thành thử thách ghi nhớ!");
    }

    private void playVocabSound(Flashcard word) {
        if (word.getAudioUrl() != null && !word.getAudioUrl().isEmpty()) {
            try {
                if (mediaPlayer == null) mediaPlayer = new MediaPlayer();
                mediaPlayer.reset();
                mediaPlayer.setDataSource(word.getAudioUrl());
                mediaPlayer.prepareAsync();
                mediaPlayer.setOnPreparedListener(MediaPlayer::start);
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    speak(word.getTerm());
                    return true;
                });
            } catch (IOException e) {
                Log.e(TAG, "Error playing audio URL", e);
                speak(word.getTerm());
            }
        } else {
            speak(word.getTerm());
        }
    }

    private void speak(String text) {
        if (isTtsReady && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vocab_speak_" + System.currentTimeMillis());
        } else {
            if (tts == null) initTTS();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isTtsReady && tts != null) {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vocab_speak_retry");
                }
            }, 1000);
        }
    }

    private void updateScoreUI() {
        if (isAiRiddleMode) {
            binding.textScore.setText("Điểm: " + aiRiddleScore);
        } else {
            binding.textScore.setText("Vocabulary Challenge");
        }
    }

    private void handleBack() {
        if (riddleTimer != null) riddleTimer.cancel();
        
        if (binding.gameContainer.getVisibility() == View.VISIBLE) {
            if (isAiRiddleMode) {
                showAiRiddleLobby(); // Go back to lobby first
                isAiRiddleMode = false; // Reset temporary here, showAiRiddleLobby will set it
                // Actually, if we are in riddle game, go to lobby. If in lobby, go to mode selection.
                // Let's refine this.
            } else {
                binding.gameContainer.setVisibility(View.GONE);
                binding.layoutAiModes.setVisibility(View.VISIBLE);
            }
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
