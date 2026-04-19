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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.data.repository.CourseRepository;
import com.example.vocabmaster.databinding.ActivityMiniGameBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private CourseRepository repository;

    // AI Quiz variables
    private List<Vocabulary> oxfordVocab = new ArrayList<>();
    private Vocabulary currentQuizVocab;
    private CountDownTimer quizTimer;
    private int quizHighScore = 0;
    private List<String> quizHistory = new ArrayList<>();
    private SharedPreferences prefs;

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

        repository = new CourseRepository(getApplication());
        prefs = getSharedPreferences("MiniGamePrefs", MODE_PRIVATE);
        quizHighScore = prefs.getInt("ai_quiz_high_score", 0);
        String historyStr = prefs.getString("ai_quiz_history", "");
        if (!historyStr.isEmpty()) {
            Collections.addAll(quizHistory, historyStr.split("\\|"));
        }

        initTTS();
        initMediaPlayer();
        initData();
        setupClickListeners();
        loadOxfordVocab();
        
        binding.textScore.setVisibility(View.GONE);
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
                
                updateDashboardUIIfVisible();
            }
        });
    }

    private void loadOxfordVocab() {
        FirebaseFirestore.getInstance().collection("vocabularies")
                .limit(200)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    oxfordVocab.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Vocabulary v = doc.toObject(Vocabulary.class);
                        if (v != null) {
                            v.setVocabularyId(doc.getId());
                            oxfordVocab.add(v);
                        }
                    }
                    Log.d(TAG, "Loaded " + oxfordVocab.size() + " oxford vocabularies");
                });
    }

    private void updateDashboardUIIfVisible() {
        View dashboard = binding.gameContainer.findViewById(R.id.layout_game_dashboard_root);
        if (dashboard != null && dashboard.getVisibility() == View.VISIBLE) {
            TextView textCount = dashboard.findViewById(R.id.text_mastered_count);
            ProgressBar progress = dashboard.findViewById(R.id.progress_mastery);
            
            int total = myFlashcards.size();
            int mastered = masteredWordIds.size();
            textCount.setText(mastered + "/" + total);
            if (total > 0) progress.setProgress((mastered * 100) / total);
        }
    }

    private void setupClickListeners() {
        binding.btnClose.setOnClickListener(v -> handleBack());
        binding.cardPlayAi.setOnClickListener(v -> showAiModes());
        binding.cardVocabularyChallenge.setOnClickListener(v -> showDashboard());
        binding.cardAiQuiz.setOnClickListener(v -> showAiQuizLobby());
    }

    private void showAiModes() {
        binding.layoutModeSelection.setVisibility(View.GONE);
        binding.layoutAiModes.setVisibility(View.VISIBLE);
        binding.textTitle.setText("Chơi với máy");
        binding.textScore.setVisibility(View.GONE);
    }

    private void showAiQuizLobby() {
        View lobby = getLayoutInflater().inflate(R.layout.layout_game_ai_quiz_lobby, binding.gameContainer, false);
        binding.gameContainer.removeAllViews();
        binding.gameContainer.setVisibility(View.VISIBLE);
        binding.gameContainer.addView(lobby);
        binding.layoutAiModes.setVisibility(View.GONE);
        binding.textTitle.setText("Đố vui cùng AI");
        binding.textScore.setVisibility(View.GONE);

        TextView textHighScore = lobby.findViewById(R.id.text_high_score);
        textHighScore.setText(String.valueOf(quizHighScore));

        RecyclerView recyclerHistory = lobby.findViewById(R.id.recycler_history);
        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        recyclerHistory.setAdapter(new QuizHistoryAdapter(quizHistory));

        lobby.findViewById(R.id.btn_start_quiz).setOnClickListener(v -> {
            if (oxfordVocab.isEmpty()) {
                Toast.makeText(this, "Đang tải dữ liệu từ vựng...", Toast.LENGTH_SHORT).show();
                loadOxfordVocab();
                return;
            }
            startAiQuiz();
        });
    }

    private void startAiQuiz() {
        score = 0;
        binding.textScore.setVisibility(View.VISIBLE);
        updateScoreUI();
        showNextQuizQuestion();
    }

    private void showNextQuizQuestion() {
        if (oxfordVocab.isEmpty()) return;
        
        currentQuizVocab = oxfordVocab.get((int) (Math.random() * oxfordVocab.size()));
        
        View view = getLayoutInflater().inflate(R.layout.layout_game_ai_quiz, binding.gameContainer, false);
        binding.gameContainer.removeAllViews();
        binding.gameContainer.addView(view);

        TextView textHint = view.findViewById(R.id.text_hint);
        TextView textWordTemplate = view.findViewById(R.id.text_word_template);
        TextView textWordLength = view.findViewById(R.id.text_word_length);
        TextView textCurrentScore = view.findViewById(R.id.text_current_score);
        com.google.android.material.textfield.TextInputEditText editAnswer = view.findViewById(R.id.edit_quiz_answer);
        ProgressBar progressTimer = view.findViewById(R.id.progress_timer);
        TextView textTimer = view.findViewById(R.id.text_timer);

        textCurrentScore.setText("Score: " + score);
        textHint.setText(currentQuizVocab.getDefinition());
        
        String word = currentQuizVocab.getWord();
        StringBuilder template = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            if (word.charAt(i) == ' ') template.append("  ");
            else template.append("_ ");
        }
        textWordTemplate.setText(template.toString().trim());
        textWordLength.setText("(" + word.length() + " letters)");

        if (quizTimer != null) quizTimer.cancel();
        quizTimer = new CountDownTimer(30000, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                int progress = (int) (millisUntilFinished / 100);
                progressTimer.setProgress(progress);
                textTimer.setText((millisUntilFinished / 1000 + 1) + "s");
            }

            @Override
            public void onFinish() {
                progressTimer.setProgress(0);
                textTimer.setText("0s");
                finishAiQuiz();
            }
        }.start();

        view.findViewById(R.id.btn_submit_quiz).setOnClickListener(v -> {
            checkQuizAnswer(editAnswer.getText().toString().trim());
        });

        editAnswer.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                checkQuizAnswer(editAnswer.getText().toString().trim());
                return true;
            }
            return false;
        });
    }

    private void checkQuizAnswer(String answer) {
        if (answer.equalsIgnoreCase(currentQuizVocab.getWord())) {
            if (quizTimer != null) quizTimer.cancel();
            score++;
            playSoundEffect(true);
            updateScoreUI();
            UiFeedback.showSnack(binding.getRoot(), "Chính xác! +1 điểm");
            new Handler(Looper.getMainLooper()).postDelayed(this::showNextQuizQuestion, 1000);
        } else {
            playSoundEffect(false);
            Toast.makeText(this, "Chưa đúng rồi, thử lại nhé!", Toast.LENGTH_SHORT).show();
        }
    }

    private void finishAiQuiz() {
        if (quizTimer != null) quizTimer.cancel();
        
        String resultMsg = "Hết giờ! Bạn đạt được " + score + " điểm.";
        boolean isNewRecord = false;
        if (score > quizHighScore && score > 0) {
            isNewRecord = true;
            quizHighScore = score;
            resultMsg = "CHÚC MỪNG! Bạn đã phá kỷ lục mới: " + score + " điểm!";
            prefs.edit().putInt("ai_quiz_high_score", quizHighScore).apply();
        }

        String timeStr = new java.text.SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(new java.util.Date());
        quizHistory.add(0, "Điểm: " + score + " - " + timeStr);
        if (quizHistory.size() > 10) quizHistory.remove(quizHistory.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (String s : quizHistory) sb.append(s).append("|");
        prefs.edit().putString("ai_quiz_history", sb.toString()).apply();

        UiFeedback.showErrorDialog(this, isNewRecord ? "Kỷ lục mới!" : "Kết thúc", resultMsg);
        showAiQuizLobby();
    }

    private void showDashboard() {
        View dashboard = getLayoutInflater().inflate(R.layout.layout_game_dashboard, binding.gameContainer, false);
        dashboard.setId(R.id.layout_game_dashboard_root);
        
        binding.gameContainer.removeAllViews();
        binding.gameContainer.setVisibility(View.VISIBLE);
        binding.gameContainer.addView(dashboard);
        binding.layoutAiModes.setVisibility(View.GONE);
        binding.textScore.setVisibility(View.GONE);

        TextView textCount = dashboard.findViewById(R.id.text_mastered_count);
        ProgressBar progress = dashboard.findViewById(R.id.progress_mastery);
        
        int total = myFlashcards.size();
        int mastered = masteredWordIds.size();
        textCount.setText(mastered + "/" + total);
        if (total > 0) progress.setProgress((mastered * 100) / total);

        dashboard.findViewById(R.id.btn_start_game).setOnClickListener(v -> startVocabChallenge());
    }

    private void startVocabChallenge() {
        List<Flashcard> unmasteredPool = new ArrayList<>();
        for (Flashcard f : myFlashcards) {
            if (!f.isMastered()) unmasteredPool.add(f);
        }

        if (unmasteredPool.size() < 4) {
            UiFeedback.showErrorDialog(this, "Thông báo", "Bạn đã hoàn thành hết từ vựng rồi!");
            return;
        }

        binding.textScore.setVisibility(View.GONE);
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
        showNextTask();
    }

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

        LinearLayout termContainer = view.findViewById(R.id.column_terms);
        LinearLayout defContainer = view.findViewById(R.id.column_definitions);

        List<Flashcard> shuffledTerms = new ArrayList<>(words);
        List<Flashcard> shuffledDefs = new ArrayList<>(words);
        Collections.shuffle(shuffledTerms);
        Collections.shuffle(shuffledDefs);

        selectedTermCard = null;
        selectedTermId = null;
        selectedDefCard = null;
        selectedDefId = null;

        for (Flashcard f : shuffledTerms) {
            MaterialCardView card = createMatchCard(f.getTerm(), f.getFirestoreId(), true);
            termContainer.addView(card);
        }
        for (Flashcard f : shuffledDefs) {
            MaterialCardView card = createMatchCard(f.getDefinition(), f.getFirestoreId(), false);
            defContainer.addView(card);
        }
    }

    private MaterialCardView createMatchCard(String text, String id, boolean isTerm) {
        MaterialCardView card = (MaterialCardView) getLayoutInflater().inflate(R.layout.item_match_card, null);
        TextView tv = card.findViewById(R.id.text_card);
        tv.setText(text);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
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
                checkMatchResult();
            }
        });
        return card;
    }

    private void checkMatchResult() {
        final MaterialCardView termCard = selectedTermCard;
        final MaterialCardView defCard = selectedDefCard;

        if (selectedTermId.equals(selectedDefId)) {
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

            boolean allDisabled = true;
            LinearLayout container = binding.gameContainer.findViewById(R.id.column_terms);
            for (int i = 0; i < container.getChildCount(); i++) {
                if (container.getChildAt(i).isEnabled()) {
                    allDisabled = false;
                    break;
                }
            }
            if (allDisabled) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    currentTaskIndex++;
                    showNextTask();
                }, 1000);
            }
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

        selectedTermCard = null; selectedTermId = null;
        selectedDefCard = null; selectedDefId = null;
    }

    private void checkResult(Flashcard word, String answer, String correct, View view) {
        boolean isCorrect = answer.equalsIgnoreCase(correct);
        playSoundEffect(isCorrect);
        
        if (isCorrect) {
            if (view instanceof MaterialButton) {
                ((MaterialButton) view).setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.success)));
                ((MaterialButton) view).setTextColor(ContextCompat.getColor(this, R.color.white));
            } else if (view instanceof TextView) {
                view.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.success)));
                ((TextView) view).setTextColor(ContextCompat.getColor(this, R.color.white));
            }
            UiFeedback.showSnack(binding.getRoot(), "Chính xác!");
        } else {
            wordErrorCount.put(word.getFirestoreId(), wordErrorCount.getOrDefault(word.getFirestoreId(), 0) + 1);
            if (view instanceof MaterialButton) {
                ((MaterialButton) view).setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.error)));
                ((MaterialButton) view).setTextColor(ContextCompat.getColor(this, R.color.white));
            }
            Toast.makeText(this, "Sai rồi! Đáp án: " + correct, Toast.LENGTH_SHORT).show();
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            currentTaskIndex++;
            showNextTask();
        }, 1200);
    }

    private void finishVocabChallenge() {
        databaseExecutor.execute(() -> {
            for (Flashcard f : gamePool) {
                if (!wordErrorCount.containsKey(f.getFirestoreId())) {
                    f.setMastered(true);
                    repository.updateFlashcard(f);
                }
            }
            
            runOnUiThread(() -> {
                binding.gameContainer.setVisibility(View.GONE);
                binding.layoutAiModes.setVisibility(View.VISIBLE);
                UiFeedback.showErrorDialog(this, "Hoàn thành", "Tuyệt vời! Bạn đã hoàn thành các thử thách.");
                initData();
            });
        });
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
        }
    }

    private void updateScoreUI() {
        binding.textScore.setText("Điểm: " + score);
    }

    private void handleBack() {
        if (quizTimer != null) quizTimer.cancel();
        binding.textScore.setVisibility(View.GONE);
        
        if (binding.gameContainer.getVisibility() == View.VISIBLE) {
            binding.gameContainer.setVisibility(View.GONE);
            binding.layoutAiModes.setVisibility(View.VISIBLE);
            binding.textTitle.setText("Chơi với máy");
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
        if (quizTimer != null) quizTimer.cancel();
        databaseExecutor.shutdown();
        super.onDestroy();
    }

    private static class QuizHistoryAdapter extends RecyclerView.Adapter<QuizHistoryAdapter.ViewHolder> {
        private final List<String> history;

        public QuizHistoryAdapter(List<String> history) {
            this.history = history;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            tv.setPadding(32, 16, 32, 16);
            tv.setTextSize(16);
            tv.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.text_secondary));
            return new ViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.textView.setText(history.get(position));
        }

        @Override
        public int getItemCount() { return history.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            ViewHolder(View itemView) {
                super(itemView);
                textView = (TextView) itemView;
            }
        }
    }
}
