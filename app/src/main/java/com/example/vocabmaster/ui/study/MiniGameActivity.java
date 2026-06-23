package com.example.vocabmaster.ui.study;

import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.databinding.ActivityMiniGameBinding;
import com.example.vocabmaster.ui.common.GamificationStatusBinder;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.example.vocabmaster.util.SoundEffectManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class MiniGameActivity extends AppCompatActivity {

    private ActivityMiniGameBinding binding;
    private GamificationStatusBinder gamificationStatusBinder;
    private String currentUid;
    
    private TextToSpeech tts;
    private boolean isTtsReady = false;
    private MediaPlayer mediaPlayer;

    // AI Riddle Mode variables
    private boolean isAiRiddleMode = false;
    private boolean isAiRiddleLobbyVisible = false;
    private int aiRiddleScore = 0;
    private Vocabulary currentRiddleWord;
    private CountDownTimer riddleTimer;
    private static final int RIDDLE_TIME_LIMIT = 30000; // 30 seconds
    private int currentPointsPossible = 5;
    
    private boolean hintEngShown = false;
    private boolean hintViShown = false;
    private boolean hint50Shown = false;
    private boolean hint75Shown = false;

    // Sentence Scramble variables
    private boolean isSentenceScrambleMode = false;
    private boolean isSentenceScrambleLobbyVisible = false;
    private int sentenceScrambleScore = 0;
    private CountDownTimer sentenceScrambleTimer;
    private final List<String> sentenceCorrectWords = new ArrayList<>();
    private final List<String> sentenceBoardWords = new ArrayList<>();
    private final List<String> sentenceHandWords = new ArrayList<>();
    private LinearLayout sentenceBoardContainer;
    private LinearLayout sentenceHandContainer;
    private TextView sentenceTimerText;
    private ProgressBar sentenceTimerProgress;
    private static final int SENTENCE_TIME_LIMIT = 30000;
    private static final Pattern SENTENCE_WORD_PATTERN = Pattern.compile("[A-Za-z]+(?:['-][A-Za-z]+)?");

    // Letter Scramble variables
    private boolean isLetterScrambleMode = false;
    private boolean isLetterScrambleLobbyVisible = false;
    private int letterScrambleScore = 0;
    private CountDownTimer letterScrambleTimer;
    private String currentLetterAnswer = "";
    private final List<String> letterBoardItems = new ArrayList<>();
    private final List<String> letterHandItems = new ArrayList<>();
    private LinearLayout letterBoardContainer;
    private LinearLayout letterHandContainer;
    private TextView letterTimerText;
    private ProgressBar letterTimerProgress;
    private static final int LETTER_TIME_LIMIT = 30000;
    private static final Pattern LETTER_WORD_PATTERN = Pattern.compile("[A-Za-z]{3,12}");

    // Lightning Mode variables
    private boolean isLightningMode = false;
    private boolean isLightningLobbyVisible = false;
    private int lightningScore = 0;
    private boolean currentLightningAnswer = false;
    private CountDownTimer lightningTimer;
    private final List<Vocabulary> lightningVocabs = new ArrayList<>();
    private TextView lightningTimerText;
    private ProgressBar lightningTimerProgress;
    private TextView lightningWordText;
    private TextView lightningMeaningText;
    private static final int LIGHTNING_TIME_LIMIT = 60000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMiniGameBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        gamificationStatusBinder = new GamificationStatusBinder(this, binding.getRoot());
        currentUid = FirebaseAuth.getInstance().getUid();
        gamificationStatusBinder.start(currentUid);

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
            SoundEffectManager.playCorrect(this);
        } else {
            SoundEffectManager.playWrong(this);
        }
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
        binding.cardSentenceScramble.setOnClickListener(v -> showSentenceScrambleLobby());
        binding.cardLetterScramble.setOnClickListener(v -> showLetterScrambleLobby());
        binding.cardLightning.setOnClickListener(v -> showLightningLobby());
    }

    private void showAiModes() {
        if (riddleTimer != null) riddleTimer.cancel();
        if (sentenceScrambleTimer != null) sentenceScrambleTimer.cancel();
        if (letterScrambleTimer != null) letterScrambleTimer.cancel();
        if (lightningTimer != null) lightningTimer.cancel();
        isAiRiddleMode = false;
        isAiRiddleLobbyVisible = false;
        isSentenceScrambleMode = false;
        isSentenceScrambleLobbyVisible = false;
        isLetterScrambleMode = false;
        isLetterScrambleLobbyVisible = false;
        isLightningMode = false;
        isLightningLobbyVisible = false;
        binding.gameContainer.removeAllViews();
        binding.gameContainer.setVisibility(View.GONE);
        binding.layoutModeSelection.setVisibility(View.GONE);
        binding.layoutAiModes.setVisibility(View.VISIBLE);
        binding.textTitle.setText("Chơi với máy");
        updateScoreUI();
    }

    // --- AI RIDDLE MODE ---
    private void showAiRiddleLobby() {
        isAiRiddleMode = true;
        isAiRiddleLobbyVisible = true;
        isSentenceScrambleMode = false;
        isSentenceScrambleLobbyVisible = false;
        isLetterScrambleMode = false;
        isLetterScrambleLobbyVisible = false;
        isLightningMode = false;
        isLightningLobbyVisible = false;
        binding.textTitle.setText("Đố vui cùng AI");
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
        beginAiRiddleGame();
    }

    // --- SENTENCE SCRAMBLE MODE ---
    private void showSentenceScrambleLobby() {
        if (sentenceScrambleTimer != null) sentenceScrambleTimer.cancel();
        isSentenceScrambleMode = true;
        isSentenceScrambleLobbyVisible = true;
        isAiRiddleMode = false;
        isAiRiddleLobbyVisible = false;
        isLetterScrambleMode = false;
        isLetterScrambleLobbyVisible = false;
        isLightningMode = false;
        isLightningLobbyVisible = false;
        binding.textTitle.setText("Sắp xếp câu");
        binding.layoutAiModes.setVisibility(View.GONE);
        View lobby = getLayoutInflater().inflate(R.layout.layout_game_sentence_scramble_lobby, binding.gameContainer, false);
        binding.gameContainer.removeAllViews();
        binding.gameContainer.setVisibility(View.VISIBLE);
        binding.gameContainer.addView(lobby);

        SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
        int highScore = prefs.getInt("sentence_scramble_high_score", 0);
        String history = prefs.getString("sentence_scramble_history", "Chưa có dữ liệu");

        ((TextView) lobby.findViewById(R.id.text_sentence_high_score)).setText("Kỉ lục: " + highScore + " câu");
        ((TextView) lobby.findViewById(R.id.text_sentence_history)).setText(history);

        lobby.findViewById(R.id.btn_start_sentence_scramble).setOnClickListener(v -> startSentenceScrambleGame());
        updateScoreUI();
    }

    private void startSentenceScrambleGame() {
        sentenceScrambleScore = 0;
        updateScoreUI();
        fetchAndShowNextSentence();
    }

    private void fetchAndShowNextSentence() {
        if (sentenceScrambleTimer != null) sentenceScrambleTimer.cancel();
        isSentenceScrambleLobbyVisible = false;
        binding.gameContainer.setVisibility(View.VISIBLE);

        new Thread(() -> {
            List<Vocabulary> vocabs = AppDatabase.getDatabase(this).vocabularyDao().getRandomLearnedVocabularies(30);
            if (vocabs.isEmpty()) {
                vocabs = AppDatabase.getDatabase(this).vocabularyDao().getRandomVocabularies(30);
            }

            Vocabulary selectedVocab = null;
            for (Vocabulary vocab : vocabs) {
                String sentence = safe(vocab.getExample_sentence());
                if (isUsableSentence(sentence)) {
                    selectedVocab = vocab;
                    break;
                }
            }

            Vocabulary finalVocab = selectedVocab;
            runOnUiThread(() -> {
                if (finalVocab == null) {
                    Toast.makeText(this, "Không tìm thấy câu ví dụ phù hợp!", Toast.LENGTH_SHORT).show();
                    showSentenceScrambleLobby();
                } else {
                    displaySentenceScrambleQuestion(finalVocab);
                }
            });
        }).start();
    }

    private boolean isUsableSentence(String sentence) {
        List<String> words = extractSentenceWords(sentence);
        return words.size() >= 3 && words.size() <= 12;
    }

    private void displaySentenceScrambleQuestion(Vocabulary vocab) {
        isSentenceScrambleLobbyVisible = false;
        String sentence = safe(vocab.getExample_sentence());
        sentenceCorrectWords.clear();
        sentenceCorrectWords.addAll(extractSentenceWords(sentence));
        sentenceBoardWords.clear();
        sentenceHandWords.clear();
        sentenceHandWords.addAll(sentenceCorrectWords);
        Collections.shuffle(sentenceHandWords);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(20), dp(24), dp(24));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout timerWrap = new LinearLayout(this);
        timerWrap.setOrientation(LinearLayout.VERTICAL);
        timerWrap.setGravity(Gravity.CENTER);
        root.addView(timerWrap, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        sentenceTimerText = new TextView(this);
        sentenceTimerText.setText("30");
        sentenceTimerText.setTextColor(getColor(R.color.brand_primary));
        sentenceTimerText.setTextSize(24);
        sentenceTimerText.setTypeface(Typeface.DEFAULT_BOLD);
        sentenceTimerText.setGravity(Gravity.CENTER);
        timerWrap.addView(sentenceTimerText, new LinearLayout.LayoutParams(dp(64), dp(48)));

        sentenceTimerProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        sentenceTimerProgress.setMax(SENTENCE_TIME_LIMIT / 100);
        sentenceTimerProgress.setProgress(SENTENCE_TIME_LIMIT / 100);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
        );
        progressParams.setMargins(0, dp(8), 0, dp(24));
        timerWrap.addView(sentenceTimerProgress, progressParams);

        TextView meaningTitle = sectionLabel("Nghĩa tiếng Việt");
        root.addView(meaningTitle);

        TextView meaningText = new TextView(this);
        String vietnameseHint = safe(vocab.getVietnamese_translation());
        if (vietnameseHint.isEmpty()) vietnameseHint = safe(vocab.getVietnameseTranslation());
        if (vietnameseHint.isEmpty()) vietnameseHint = "Chưa có nghĩa tiếng Việt cho câu này";
        meaningText.setText(vietnameseHint);
        meaningText.setTextColor(getColor(R.color.text_primary));
        meaningText.setTextSize(18);
        meaningText.setGravity(Gravity.CENTER);
        meaningText.setLineSpacing(dp(4), 1f);
        meaningText.setPadding(dp(16), dp(14), dp(16), dp(14));
        meaningText.setBackgroundResource(R.drawable.bg_white_translucent_stroke);
        LinearLayout.LayoutParams meaningParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        meaningParams.setMargins(0, dp(8), 0, dp(20));
        root.addView(meaningText, meaningParams);

        TextView boardTitle = sectionLabel("Bảng sắp xếp");
        root.addView(boardTitle);

        sentenceBoardContainer = wordArea();
        root.addView(sentenceBoardContainer, areaParams());

        TextView handTitle = sectionLabel("Trên tay");
        LinearLayout.LayoutParams handTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        handTitleParams.setMargins(0, dp(18), 0, dp(8));
        root.addView(handTitle, handTitleParams);

        sentenceHandContainer = wordArea();
        root.addView(sentenceHandContainer, areaParams());

        MaterialButton submitButton = new MaterialButton(this);
        submitButton.setText("NỘP BÀI");
        submitButton.setTextSize(16);
        submitButton.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
        );
        submitParams.setMargins(0, dp(24), 0, 0);
        root.addView(submitButton, submitParams);
        submitButton.setOnClickListener(v -> submitSentenceScramble());

        binding.gameContainer.removeAllViews();
        binding.gameContainer.addView(scrollView);
        renderSentenceWordAreas();
        startSentenceScrambleTimer();
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(getColor(R.color.text_primary));
        label.setTextSize(16);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        return label;
    }

    private LinearLayout wordArea() {
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        area.setPadding(dp(8), dp(8), dp(8), dp(8));
        area.setBackgroundResource(R.drawable.bg_white_translucent_stroke);
        return area;
    }

    private LinearLayout.LayoutParams areaParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(8), 0, 0);
        return params;
    }

    private void renderSentenceWordAreas() {
        renderWordList(sentenceBoardContainer, sentenceBoardWords, true);
        renderWordList(sentenceHandContainer, sentenceHandWords, false);
    }

    private void renderWordList(LinearLayout container, List<String> words, boolean fromBoard) {
        container.removeAllViews();
        if (words.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(fromBoard ? "Chạm các thẻ ở dưới để đưa lên đây" : "Không còn thẻ nào");
            empty.setTextColor(getColor(R.color.text_secondary));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(12), 0, dp(12));
            container.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            return;
        }

        LinearLayout row = null;
        int rowWidth = 0;
        int maxRowWidth = getResources().getDisplayMetrics().widthPixels - dp(80);
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            int chipOuterWidth = estimateChipOuterWidth(word);
            if (row == null || rowWidth + chipOuterWidth > maxRowWidth) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                container.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                ));
                rowWidth = 0;
            }

            MaterialButton chip = new MaterialButton(this);
            chip.setText(word);
            chip.setTextSize(14);
            chip.setAllCaps(false);
            chip.setSingleLine(true);
            chip.setMinHeight(0);
            chip.setMinWidth(0);
            chip.setMinimumHeight(0);
            chip.setMinimumWidth(0);
            chip.setInsetTop(0);
            chip.setInsetBottom(0);
            chip.setPadding(dp(10), dp(6), dp(10), dp(6));
            int index = i;
            chip.setOnClickListener(v -> moveSentenceWord(index, fromBoard));

            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    Math.min(chipOuterWidth - dp(8), maxRowWidth),
                    dp(42)
            );
            chipParams.setMargins(dp(4), dp(4), dp(4), dp(4));
            row.addView(chip, chipParams);
            rowWidth += chipOuterWidth;
        }
    }

    private int estimateChipOuterWidth(String word) {
        TextView measureView = new TextView(this);
        measureView.setTextSize(14);
        float textWidth = measureView.getPaint().measureText(word);
        return (int) Math.ceil(textWidth) + dp(36);
    }

    private void moveSentenceWord(int index, boolean fromBoard) {
        if (fromBoard) {
            if (index >= 0 && index < sentenceBoardWords.size()) {
                sentenceHandWords.add(sentenceBoardWords.remove(index));
            }
        } else {
            if (index >= 0 && index < sentenceHandWords.size()) {
                sentenceBoardWords.add(sentenceHandWords.remove(index));
            }
        }
        renderSentenceWordAreas();
    }

    private void submitSentenceScramble() {
        if (sentenceBoardWords.equals(sentenceCorrectWords)) {
            if (sentenceScrambleTimer != null) sentenceScrambleTimer.cancel();
            sentenceScrambleScore += 1;
            updateScoreUI();
            playSoundEffect(true);
            UiFeedback.showSnack(binding.getRoot(), "Chính xác! +1 điểm");
            new Handler(Looper.getMainLooper()).postDelayed(this::fetchAndShowNextSentence, 800);
        } else {
            playSoundEffect(false);
            finishSentenceScrambleGame("Sai rồi! Câu đúng là:\n" + String.join(" ", sentenceCorrectWords));
        }
    }

    private void startSentenceScrambleTimer() {
        if (sentenceScrambleTimer != null) sentenceScrambleTimer.cancel();
        sentenceScrambleTimer = new CountDownTimer(SENTENCE_TIME_LIMIT, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                sentenceTimerText.setText(String.valueOf((int) (millisUntilFinished / 1000)));
                sentenceTimerProgress.setProgress((int) (millisUntilFinished / 100));
            }

            @Override
            public void onFinish() {
                finishSentenceScrambleGame("Hết giờ!");
            }
        }.start();
    }

    private void finishSentenceScrambleGame(String reason) {
        if (sentenceScrambleTimer != null) sentenceScrambleTimer.cancel();

        SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
        int oldHighScore = prefs.getInt("sentence_scramble_high_score", 0);
        boolean isNewRecord = sentenceScrambleScore > oldHighScore;
        if (isNewRecord) {
            prefs.edit().putInt("sentence_scramble_high_score", sentenceScrambleScore).apply();
        }

        String history = prefs.getString("sentence_scramble_history", "");
        String newEntry = "Điểm: " + sentenceScrambleScore + " - " + new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date());
        if (history.isEmpty() || history.equals("Chưa có dữ liệu")) history = newEntry;
        else {
            String[] lines = history.split("\n");
            StringBuilder sb = new StringBuilder(newEntry);
            for (int i = 0; i < Math.min(lines.length, 4); i++) {
                sb.append("\n").append(lines[i]);
            }
            history = sb.toString();
        }
        prefs.edit().putString("sentence_scramble_history", history).apply();

        String msg = reason + "\n\nBạn xếp đúng " + sentenceScrambleScore + " câu.";
        if (isNewRecord && sentenceScrambleScore > 0) msg += "\n\nCHÚC MỪNG! BẠN ĐÃ PHÁ KỈ LỤC!";

        new AlertDialog.Builder(this)
                .setTitle("Game Over")
                .setMessage(msg)
                .setPositiveButton("Về sảnh", (dialog, which) -> showSentenceScrambleLobby())
                .setCancelable(false)
                .show();
    }

    private List<String> extractSentenceWords(String sentence) {
        List<String> words = new ArrayList<>();
        Matcher matcher = SENTENCE_WORD_PATTERN.matcher(safe(sentence));
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return words;
    }

    // --- LETTER SCRAMBLE MODE ---
    private void showLetterScrambleLobby() {
        if (letterScrambleTimer != null) letterScrambleTimer.cancel();
        isLetterScrambleMode = true;
        isLetterScrambleLobbyVisible = true;
        isAiRiddleMode = false;
        isAiRiddleLobbyVisible = false;
        isSentenceScrambleMode = false;
        isSentenceScrambleLobbyVisible = false;
        isLightningMode = false;
        isLightningLobbyVisible = false;
        binding.textTitle.setText("Sắp xếp chữ");
        binding.layoutAiModes.setVisibility(View.GONE);
        View lobby = getLayoutInflater().inflate(R.layout.layout_game_letter_scramble_lobby, binding.gameContainer, false);
        binding.gameContainer.removeAllViews();
        binding.gameContainer.setVisibility(View.VISIBLE);
        binding.gameContainer.addView(lobby);

        SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
        int highScore = prefs.getInt("letter_scramble_high_score", 0);
        String history = prefs.getString("letter_scramble_history", "Chưa có dữ liệu");

        ((TextView) lobby.findViewById(R.id.text_letter_high_score)).setText("Kỉ lục: " + highScore + " từ");
        ((TextView) lobby.findViewById(R.id.text_letter_history)).setText(history);

        lobby.findViewById(R.id.btn_start_letter_scramble).setOnClickListener(v -> startLetterScrambleGame());
        updateScoreUI();
    }

    private void startLetterScrambleGame() {
        letterScrambleScore = 0;
        updateScoreUI();
        fetchAndShowNextLetterWord();
    }

    private void fetchAndShowNextLetterWord() {
        if (letterScrambleTimer != null) letterScrambleTimer.cancel();
        isLetterScrambleLobbyVisible = false;
        binding.gameContainer.setVisibility(View.VISIBLE);

        new Thread(() -> {
            List<Vocabulary> vocabs = AppDatabase.getDatabase(this).vocabularyDao().getRandomLearnedVocabularies(30);
            if (vocabs.isEmpty()) {
                vocabs = AppDatabase.getDatabase(this).vocabularyDao().getRandomVocabularies(30);
            }

            Vocabulary selectedVocab = null;
            for (Vocabulary vocab : vocabs) {
                if (isUsableLetterWord(vocab)) {
                    selectedVocab = vocab;
                    break;
                }
            }

            Vocabulary finalVocab = selectedVocab;
            runOnUiThread(() -> {
                if (finalVocab == null) {
                    Toast.makeText(this, "Không tìm thấy từ phù hợp để xếp chữ!", Toast.LENGTH_SHORT).show();
                    showLetterScrambleLobby();
                } else {
                    displayLetterScrambleQuestion(finalVocab);
                }
            });
        }).start();
    }

    private boolean isUsableLetterWord(Vocabulary vocab) {
        return vocab != null && !normalizeLetterAnswer(vocab.getWord()).isEmpty();
    }

    private String normalizeLetterAnswer(String word) {
        String value = safe(word);
        if (!LETTER_WORD_PATTERN.matcher(value).matches()) return "";
        return value.toLowerCase(Locale.US);
    }

    private void displayLetterScrambleQuestion(Vocabulary vocab) {
        isLetterScrambleLobbyVisible = false;
        currentLetterAnswer = normalizeLetterAnswer(vocab.getWord());
        letterBoardItems.clear();
        letterHandItems.clear();
        for (int i = 0; i < currentLetterAnswer.length(); i++) {
            letterHandItems.add(String.valueOf(currentLetterAnswer.charAt(i)).toUpperCase(Locale.US));
        }
        Collections.shuffle(letterHandItems);
        if (String.join("", letterHandItems).equalsIgnoreCase(currentLetterAnswer) && letterHandItems.size() > 3) {
            Collections.shuffle(letterHandItems);
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(20), dp(24), dp(24));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout timerWrap = new LinearLayout(this);
        timerWrap.setOrientation(LinearLayout.VERTICAL);
        timerWrap.setGravity(Gravity.CENTER);
        root.addView(timerWrap, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        letterTimerText = new TextView(this);
        letterTimerText.setText("30");
        letterTimerText.setTextColor(getColor(R.color.brand_primary));
        letterTimerText.setTextSize(24);
        letterTimerText.setTypeface(Typeface.DEFAULT_BOLD);
        letterTimerText.setGravity(Gravity.CENTER);
        timerWrap.addView(letterTimerText, new LinearLayout.LayoutParams(dp(64), dp(48)));

        letterTimerProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        letterTimerProgress.setMax(LETTER_TIME_LIMIT / 100);
        letterTimerProgress.setProgress(LETTER_TIME_LIMIT / 100);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
        );
        progressParams.setMargins(0, dp(8), 0, dp(24));
        timerWrap.addView(letterTimerProgress, progressParams);

        TextView englishTitle = sectionLabel("Nghĩa tiếng Anh");
        root.addView(englishTitle);

        TextView englishText = hintBox(safe(vocab.getDefinition()).isEmpty()
                ? "Chưa có nghĩa tiếng Anh cho từ này"
                : safe(vocab.getDefinition()));
        root.addView(englishText, hintBoxParams());

        TextView vietnameseTitle = sectionLabel("Nghĩa tiếng Việt");
        LinearLayout.LayoutParams vietnameseTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        vietnameseTitleParams.setMargins(0, dp(12), 0, 0);
        root.addView(vietnameseTitle, vietnameseTitleParams);

        String vietnameseHint = safe(vocab.getVietnamese_translation());
        if (vietnameseHint.isEmpty()) vietnameseHint = safe(vocab.getVietnameseTranslation());
        TextView vietnameseText = hintBox(vietnameseHint.isEmpty()
                ? "Chưa có nghĩa tiếng Việt cho từ này"
                : vietnameseHint);
        root.addView(vietnameseText, hintBoxParams());

        TextView letterCount = new TextView(this);
        letterCount.setText("Số chữ: " + currentLetterAnswer.length());
        letterCount.setTextColor(getColor(R.color.text_secondary));
        letterCount.setGravity(Gravity.CENTER);
        letterCount.setTextSize(15);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        countParams.setMargins(0, dp(12), 0, dp(16));
        root.addView(letterCount, countParams);

        TextView boardTitle = sectionLabel("Bảng trả lời");
        root.addView(boardTitle);

        letterBoardContainer = wordArea();
        root.addView(letterBoardContainer, areaParams());

        TextView handTitle = sectionLabel("Chữ cái");
        LinearLayout.LayoutParams handTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        handTitleParams.setMargins(0, dp(18), 0, dp(8));
        root.addView(handTitle, handTitleParams);

        letterHandContainer = wordArea();
        root.addView(letterHandContainer, areaParams());

        MaterialButton submitButton = new MaterialButton(this);
        submitButton.setText("NỘP BÀI");
        submitButton.setTextSize(16);
        submitButton.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
        );
        submitParams.setMargins(0, dp(24), 0, 0);
        root.addView(submitButton, submitParams);
        submitButton.setOnClickListener(v -> submitLetterScramble());

        binding.gameContainer.removeAllViews();
        binding.gameContainer.addView(scrollView);
        renderLetterScrambleAreas();
        startLetterScrambleTimer();
    }

    private TextView hintBox(String text) {
        TextView hint = new TextView(this);
        hint.setText(text);
        hint.setTextColor(getColor(R.color.text_primary));
        hint.setTextSize(17);
        hint.setGravity(Gravity.CENTER);
        hint.setLineSpacing(dp(4), 1f);
        hint.setPadding(dp(16), dp(14), dp(16), dp(14));
        hint.setBackgroundResource(R.drawable.bg_white_translucent_stroke);
        return hint;
    }

    private LinearLayout.LayoutParams hintBoxParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(8), 0, 0);
        return params;
    }

    private void renderLetterScrambleAreas() {
        renderLetterList(letterBoardContainer, letterBoardItems, true);
        renderLetterList(letterHandContainer, letterHandItems, false);
    }

    private void renderLetterList(LinearLayout container, List<String> letters, boolean fromBoard) {
        container.removeAllViews();
        if (letters.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(fromBoard ? "Chạm các chữ ở dưới để đưa lên đây" : "Không còn chữ nào");
            empty.setTextColor(getColor(R.color.text_secondary));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(12), 0, dp(12));
            container.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            return;
        }

        LinearLayout row = null;
        int rowWidth = 0;
        int chipOuterWidth = dp(52);
        int maxRowWidth = getResources().getDisplayMetrics().widthPixels - dp(80);
        for (int i = 0; i < letters.size(); i++) {
            if (row == null || rowWidth + chipOuterWidth > maxRowWidth) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                container.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                ));
                rowWidth = 0;
            }

            MaterialButton chip = new MaterialButton(this);
            chip.setText(letters.get(i));
            chip.setTextSize(18);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setAllCaps(false);
            chip.setSingleLine(true);
            chip.setMinHeight(0);
            chip.setMinWidth(0);
            chip.setMinimumHeight(0);
            chip.setMinimumWidth(0);
            chip.setInsetTop(0);
            chip.setInsetBottom(0);
            chip.setPadding(0, 0, 0, 0);
            int index = i;
            chip.setOnClickListener(v -> moveLetterItem(index, fromBoard));

            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(dp(44), dp(44));
            chipParams.setMargins(dp(4), dp(4), dp(4), dp(4));
            row.addView(chip, chipParams);
            rowWidth += chipOuterWidth;
        }
    }

    private void moveLetterItem(int index, boolean fromBoard) {
        if (fromBoard) {
            if (index >= 0 && index < letterBoardItems.size()) {
                letterHandItems.add(letterBoardItems.remove(index));
            }
        } else {
            if (index >= 0 && index < letterHandItems.size()) {
                letterBoardItems.add(letterHandItems.remove(index));
            }
        }
        renderLetterScrambleAreas();
    }

    private void submitLetterScramble() {
        String answer = String.join("", letterBoardItems).toLowerCase(Locale.US);
        if (answer.equals(currentLetterAnswer)) {
            if (letterScrambleTimer != null) letterScrambleTimer.cancel();
            letterScrambleScore += 1;
            updateScoreUI();
            playSoundEffect(true);
            UiFeedback.showSnack(binding.getRoot(), "Chính xác! +1 điểm");
            new Handler(Looper.getMainLooper()).postDelayed(this::fetchAndShowNextLetterWord, 800);
        } else {
            playSoundEffect(false);
            finishLetterScrambleGame("Sai rồi! Từ đúng là: " + currentLetterAnswer);
        }
    }

    private void startLetterScrambleTimer() {
        if (letterScrambleTimer != null) letterScrambleTimer.cancel();
        letterScrambleTimer = new CountDownTimer(LETTER_TIME_LIMIT, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                letterTimerText.setText(String.valueOf((int) (millisUntilFinished / 1000)));
                letterTimerProgress.setProgress((int) (millisUntilFinished / 100));
            }

            @Override
            public void onFinish() {
                finishLetterScrambleGame("Hết giờ! Từ đúng là: " + currentLetterAnswer);
            }
        }.start();
    }

    private void finishLetterScrambleGame(String reason) {
        if (letterScrambleTimer != null) letterScrambleTimer.cancel();

        SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
        int oldHighScore = prefs.getInt("letter_scramble_high_score", 0);
        boolean isNewRecord = letterScrambleScore > oldHighScore;
        if (isNewRecord) {
            prefs.edit().putInt("letter_scramble_high_score", letterScrambleScore).apply();
        }

        String history = prefs.getString("letter_scramble_history", "");
        String newEntry = "Điểm: " + letterScrambleScore + " - " + new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date());
        if (history.isEmpty() || history.equals("Chưa có dữ liệu")) history = newEntry;
        else {
            String[] lines = history.split("\n");
            StringBuilder sb = new StringBuilder(newEntry);
            for (int i = 0; i < Math.min(lines.length, 4); i++) {
                sb.append("\n").append(lines[i]);
            }
            history = sb.toString();
        }
        prefs.edit().putString("letter_scramble_history", history).apply();

        String msg = reason + "\n\nBạn xếp đúng " + letterScrambleScore + " từ.";
        if (isNewRecord && letterScrambleScore > 0) msg += "\n\nCHÚC MỪNG! BẠN ĐÃ PHÁ KỈ LỤC!";

        new AlertDialog.Builder(this)
                .setTitle("Game Over")
                .setMessage(msg)
                .setPositiveButton("Về sảnh", (dialog, which) -> showLetterScrambleLobby())
                .setCancelable(false)
                .show();
    }

    // --- LIGHTNING MODE ---
    private void showLightningLobby() {
        if (lightningTimer != null) lightningTimer.cancel();
        isLightningMode = true;
        isLightningLobbyVisible = true;
        isAiRiddleMode = false;
        isAiRiddleLobbyVisible = false;
        isSentenceScrambleMode = false;
        isSentenceScrambleLobbyVisible = false;
        isLetterScrambleMode = false;
        isLetterScrambleLobbyVisible = false;
        binding.textTitle.setText("Nhanh như chớp");
        binding.layoutAiModes.setVisibility(View.GONE);
        View lobby = getLayoutInflater().inflate(R.layout.layout_game_lightning_lobby, binding.gameContainer, false);
        binding.gameContainer.removeAllViews();
        binding.gameContainer.setVisibility(View.VISIBLE);
        binding.gameContainer.addView(lobby);

        SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
        int highScore = prefs.getInt("lightning_high_score", 0);
        String history = prefs.getString("lightning_history", "Chưa có dữ liệu");

        ((TextView) lobby.findViewById(R.id.text_lightning_high_score)).setText("Kỉ lục: " + highScore + " điểm");
        ((TextView) lobby.findViewById(R.id.text_lightning_history)).setText(history);

        lobby.findViewById(R.id.btn_start_lightning).setOnClickListener(v -> startLightningGame());
        updateScoreUI();
    }

    private void startLightningGame() {
        lightningScore = 0;
        lightningVocabs.clear();
        updateScoreUI();

        new Thread(() -> {
            List<Vocabulary> vocabs = AppDatabase.getDatabase(this).vocabularyDao().getRandomLearnedVocabularies(100);
            List<Vocabulary> filteredVocabs = filterLightningVocabs(vocabs);
            if (filteredVocabs.size() < 2) {
                filteredVocabs = filterLightningVocabs(AppDatabase.getDatabase(this).vocabularyDao().getRandomVocabularies(100));
            }

            List<Vocabulary> finalVocabs = filteredVocabs;
            runOnUiThread(() -> {
                if (finalVocabs.size() < 2) {
                    Toast.makeText(this, "Cần ít nhất 2 từ có nghĩa tiếng Việt để chơi!", Toast.LENGTH_SHORT).show();
                    showLightningLobby();
                    return;
                }
                lightningVocabs.clear();
                lightningVocabs.addAll(finalVocabs);
                displayLightningGame();
            });
        }).start();
    }

    private List<Vocabulary> filterLightningVocabs(List<Vocabulary> vocabs) {
        List<Vocabulary> filtered = new ArrayList<>();
        for (Vocabulary vocab : vocabs) {
            if (!isBlank(vocab.getWord()) && !getVietnameseMeaning(vocab).isEmpty()) {
                filtered.add(vocab);
            }
        }
        return filtered;
    }

    private void displayLightningGame() {
        isLightningLobbyVisible = false;

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(20), dp(24), dp(24));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        lightningTimerText = new TextView(this);
        lightningTimerText.setText("01:00");
        lightningTimerText.setTextColor(getColor(R.color.brand_primary));
        lightningTimerText.setTextSize(26);
        lightningTimerText.setTypeface(Typeface.DEFAULT_BOLD);
        lightningTimerText.setGravity(Gravity.CENTER);
        root.addView(lightningTimerText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        ));

        lightningTimerProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        lightningTimerProgress.setMax(LIGHTNING_TIME_LIMIT / 100);
        lightningTimerProgress.setProgress(LIGHTNING_TIME_LIMIT / 100);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
        );
        progressParams.setMargins(0, dp(8), 0, dp(28));
        root.addView(lightningTimerProgress, progressParams);

        TextView wordTitle = sectionLabel("Từ tiếng Anh");
        root.addView(wordTitle);

        lightningWordText = new TextView(this);
        lightningWordText.setTextColor(getColor(R.color.text_primary));
        lightningWordText.setTextSize(34);
        lightningWordText.setTypeface(Typeface.DEFAULT_BOLD);
        lightningWordText.setGravity(Gravity.CENTER);
        lightningWordText.setPadding(dp(16), dp(20), dp(16), dp(20));
        lightningWordText.setBackgroundResource(R.drawable.bg_white_translucent_stroke);
        LinearLayout.LayoutParams wordParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        wordParams.setMargins(0, dp(8), 0, dp(20));
        root.addView(lightningWordText, wordParams);

        TextView meaningTitle = sectionLabel("Nghĩa tiếng Việt");
        root.addView(meaningTitle);

        lightningMeaningText = hintBox("");
        lightningMeaningText.setTextSize(20);
        LinearLayout.LayoutParams meaningParams = hintBoxParams();
        meaningParams.setMargins(0, dp(8), 0, dp(28));
        root.addView(lightningMeaningText, meaningParams);

        LinearLayout answerRow = new LinearLayout(this);
        answerRow.setOrientation(LinearLayout.HORIZONTAL);
        answerRow.setGravity(Gravity.CENTER);
        root.addView(answerRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
        ));

        MaterialButton wrongButton = lightningAnswerButton("SAI");
        LinearLayout.LayoutParams wrongParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        wrongParams.setMargins(0, 0, dp(8), 0);
        answerRow.addView(wrongButton, wrongParams);
        wrongButton.setOnClickListener(v -> answerLightning(false));

        MaterialButton correctButton = lightningAnswerButton("ĐÚNG");
        LinearLayout.LayoutParams correctParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        correctParams.setMargins(dp(8), 0, 0, 0);
        answerRow.addView(correctButton, correctParams);
        correctButton.setOnClickListener(v -> answerLightning(true));

        binding.gameContainer.removeAllViews();
        binding.gameContainer.addView(scrollView);
        showNextLightningQuestion();
        startLightningTimer();
    }

    private MaterialButton lightningAnswerButton(String text) {
        MaterialButton button = new MaterialButton(this);
        button.setText(text);
        button.setTextSize(18);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        return button;
    }

    private void showNextLightningQuestion() {
        if (lightningVocabs.size() < 2) return;

        Vocabulary wordVocab = lightningVocabs.get((int) (Math.random() * lightningVocabs.size()));
        boolean shouldMatch = Math.random() < 0.5;
        Vocabulary meaningVocab = wordVocab;

        if (!shouldMatch) {
            List<Vocabulary> shuffledVocabs = new ArrayList<>(lightningVocabs);
            Collections.shuffle(shuffledVocabs);
            for (Vocabulary candidate : shuffledVocabs) {
                if (candidate != wordVocab
                        && !getVietnameseMeaning(candidate).equalsIgnoreCase(getVietnameseMeaning(wordVocab))) {
                    meaningVocab = candidate;
                    break;
                }
            }
            shouldMatch = meaningVocab == wordVocab;
        }

        currentLightningAnswer = shouldMatch;
        lightningWordText.setText(safe(wordVocab.getWord()));
        lightningMeaningText.setText(getVietnameseMeaning(meaningVocab));
    }

    private void answerLightning(boolean userAnswer) {
        boolean isCorrect = userAnswer == currentLightningAnswer;
        lightningScore += isCorrect ? 1 : -1;
        updateScoreUI();
        playSoundEffect(isCorrect);
        UiFeedback.showSnack(binding.getRoot(), isCorrect ? "Chính xác! +1 điểm" : "Sai rồi! -1 điểm");
        showNextLightningQuestion();
    }

    private void startLightningTimer() {
        if (lightningTimer != null) lightningTimer.cancel();
        lightningTimer = new CountDownTimer(LIGHTNING_TIME_LIMIT, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                lightningTimerText.setText(formatGameTime(millisUntilFinished));
                lightningTimerProgress.setProgress((int) (millisUntilFinished / 100));
            }

            @Override
            public void onFinish() {
                lightningTimerText.setText("00:00");
                lightningTimerProgress.setProgress(0);
                finishLightningGame();
            }
        }.start();
    }

    private void finishLightningGame() {
        if (lightningTimer != null) lightningTimer.cancel();

        SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
        int oldHighScore = prefs.getInt("lightning_high_score", 0);
        boolean isNewRecord = lightningScore > oldHighScore;
        if (isNewRecord) {
            prefs.edit().putInt("lightning_high_score", lightningScore).apply();
        }

        String history = prefs.getString("lightning_history", "");
        String newEntry = "Điểm: " + lightningScore + " - " + new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date());
        if (history.isEmpty() || history.equals("Chưa có dữ liệu")) history = newEntry;
        else {
            String[] lines = history.split("\n");
            StringBuilder sb = new StringBuilder(newEntry);
            for (int i = 0; i < Math.min(lines.length, 4); i++) {
                sb.append("\n").append(lines[i]);
            }
            history = sb.toString();
        }
        prefs.edit().putString("lightning_history", history).apply();

        String msg = "Hết giờ! Bạn đạt " + lightningScore + " điểm.";
        if (isNewRecord && lightningScore > 0) msg += "\n\nCHÚC MỪNG! BẠN ĐÃ PHÁ KỈ LỤC!";

        new AlertDialog.Builder(this)
                .setTitle("Tổng kết")
                .setMessage(msg)
                .setPositiveButton("Về sảnh", (dialog, which) -> showLightningLobby())
                .setCancelable(false)
                .show();
    }

    private String getVietnameseMeaning(Vocabulary vocab) {
        if (vocab == null) return "";
        String meaning = safe(vocab.getVietnamese_translation());
        if (meaning.isEmpty()) meaning = safe(vocab.getVietnameseTranslation());
        return meaning;
    }

    private String formatGameTime(long millisUntilFinished) {
        long totalSeconds = Math.max(0L, millisUntilFinished / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private void beginAiRiddleGame() {
        isAiRiddleLobbyVisible = false;
        aiRiddleScore = 0;
        updateScoreUI();
        fetchAndShowNextRiddle();
    }

    private void fetchAndShowNextRiddle() {
        if (riddleTimer != null) riddleTimer.cancel();
        binding.gameContainer.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<Vocabulary> randomVocabs = AppDatabase.getDatabase(this).vocabularyDao().getRandomVocabularies(20);
            Vocabulary selectedLocalWord = null;
            for (Vocabulary vocab : randomVocabs) {
                if (isUsableRiddleWord(vocab)) {
                    selectedLocalWord = vocab;
                    break;
                }
            }

            Vocabulary finalWord = selectedLocalWord;
            runOnUiThread(() -> {
                if (finalWord != null) {
                    currentRiddleWord = finalWord;
                    displayRiddleQuestion();
                } else {
                    Toast.makeText(this, "Không tìm thấy dữ liệu từ vựng phù hợp!", Toast.LENGTH_SHORT).show();
                    showAiRiddleLobby();
                }
            });
        }).start();
    }

    private boolean isUsableRiddleWord(Vocabulary vocab) {
        if (vocab == null || isBlank(vocab.getWord())) return false;
        return !isBlank(vocab.getDefinition()) || !isBlank(vocab.getExample_sentence());
    }

    private void displayRiddleQuestion() {
        isAiRiddleLobbyVisible = false;
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
        String word = safe(currentRiddleWord.getWord()).toLowerCase(Locale.US);
        String question = safe(currentRiddleWord.getExample_sentence());
        if (question.isEmpty()) question = safe(currentRiddleWord.getDefinition());
        if (question.isEmpty()) question = "Which English word matches this vocabulary riddle?";
        
        question = question.replaceAll("(?i)" + Pattern.quote(word), "_______");
        if (!question.contains("_______")) {
            question = "Từ tiếng Anh nào có nghĩa là: " + safe(currentRiddleWord.getDefinition());
        }
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
                        String vi = safe(currentRiddleWord.getVietnamese_translation());
                        if (vi.isEmpty()) vi = safe(currentRiddleWord.getVietnameseTranslation());
                        if (vi.isEmpty()) vi = "Chưa có bản dịch";
                        textViHint.setText("Gợi ý (Tiếng Việt): " + vi);
                    }
                    UiFeedback.showSnack(binding.getRoot(), "Gợi ý 2: Nghĩa tiếng Việt (+3 điểm)");
                }

                // 15s (5s more): 50% characters hint, +2 points
                if (elapsed >= 15000 && !hint50Shown) {
                    hint50Shown = true;
                    currentPointsPossible = 2;
                    textPlaceholders.setText(getMaskedWord(safe(currentRiddleWord.getWord()).toLowerCase(Locale.US), 50));
                    UiFeedback.showSnack(binding.getRoot(), "Gợi ý 3: 50% ký tự (+2 điểm)");
                }

                // 25s (10s more): 75% characters hint, +1 point
                if (elapsed >= 25000 && !hint75Shown) {
                    hint75Shown = true;
                    currentPointsPossible = 1;
                    textPlaceholders.setText(getMaskedWord(safe(currentRiddleWord.getWord()).toLowerCase(Locale.US), 75));
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

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return safe(value).isEmpty();
    }

    private void updateScoreUI() {
        if (isAiRiddleMode) {
            binding.textScore.setText("Điểm: " + aiRiddleScore);
        } else if (isSentenceScrambleMode) {
            binding.textScore.setText("Điểm: " + sentenceScrambleScore);
        } else if (isLetterScrambleMode) {
            binding.textScore.setText("Điểm: " + letterScrambleScore);
        } else if (isLightningMode) {
            binding.textScore.setText("Điểm: " + lightningScore);
        } else {
            binding.textScore.setText("Minigames");
        }
    }

    private void handleBack() {
        if (riddleTimer != null) riddleTimer.cancel();
        if (sentenceScrambleTimer != null) sentenceScrambleTimer.cancel();
        if (letterScrambleTimer != null) letterScrambleTimer.cancel();
        if (lightningTimer != null) lightningTimer.cancel();
        
        if (binding.gameContainer.getVisibility() == View.VISIBLE) {
            if (isAiRiddleLobbyVisible || isSentenceScrambleLobbyVisible || isLetterScrambleLobbyVisible || isLightningLobbyVisible) {
                showAiModes();
            } else if (isLightningMode) {
                showLightningLobby();
            } else if (isLetterScrambleMode) {
                showLetterScrambleLobby();
            } else if (isSentenceScrambleMode) {
                showSentenceScrambleLobby();
            } else {
                showAiRiddleLobby();
            }
        } else if (binding.layoutAiModes.getVisibility() == View.VISIBLE) {
            binding.layoutAiModes.setVisibility(View.GONE);
            binding.layoutModeSelection.setVisibility(View.VISIBLE);
            binding.textTitle.setText("Minigames");
            isAiRiddleMode = false;
            isAiRiddleLobbyVisible = false;
            isSentenceScrambleMode = false;
            isSentenceScrambleLobbyVisible = false;
            isLetterScrambleMode = false;
            isLetterScrambleLobbyVisible = false;
            isLightningMode = false;
            isLightningLobbyVisible = false;
            updateScoreUI();
        } else finish();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
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
        if (sentenceScrambleTimer != null) sentenceScrambleTimer.cancel();
        if (letterScrambleTimer != null) letterScrambleTimer.cancel();
        if (lightningTimer != null) lightningTimer.cancel();
        if (gamificationStatusBinder != null) gamificationStatusBinder.stop();
        super.onDestroy();
    }
}
