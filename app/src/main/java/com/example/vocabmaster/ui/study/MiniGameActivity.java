package com.example.vocabmaster.ui.study;

import android.content.res.ColorStateList;
import android.media.MediaPlayer;
import android.os.Bundle;
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

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.data.repository.CourseRepository;
import com.example.vocabmaster.databinding.ActivityMiniGameBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

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
                
                // Nếu đang ở màn hình Dashboard thì cập nhật lại UI
                updateDashboardUIIfVisible();
            }
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
    }

    private void showAiModes() {
        binding.layoutModeSelection.setVisibility(View.GONE);
        binding.layoutAiModes.setVisibility(View.VISIBLE);
        binding.textTitle.setText("Chơi với máy");
    }

    private void showDashboard() {
        View dashboard = getLayoutInflater().inflate(R.layout.layout_game_dashboard, binding.gameContainer, false);
        // Gán ID cho root của layout mới inflate để dễ kiểm tra ở updateDashboardUIIfVisible
        dashboard.setId(R.id.layout_game_dashboard_root);
        
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
            // Auto speak after a short delay
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
            score += 5;
            updateScoreUI();

            // Check if all matched
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
            score += 10;
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

        updateScoreUI();
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
            
            // Sau khi update xong database, quay về UI thread để hiển thị thông báo
            runOnUiThread(() -> {
                binding.gameContainer.setVisibility(View.GONE);
                binding.layoutAiModes.setVisibility(View.VISIBLE);
                UiFeedback.showErrorDialog(this, "Hoàn thành", "Tuyệt vời! Bạn nhận được " + score + " XP");
                
                // Cập nhật lại danh sách local để UI Dashboard đồng bộ
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
            Log.d(TAG, "Speaking: " + text);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vocab_speak_" + System.currentTimeMillis());
        } else {
            Log.w(TAG, "TTS not ready. Initializing...");
            // Don't re-init here if already initializing, but we'll try to use TTS if available
            if (tts == null) initTTS();
            
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isTtsReady && tts != null) {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vocab_speak_retry");
                }
            }, 1000);
        }
    }

    private void updateScoreUI() {
        binding.textScore.setText("XP: " + score);
    }

    private void handleBack() {
        if (binding.gameContainer.getVisibility() == View.VISIBLE) {
            binding.gameContainer.setVisibility(View.GONE);
            binding.layoutAiModes.setVisibility(View.VISIBLE);
        } else if (binding.layoutAiModes.getVisibility() == View.VISIBLE) {
            binding.layoutAiModes.setVisibility(View.GONE);
            binding.layoutModeSelection.setVisibility(View.VISIBLE);
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
        databaseExecutor.shutdown();
        super.onDestroy();
    }
}
