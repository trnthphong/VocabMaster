package com.example.vocabmaster.ui.study;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Challenge;
import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.data.repository.CourseRepository;
import com.example.vocabmaster.data.repository.StudyPlanRepository;
import com.example.vocabmaster.databinding.ActivityStudyBinding;
import com.example.vocabmaster.databinding.LayoutFlashcardTopicBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class StudyActivity extends AppCompatActivity {
    private static final String TAG = "StudyActivity";
    private static final int TARGET_PRACTICE_CHALLENGES = 12;
    private ActivityStudyBinding binding;
    private LayoutFlashcardTopicBinding topicBinding;
    private FirebaseFirestore db;
    private String lessonId;
    private String courseId;
    private String wordId;
    private List<Challenge> challenges = new ArrayList<>();
    private int currentChallengeIndex = 0;
    private int correctAnswers = 0;
    private int wrongAnswers = 0;
    private Vocabulary currentVocab;
    private CourseRepository repository;
    private StudyPlanRepository studyPlanRepository;
    private MediaPlayer mediaPlayer;
    private TextToSpeech tts;
    private boolean isTtsReady = false;
    private boolean lessonCompletionSaved = false;
    private Set<String> introducedWords = new HashSet<>();
    
    private boolean isFlashcardMode = false;
    private boolean useTopicLayout = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        repository = new CourseRepository(getApplication());
        studyPlanRepository = new StudyPlanRepository(getApplication());
        mediaPlayer = new MediaPlayer();
        initTextToSpeech();
        
        lessonId = getIntent().getStringExtra("lesson_id");
        courseId = getIntent().getStringExtra("course_id");
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
            // Hide bottom controls for challenge mode
            binding.bottomControls.setVisibility(View.GONE);
        }

        setupListeners();
    }

    private void setupFlashcardOnlyUI() {
        binding.layoutStats.setVisibility(View.GONE);
        
        if (useTopicLayout) {
            binding.bottomControls.setVisibility(View.VISIBLE);
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
            binding.bottomControls.setVisibility(View.VISIBLE);
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
                        loadIntroducedWordsThenDisplay();
                    } else {
                        createFallbackChallengesForLesson();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading challenges", e);
                    Toast.makeText(this, "Lỗi khi tải dữ liệu", Toast.LENGTH_SHORT).show();
                });
    }

    private void initTextToSpeech() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });
    }

    private void loadIntroducedWordsThenDisplay() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            normalizePracticeChallenges();
            ensureEnoughPracticeChallenges();
            displayChallenge();
            return;
        }

        db.collection("challengeProgress")
                .whereEqualTo("userId", uid)
                .whereEqualTo("completed", true)
                .whereEqualTo("type", "INTRO")
                .get()
                .addOnCompleteListener(task -> {
                    introducedWords.clear();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult()) {
                            String answerText = doc.getString("answerText");
                            if (answerText != null && !answerText.trim().isEmpty()) {
                                introducedWords.add(answerText.trim().toLowerCase(Locale.US));
                            }
                        }
                    }
                    normalizePracticeChallenges();
                    ensureEnoughPracticeChallenges();
                    displayChallenge();
                });
    }

    private void createFallbackChallengesForLesson() {
        String title = getIntent().getStringExtra("lesson_title");
        if (title == null || title.trim().isEmpty()) title = "this lesson";

        challenges = new ArrayList<>();
        List<String> keyWords = Arrays.asList("learn", "practice", "listen", "speak", "review");
        challenges.add(buildFallbackChallenge("INTRO", 1, buildSimpleQuestion("INTRO", "learn"), keyWords, "learn"));
        challenges.add(buildFallbackChallenge("INTRO", 2, buildSimpleQuestion("INTRO", "practice"), keyWords, "practice"));
        challenges.add(buildFallbackChallenge("INTRO", 3, buildSimpleQuestion("INTRO", "listen"), keyWords, "listen"));
        challenges.add(buildFallbackChallenge("SELECT", 4, "Chọn từ có nghĩa là \"học\".", keyWords, "learn"));
        challenges.add(buildFallbackChallenge("TYPE", 5, "Viết từ có nghĩa là \"luyện tập\".", keyWords, "practice"));
        challenges.add(buildFallbackChallenge("LISTEN", 6, "Nghe và chọn từ đúng.", keyWords, "listen"));
        challenges.add(buildArrangeChallenge(7, "I can practice in " + title + "."));
        challenges.add(buildFallbackChallenge("SELECT", 8, "Chọn từ có nghĩa là \"nói\".", keyWords, "speak"));
        challenges.add(buildFallbackChallenge("TYPE", 9, "Viết từ có nghĩa là \"ôn lại\".", keyWords, "review"));
        challenges.add(buildFallbackChallenge("LISTEN", 10, "Nghe và chọn từ đúng.", keyWords, "review"));

        for (Challenge challenge : challenges) {
            String challengeId = db.collection("challenges").document().getId();
            challenge.setId(challengeId);
            challenge.setLessonId(lessonId);
            db.collection("challenges").document(challengeId).set(challenge);
        }

        Toast.makeText(this, "Đã tạo thử thách cho bài học", Toast.LENGTH_SHORT).show();
        displayChallenge();
    }

    private Challenge buildFallbackChallenge(String type, int orderNum, String question, List<String> optionTexts, String correctText) {
        Challenge challenge = new Challenge();
        challenge.setType(type);
        challenge.setOrderNum(orderNum);
        challenge.setQuestion(question);
        challenge.setOptions(buildFallbackOptions(optionTexts, correctText));
        return challenge;
    }

    private Challenge buildArrangeChallenge(int orderNum, String sentence) {
        Challenge challenge = new Challenge();
        challenge.setType("ARRANGE");
        challenge.setOrderNum(orderNum);
        challenge.setQuestion("Nghe câu rồi sắp xếp các từ.");

        Challenge.ChallengeOption option = new Challenge.ChallengeOption();
        option.setText(sentence);
        option.setCorrect(true);
        challenge.setOptions(Collections.singletonList(option));
        return challenge;
    }

    private List<Challenge.ChallengeOption> buildFallbackOptions(String correctText) {
        return buildFallbackOptions(Arrays.asList(correctText, "skip", "delete", "forget"), correctText);
    }

    private List<Challenge.ChallengeOption> buildFallbackOptions(List<String> sourceOptions, String correctText) {
        List<String> optionTexts = new ArrayList<>(sourceOptions);
        if (!optionTexts.contains(correctText)) optionTexts.add(correctText);
        Collections.shuffle(optionTexts);

        List<Challenge.ChallengeOption> options = new ArrayList<>();
        for (String text : optionTexts) {
            Challenge.ChallengeOption option = new Challenge.ChallengeOption();
            option.setText(text);
            option.setCorrect(text.equals(correctText));
            options.add(option);
        }
        return options;
    }

    private void normalizePracticeChallenges() {
        if (challenges == null || challenges.isEmpty()) return;

        List<String> words = extractChallengeWords();
        List<Challenge> intros = new ArrayList<>();
        List<Challenge> practices = new ArrayList<>();
        boolean hasSelect = false;
        boolean hasType = false;
        boolean hasListen = false;
        boolean hasArrange = false;
        boolean hasMatch = false;
        int nextOrder = 1;

        for (Challenge challenge : challenges) {
            String type = challenge.getType() == null ? "SELECT" : challenge.getType().trim().toUpperCase(Locale.US);
            if ("INTRO".equals(type)) {
                String introWord = getCorrectText(challenge);
                if (introWord.trim().isEmpty()) introWord = words.get(intros.size() % words.size());
                String key = introWord.trim().toLowerCase(Locale.US);
                if (introducedWords.contains(key)) continue;
                challenge.setType("INTRO");
                challenge.setQuestion(buildSimpleQuestion("INTRO", introWord));
                intros.add(challenge);
                introducedWords.add(key);
                continue;
            }
            if ("SPEAK".equals(type)) continue;
            if ("INPUT".equals(type) || "FORM".equals(type)) type = "TYPE";
            if ("REVIEW".equals(type)) type = "SELECT";
            if (!"SELECT".equals(type) && !"TYPE".equals(type) && !"LISTEN".equals(type)
                    && !"ARRANGE".equals(type) && !"MATCH".equals(type)) {
                type = "SELECT";
            }

            String correctText = getCorrectText(challenge);
            if (correctText.trim().isEmpty()) correctText = words.get((nextOrder - 1) % words.size());
            if ("ARRANGE".equals(type) && !correctText.contains(" ")) {
                setSingleCorrectOption(challenge, "I can use " + correctText + ".");
            }

            challenge.setType(type);
            challenge.setQuestion(buildSimpleQuestion(type, correctText));
            practices.add(challenge);

            hasSelect |= "SELECT".equals(type);
            hasType |= "TYPE".equals(type);
            hasListen |= "LISTEN".equals(type);
            hasArrange |= "ARRANGE".equals(type);
            hasMatch |= "MATCH".equals(type);
            if (practices.size() >= TARGET_PRACTICE_CHALLENGES) break;
        }

        for (String word : words) {
            if (intros.size() >= 3) break;
            String key = word.trim().toLowerCase(Locale.US);
            if (introducedWords.contains(key)) continue;
            intros.add(buildFallbackChallenge("INTRO", 0, buildSimpleQuestion("INTRO", word), words, word));
            introducedWords.add(key);
        }

        while (practices.size() < TARGET_PRACTICE_CHALLENGES && (!hasSelect || !hasType || !hasListen || !hasArrange || !hasMatch)) {
            String word = words.get(practices.size() % words.size());
            Challenge extra;
            if (!hasSelect) {
                extra = buildFallbackChallenge("SELECT", nextOrder++, buildSimpleQuestion("SELECT", word), words, word);
                hasSelect = true;
            } else if (!hasType) {
                extra = buildFallbackChallenge("TYPE", nextOrder++, buildSimpleQuestion("TYPE", word), words, word);
                hasType = true;
            } else if (!hasListen) {
                extra = buildFallbackChallenge("LISTEN", nextOrder++, buildSimpleQuestion("LISTEN", word), words, word);
                hasListen = true;
            } else if (!hasArrange) {
                extra = buildArrangeChallenge(nextOrder++, "I can use " + word + ".");
                hasArrange = true;
            } else {
                extra = buildMatchChallenge(nextOrder++, words);
                hasMatch = true;
            }
            extra.setLessonId(lessonId);
            practices.add(extra);
        }

        while (practices.size() < TARGET_PRACTICE_CHALLENGES) {
            String word = words.get(practices.size() % words.size());
            String type;
            switch (practices.size() % 4) {
                case 0: type = "SELECT"; break;
                case 1: type = "TYPE"; break;
                case 2: type = "LISTEN"; break;
                default: type = "ARRANGE"; break;
            }
            Challenge extra = "ARRANGE".equals(type)
                    ? buildArrangeChallenge(nextOrder++, "I can use " + word + ".")
                    : buildFallbackChallenge(type, nextOrder++, buildSimpleQuestion(type, word), words, word);
            extra.setLessonId(lessonId);
            practices.add(extra);
        }

        List<Challenge> normalized = new ArrayList<>();
        normalized.addAll(intros.subList(0, Math.min(3, intros.size())));
        normalized.addAll(practices);
        nextOrder = 1;
        for (Challenge challenge : normalized) {
            challenge.setOrderNum(nextOrder++);
        }
        challenges = normalized;
    }

    private Challenge buildMatchChallenge(int orderNum, List<String> words) {
        Challenge challenge = new Challenge();
        challenge.setType("MATCH");
        challenge.setOrderNum(orderNum);
        challenge.setQuestion("Nối từ với nghĩa tiếng Việt.");
        challenge.setOptions(buildFallbackOptions(words, words.get(0)));
        for (Challenge.ChallengeOption option : challenge.getOptions()) {
            option.setCorrect(true);
        }
        return challenge;
    }

    private void setSingleCorrectOption(Challenge challenge, String text) {
        Challenge.ChallengeOption option = new Challenge.ChallengeOption();
        option.setText(text);
        option.setCorrect(true);
        challenge.setOptions(Collections.singletonList(option));
    }

    private String buildSimpleQuestion(String type, String correctText) {
        String meaning = getVietnameseMeaning(correctText);
        switch (type) {
            case "INTRO":
                return "Từ mới: " + correctText + " = " + meaning + ".";
            case "TYPE":
                return "Viết từ có nghĩa là \"" + meaning + "\".";
            case "LISTEN":
                return "Nghe và chọn từ đúng.";
            case "ARRANGE":
                return "Nghe câu rồi sắp xếp các từ.";
            case "MATCH":
                return "Nối từ với nghĩa tiếng Việt.";
            default:
                return "Chọn từ có nghĩa là \"" + meaning + "\".";
        }
    }

    private void ensureEnoughPracticeChallenges() {
        if (challenges == null || countPracticeChallenges() >= TARGET_PRACTICE_CHALLENGES) return;

        List<String> words = extractChallengeWords();
        int nextOrder = 1;
        for (Challenge challenge : challenges) {
            nextOrder = Math.max(nextOrder, challenge.getOrderNum() + 1);
        }

        while (countPracticeChallenges() < TARGET_PRACTICE_CHALLENGES) {
            int index = countPracticeChallenges();
            String word = words.get(index % words.size());
            String type;
            switch (index % 4) {
                case 0: type = "LISTEN"; break;
                case 1: type = "TYPE"; break;
                case 2: type = "ARRANGE"; break;
                default: type = "SELECT"; break;
            }
            Challenge challenge = "ARRANGE".equals(type)
                    ? buildArrangeChallenge(nextOrder++, "I can use " + word + ".")
                    : buildFallbackChallenge(type, nextOrder++, "LISTEN".equals(type)
                    ? "Nghe và chọn từ đúng."
                    : ("TYPE".equals(type)
                    ? "Viết từ có nghĩa là \"" + getVietnameseMeaning(word) + "\"."
                    : "Chọn từ có nghĩa là \"" + getVietnameseMeaning(word) + "\"."), words, word);

            String challengeId = db.collection("challenges").document().getId();
            challenge.setId(challengeId);
            challenge.setLessonId(lessonId);
            challenges.add(challenge);
            db.collection("challenges").document(challengeId).set(challenge);
        }
    }

    private int countPracticeChallenges() {
        int count = 0;
        if (challenges == null) return 0;
        for (Challenge challenge : challenges) {
            String type = challenge.getType() == null ? "" : challenge.getType().trim().toUpperCase(Locale.US);
            if (!"INTRO".equals(type)) count++;
        }
        return count;
    }

    private List<String> extractChallengeWords() {
        List<String> words = new ArrayList<>();
        if (challenges != null) {
            for (Challenge challenge : challenges) {
                if (challenge.getOptions() == null) continue;
                for (Challenge.ChallengeOption option : challenge.getOptions()) {
                    String text = option.getText();
                    if (text == null || text.trim().isEmpty()) continue;
                    if ("Tôi đã nói".equals(text) || "I said it".equals(text)) continue;
                    if (!words.contains(text)) words.add(text);
                }
            }
        }
        if (words.isEmpty()) words.addAll(Arrays.asList("learn", "practice", "listen", "speak", "review"));
        return words;
    }

    private void displayChallenge() {
        if (currentChallengeIndex >= challenges.size()) {
            markLessonCompleted();
            int totalChallenges = Math.max(1, challenges.size());
            int accuracy = Math.round((correctAnswers * 100f) / totalChallenges);
            int stars = accuracy >= 100 ? 3 : (accuracy >= 75 ? 2 : 1);
            
            Intent intent = new Intent(this, StudySummaryActivity.class);
            intent.putExtra("xp", challenges.size() * 10);
            intent.putExtra("lesson_id", lessonId);
            intent.putExtra("course_id", courseId);
            intent.putExtra("lesson_title", getIntent().getStringExtra("lesson_title"));
            intent.putExtra("total_challenges", challenges.size());
            intent.putExtra("correct_challenges", correctAnswers);
            intent.putExtra("stars", stars);
            
            if (getIntent().hasExtra("next_lesson_id")) {
                intent.putExtra("next_lesson_id", getIntent().getStringExtra("next_lesson_id"));
                intent.putExtra("next_lesson_title", getIntent().getStringExtra("next_lesson_title"));
            }
            
            startActivity(intent);
            finish();
            return;
        }

        binding.bottomControls.setVisibility(View.GONE);

        Challenge challenge = challenges.get(currentChallengeIndex);
        binding.cardFlashcard.setVisibility(View.GONE);
        binding.dynamicTaskLayout.setVisibility(View.VISIBLE);
        binding.dynamicTaskLayout.removeAllViews();

        int progress = (int) (((float) (currentChallengeIndex + 1) / challenges.size()) * 100);
        binding.studyProgress.setProgress(progress);

        View challengeView = getLayoutInflater().inflate(R.layout.layout_challenge_select, binding.dynamicTaskLayout, false);
        TextView textQuestion = challengeView.findViewById(R.id.text_question);
        GridLayout optionsContainer = challengeView.findViewById(R.id.options_container);
        LinearLayout feedbackContainer = challengeView.findViewById(R.id.feedback_container);

        textQuestion.setText(challenge.getQuestion());

        String type = challenge.getType() == null ? "SELECT" : challenge.getType();
        if ("INTRO".equals(type)) {
            renderIntroChallenge(challenge, optionsContainer, feedbackContainer);
        } else if ("LISTEN".equals(type)) {
            renderListeningChallenge(challenge, optionsContainer, feedbackContainer);
        } else if ("MATCH".equals(type)) {
            renderMatchChallenge(challenge, optionsContainer, feedbackContainer);
        } else if ("ARRANGE".equals(type)) {
            renderArrangeChallenge(challenge, optionsContainer, feedbackContainer);
        } else if ("SPEAK".equals(type)) {
            renderSpeakChallenge(challenge, optionsContainer, feedbackContainer);
        } else if ("TYPE".equals(type) || "INPUT".equals(type) || "FORM".equals(type)) {
            renderTypeChallenge(challenge, optionsContainer, feedbackContainer);
        } else {
            renderChoiceChallenge(challenge, optionsContainer, feedbackContainer);
        }

        binding.dynamicTaskLayout.addView(challengeView);
    }

    private void renderIntroChallenge(Challenge challenge, GridLayout container, LinearLayout feedbackContainer) {
        String word = getCorrectText(challenge);
        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.word);
        image.setBackground(makeRoundedBg(Color.WHITE, getColor(R.color.brand_primary_light), 2));
        image.setPadding(28, 28, 28, 28);
        GridLayout.LayoutParams imageParams = new GridLayout.LayoutParams();
        imageParams.columnSpec = GridLayout.spec(0, 2, 1f);
        imageParams.width = 180;
        imageParams.height = 180;
        imageParams.setGravity(Gravity.CENTER);
        imageParams.setMargins(10, 10, 10, 12);
        image.setLayoutParams(imageParams);
        container.addView(image);

        TextView card = makeLargeCard(word);
        
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.columnSpec = GridLayout.spec(0, 2, 1f);
        params.setMargins(10, 10, 10, 10);
        card.setLayoutParams(params);
        container.addView(card);

        TextView phonetic = makeFeedbackText(getStandardPhonetic(word), true);
        GridLayout.LayoutParams phoneticParams = new GridLayout.LayoutParams();
        phoneticParams.columnSpec = GridLayout.spec(0, 2, 1f);
        phoneticParams.setMargins(10, 0, 10, 10);
        phonetic.setLayoutParams(phoneticParams);
        container.addView(phonetic);

        Button button = makeOptionButton("Đã hiểu");
        TextView meaning = makeFeedbackText(getVietnameseMeaning(word), true);
        GridLayout.LayoutParams meaningParams = new GridLayout.LayoutParams();
        meaningParams.columnSpec = GridLayout.spec(0, 2, 1f);
        meaningParams.setMargins(10, 4, 10, 12);
        meaning.setLayoutParams(meaningParams);
        container.addView(meaning);

        ImageButton listen = new ImageButton(this);
        listen.setImageResource(R.drawable.volume);
        listen.setBackgroundColor(Color.TRANSPARENT);
        listen.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        listen.setAdjustViewBounds(true);
        listen.setPadding(dp(10), dp(10), dp(10), dp(10));
        listen.setOnClickListener(v -> speakText(word));
        GridLayout.LayoutParams listenParams = new GridLayout.LayoutParams();
        listenParams.columnSpec = GridLayout.spec(0, 2, 1f);
        listenParams.width = 100;
        listenParams.height = 100;
        listenParams.setGravity(Gravity.CENTER);
        listenParams.setMargins(10, 4, 10, 12);
        listen.setLayoutParams(listenParams);
        container.addView(listen);
        new Handler(Looper.getMainLooper()).postDelayed(() -> speakText(word), 250);

        GridLayout.LayoutParams btnParams = new GridLayout.LayoutParams();
        btnParams.columnSpec = GridLayout.spec(0, 2, 1f);
        btnParams.setMargins(10, 10, 10, 10);
        button.setLayoutParams(btnParams);
        button.setOnClickListener(v -> handleCorrectAnswer(challenge, container, feedbackContainer));
        container.addView(button);
    }

    private void renderChoiceChallenge(Challenge challenge, GridLayout container, LinearLayout feedbackContainer) {
        List<Challenge.ChallengeOption> options = challenge.getOptions();
        if (options == null) return;

        for (Challenge.ChallengeOption option : options) {
            Button button = makeOptionButton(option.getText());
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(76);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dp(10), dp(10), dp(10), dp(10));
            button.setLayoutParams(params);
            
            button.setOnClickListener(v -> {
                if (option.isCorrect()) {
                    applyCorrectButtonStyle(button);
                    handleCorrectAnswer(challenge, container, feedbackContainer);
                } else {
                    handleWrongAnswer(container, feedbackContainer, getCorrectText(challenge));
                }
            });
            container.addView(button);
        }
    }

    private void renderListeningChallenge(Challenge challenge, GridLayout container, LinearLayout feedbackContainer) {
        ImageButton replay = new ImageButton(this);
        replay.setImageResource(R.drawable.volume);
        replay.setBackgroundColor(Color.TRANSPARENT);
        replay.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        replay.setAdjustViewBounds(true);
        replay.setPadding(dp(12), dp(12), dp(12), dp(12));
        replay.setOnClickListener(v -> speakChallengeAnswer(challenge));

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.columnSpec = GridLayout.spec(0, 2, 1f);
        params.width = 150;
        params.height = 150;
        params.setMargins(0, 0, 0, 20);
        params.setGravity(Gravity.CENTER);
        replay.setLayoutParams(params);
        
        container.addView(replay);

        new Handler(Looper.getMainLooper()).postDelayed(() -> speakChallengeAnswer(challenge), 350);
        renderChoiceChallenge(challenge, container, feedbackContainer);
    }

    private void renderSpeakChallenge(Challenge challenge, GridLayout container, LinearLayout feedbackContainer) {
        TextView hint = makeFeedbackText("Nói to câu trên, rồi xác nhận khi bạn đã nói xong.", true);
        GridLayout.LayoutParams hintParams = new GridLayout.LayoutParams();
        hintParams.columnSpec = GridLayout.spec(0, 2, 1f);
        hint.setLayoutParams(hintParams);
        container.addView(hint);

        Button confirm = makeOptionButton(getCorrectText(challenge));
        GridLayout.LayoutParams confirmParams = new GridLayout.LayoutParams();
        confirmParams.columnSpec = GridLayout.spec(0, 2, 1f);
        confirm.setLayoutParams(confirmParams);
        confirm.setOnClickListener(v -> handleCorrectAnswer(challenge, container, feedbackContainer));
        container.addView(confirm);
    }

    private void renderTypeChallenge(Challenge challenge, GridLayout container, LinearLayout feedbackContainer) {
        String correctText = getCorrectText(challenge);
        TextView hint = makeFeedbackText("Nghĩa: " + getVietnameseMeaning(correctText), true);
        GridLayout.LayoutParams hintParams = new GridLayout.LayoutParams();
        hintParams.columnSpec = GridLayout.spec(0, 2, 1f);
        hint.setLayoutParams(hintParams);
        container.addView(hint);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setTextSize(22);
        input.setGravity(Gravity.CENTER);
        input.setHint("Nhập từ");
        input.setPadding(24, 24, 24, 24);
        input.setBackground(makeRoundedBg(Color.WHITE, getColor(R.color.brand_primary_light), 2));
        GridLayout.LayoutParams inputParams = new GridLayout.LayoutParams();
        inputParams.columnSpec = GridLayout.spec(0, 2, 1f);
        inputParams.width = GridLayout.LayoutParams.MATCH_PARENT;
        inputParams.setMargins(10, 14, 10, 14);
        input.setLayoutParams(inputParams);
        container.addView(input);

        Button submit = makeOptionButton("Kiểm tra");
        GridLayout.LayoutParams submitParams = new GridLayout.LayoutParams();
        submitParams.columnSpec = GridLayout.spec(0, 2, 1f);
        submitParams.setMargins(10, 8, 10, 8);
        submit.setLayoutParams(submitParams);
        submit.setOnClickListener(v -> {
            String answer = input.getText().toString().trim();
            if (answer.equalsIgnoreCase(correctText.trim())) {
                handleCorrectAnswer(challenge, container, feedbackContainer);
            } else {
                handleWrongAnswer(container, feedbackContainer, correctText);
            }
        });
        container.addView(submit);
    }

    private void renderMatchChallenge(Challenge challenge, GridLayout container, LinearLayout feedbackContainer) {
        List<String> words = extractOptionTexts(challenge);
        if (words.isEmpty()) {
            renderChoiceChallenge(challenge, container, feedbackContainer);
            return;
        }

        TextView status = makeFeedbackText("Chọn một từ, rồi chọn nghĩa tương ứng.", true);
        GridLayout.LayoutParams statusParams = new GridLayout.LayoutParams();
        statusParams.columnSpec = GridLayout.spec(0, 2, 1f);
        status.setLayoutParams(statusParams);
        status.setVisibility(View.GONE);
        container.addView(status);

        final String[] selectedWord = {null};
        final Button[] selectedWordButton = {null};
        Set<String> matched = new HashSet<>();

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setGravity(Gravity.TOP);
        GridLayout.LayoutParams columnsParams = new GridLayout.LayoutParams();
        columnsParams.columnSpec = GridLayout.spec(0, 2, 1f);
        columnsParams.width = GridLayout.LayoutParams.MATCH_PARENT;
        columnsParams.setMargins(0, dp(8), 0, dp(8));
        columns.setLayoutParams(columnsParams);

        LinearLayout wordColumn = new LinearLayout(this);
        wordColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout meaningColumn = new LinearLayout(this);
        meaningColumn.setOrientation(LinearLayout.VERTICAL);
        columns.addView(wordColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        columns.addView(meaningColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        container.addView(columns);

        for (String word : words) {
            Button wordButton = makeOptionButton(word);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(72));
            params.setMargins(dp(8), dp(8), dp(8), dp(8));
            wordButton.setLayoutParams(params);
            wordButton.setOnClickListener(v -> {
                if (selectedWordButton[0] != null && selectedWordButton[0].getVisibility() == View.VISIBLE) {
                    applyDefaultButtonStyle(selectedWordButton[0]);
                }
                selectedWord[0] = word;
                selectedWordButton[0] = wordButton;
                applySelectedButtonStyle(wordButton);
            });
            wordColumn.addView(wordButton);
        }

        List<String> meanings = new ArrayList<>();
        for (String word : words) meanings.add(getVietnameseMeaning(word));
        Collections.shuffle(meanings);
        for (String meaning : meanings) {
            Button meaningButton = makeOptionButton(meaning);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(72));
            params.setMargins(dp(8), dp(8), dp(8), dp(8));
            meaningButton.setLayoutParams(params);
            meaningButton.setOnClickListener(v -> {
                if (selectedWord[0] == null) {
                    Toast.makeText(this, "Chọn từ trước nhé.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (meaning.equals(getVietnameseMeaning(selectedWord[0]))) {
                    applyCorrectButtonStyle(meaningButton);
                    if (selectedWordButton[0] != null) applyCorrectButtonStyle(selectedWordButton[0]);
                    matched.add(selectedWord[0]);
                    if (selectedWordButton[0] != null) selectedWordButton[0].setVisibility(View.GONE);
                    meaningButton.setVisibility(View.GONE);
                    selectedWord[0] = null;
                    selectedWordButton[0] = null;
                    if (matched.size() == words.size()) {
                        handleCorrectAnswer(challenge, container, feedbackContainer);
                    }
                } else {
                    handleWrongAnswer(container, feedbackContainer, selectedWord[0] + " = " + getVietnameseMeaning(selectedWord[0]));
                }
            });
            meaningColumn.addView(meaningButton);
        }
    }

    private void renderArrangeChallenge(Challenge challenge, GridLayout container, LinearLayout feedbackContainer) {
        String sentence = getCorrectText(challenge);
        if (sentence == null || sentence.trim().isEmpty()) sentence = "I can use " + firstOptionText(challenge) + ".";

        ImageButton replay = new ImageButton(this);
        replay.setImageResource(R.drawable.volume);
        replay.setBackgroundColor(Color.TRANSPARENT);
        replay.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        replay.setAdjustViewBounds(true);
        replay.setPadding(dp(10), dp(10), dp(10), dp(10));
        GridLayout.LayoutParams replayParams = new GridLayout.LayoutParams();
        replayParams.columnSpec = GridLayout.spec(0, 2, 1f);
        replayParams.width = dp(88);
        replayParams.height = dp(88);
        replayParams.setGravity(Gravity.CENTER);
        replay.setLayoutParams(replayParams);
        String finalSentence = sentence;
        replay.setOnClickListener(v -> speakText(finalSentence));
        container.addView(replay);
        new Handler(Looper.getMainLooper()).postDelayed(() -> speakText(finalSentence), 350);

        GridLayout answerBox = new GridLayout(this);
        answerBox.setColumnCount(3);
        answerBox.setMinimumHeight(dp(76));
        answerBox.setPadding(dp(8), dp(8), dp(8), dp(8));
        answerBox.setBackground(makeRoundedBg(Color.WHITE, getColor(R.color.brand_primary), 2));
        GridLayout.LayoutParams answerParams = new GridLayout.LayoutParams();
        answerParams.columnSpec = GridLayout.spec(0, 2, 1f);
        answerParams.width = GridLayout.LayoutParams.MATCH_PARENT;
        answerParams.height = GridLayout.LayoutParams.WRAP_CONTENT;
        answerParams.setMargins(dp(10), dp(14), dp(10), dp(18));
        answerBox.setLayoutParams(answerParams);
        container.addView(answerBox);

        GridLayout choicesGrid = new GridLayout(this);
        choicesGrid.setColumnCount(3);
        choicesGrid.setPadding(dp(2), dp(2), dp(2), dp(2));
        GridLayout.LayoutParams choicesParams = new GridLayout.LayoutParams();
        choicesParams.columnSpec = GridLayout.spec(0, 2, 1f);
        choicesParams.width = GridLayout.LayoutParams.MATCH_PARENT;
        choicesParams.setMargins(dp(4), dp(4), dp(4), dp(4));
        choicesGrid.setLayoutParams(choicesParams);
        container.addView(choicesGrid);

        List<String> pieces = new ArrayList<>(Arrays.asList(sentence.replace(".", "").split(" ")));
        Collections.shuffle(pieces);
        List<String> selected = new ArrayList<>();
        Map<Button, String> selectedChips = new LinkedHashMap<>();
        for (int i = 0; i < pieces.size(); i++) {
            String piece = pieces.get(i);
            Button chip = makeChipButton(piece);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = GridLayout.LayoutParams.WRAP_CONTENT;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED);
            params.setMargins(dp(6), dp(6), dp(6), dp(6));
            chip.setLayoutParams(params);
            chip.setOnClickListener(v -> {
                selected.add(piece);
                chip.setVisibility(View.GONE);

                Button answerChip = makeChipButton(piece);
                GridLayout.LayoutParams answerChipParams = new GridLayout.LayoutParams();
                answerChipParams.width = GridLayout.LayoutParams.WRAP_CONTENT;
                answerChipParams.height = GridLayout.LayoutParams.WRAP_CONTENT;
                answerChipParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED);
                answerChipParams.setMargins(dp(6), dp(6), dp(6), dp(6));
                answerChip.setLayoutParams(answerChipParams);
                selectedChips.put(answerChip, piece);
                answerChip.setOnClickListener(selectedView -> {
                    String removed = selectedChips.remove(answerChip);
                    if (removed != null) selected.remove(removed);
                    answerBox.removeView(answerChip);
                    chip.setVisibility(View.VISIBLE);
                });
                answerBox.addView(answerChip);

            });
            choicesGrid.addView(chip);
        }

        Button submit = makeOptionButton("Kiểm tra");
        GridLayout.LayoutParams submitParams = new GridLayout.LayoutParams();
        submitParams.columnSpec = GridLayout.spec(0, 2, 1f);
        submitParams.width = GridLayout.LayoutParams.MATCH_PARENT;
        submitParams.height = dp(62);
        submitParams.setMargins(dp(10), dp(14), dp(10), dp(8));
        submit.setLayoutParams(submitParams);
        submit.setOnClickListener(v -> {
            if (selected.size() != pieces.size()) {
                Toast.makeText(this, "Chọn đủ các từ trong câu trước nhé.", Toast.LENGTH_SHORT).show();
                return;
            }
            String candidate = String.join(" ", selected);
            if (candidate.equals(finalSentence.replace(".", ""))) {
                for (Button selectedChip : selectedChips.keySet()) {
                    applyCorrectButtonStyle(selectedChip);
                }
                applyCorrectButtonStyle(submit);
                handleCorrectAnswer(challenge, container, feedbackContainer);
            } else {
                handleWrongAnswer(container, feedbackContainer, finalSentence);
            }
        });
        container.addView(submit);
    }

    private void handleCorrectAnswer(Challenge challenge, ViewGroup container, LinearLayout feedbackContainer) {
        correctAnswers++;
        disableInput(container);
        playFeedbackSound(true);
        UiFeedback.performHaptic(this, 10);
        showInlineFeedback(feedbackContainer, true, "Chính xác! Rất giỏi.");
        recordChallengeCompleted(challenge);
        showContinueButton(feedbackContainer);
    }

    private void handleWrongAnswer(ViewGroup container, LinearLayout feedbackContainer, String correctText) {
        wrongAnswers++;
        disableInput(container);
        playFeedbackSound(false);
        UiFeedback.performHaptic(this, 20);
        showInlineFeedback(feedbackContainer, false, "Chưa đúng rồi. Đáp án đúng:\n" + correctText);
        showContinueButton(feedbackContainer);
    }

    private void playFeedbackSound(boolean correct) {
        new Thread(() -> {
            android.media.ToneGenerator toneGenerator = null;
            try {
                toneGenerator = new android.media.ToneGenerator(
                        android.media.AudioManager.STREAM_MUSIC, 80);
                if (correct) {
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 120);
                    Thread.sleep(150);
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 90);
                    Thread.sleep(120);
                } else {
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_NACK, 300);
                    Thread.sleep(350);
                }
            } catch (Exception ignored) {
            } finally {
                if (toneGenerator != null) toneGenerator.release();
            }
        }).start();
    }

    private void disableInput(ViewGroup container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof ViewGroup) {
                disableInput((ViewGroup) child);
            }
            child.setEnabled(false);
            child.setOnClickListener(null);
        }
    }

    private void showInlineFeedback(LinearLayout container, boolean correct, String message) {
        container.removeAllViews();
        TextView feedback = makeFeedbackText(message, correct);
        container.addView(feedback);
    }

    private void showContinueButton(LinearLayout container) {
        Button next = makeOptionButton("Tiếp tục");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56));
        params.setMargins(dp(8), dp(8), dp(8), 0);
        next.setLayoutParams(params);
        next.setOnClickListener(v -> nextChallenge());
        container.addView(next);
    }

    private Button makeOptionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(17);
        button.setMinHeight(dp(64));
        button.setMinimumHeight(dp(64));
        button.setMinWidth(0);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), dp(18), dp(14), dp(18));
        applyDefaultButtonStyle(button);
        return button;
    }

    private Button makeChipButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setSingleLine(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        applyDefaultButtonStyle(button);
        return button;
    }

    private void applyDefaultButtonStyle(Button button) {
        button.setBackground(makeRoundedBg(Color.WHITE, getColor(R.color.brand_primary), 2));
        button.setTextColor(getColor(R.color.brand_primary));
    }

    private void applySelectedButtonStyle(Button button) {
        button.setBackground(makeRoundedBg(getColor(R.color.brand_primary), getColor(R.color.brand_primary), 2));
        button.setTextColor(Color.WHITE);
    }

    private void applyCorrectButtonStyle(Button button) {
        button.setBackground(makeRoundedBg(getColor(R.color.success), getColor(R.color.success), 2));
        button.setTextColor(Color.WHITE);
    }

    private TextView makeLargeCard(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(26);
        view.setTypeface(null, Typeface.BOLD);
        view.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        view.setTextColor(getColor(R.color.brand_primary));
        view.setPadding(24, 32, 24, 32);
        view.setBackground(makeRoundedBg(Color.WHITE, getColor(R.color.brand_primary_light), 2));
        return view;
    }

    private TextView makeFeedbackText(String message, boolean correct) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextSize(17);
        view.setTypeface(null, Typeface.BOLD);
        view.setTextColor(correct ? getColor(R.color.success) : getColor(R.color.error));
        view.setPadding(16, 16, 16, 16);
        view.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        view.setBackground(makeRoundedBg(Color.WHITE, correct ? getColor(R.color.success) : getColor(R.color.error), 2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(params);
        return view;
    }

    private GradientDrawable makeRoundedBg(int fillColor, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(22);
        drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private Challenge.ChallengeOption getCorrectOption(Challenge challenge) {
        if (challenge.getOptions() == null) return null;
        for (Challenge.ChallengeOption option : challenge.getOptions()) {
            if (option.isCorrect()) return option;
        }
        return challenge.getOptions().isEmpty() ? null : challenge.getOptions().get(0);
    }

    private String getCorrectText(Challenge challenge) {
        Challenge.ChallengeOption option = getCorrectOption(challenge);
        return option != null && option.getText() != null ? option.getText() : "";
    }

    private String firstOptionText(Challenge challenge) {
        if (challenge.getOptions() != null && !challenge.getOptions().isEmpty()) {
            String text = challenge.getOptions().get(0).getText();
            return text == null ? "" : text;
        }
        return "";
    }

    private List<String> extractOptionTexts(Challenge challenge) {
        List<String> words = new ArrayList<>();
        if (challenge.getOptions() == null) return words;
        for (Challenge.ChallengeOption option : challenge.getOptions()) {
            String text = option.getText();
            if (text != null && !text.trim().isEmpty() && !words.contains(text)) {
                words.add(text.trim());
            }
        }
        return words;
    }

    private void speakText(String text) {
        if (!isTtsReady || tts == null || text == null || text.trim().isEmpty()) return;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "challenge_sentence_audio");
    }

    private String getStandardPhonetic(String word) {
        String key = word == null ? "" : word.trim().toLowerCase(Locale.US);
        switch (key) {
            case "resume": return "/ˈrez.juː.meɪ/";
            case "deadline": return "/ˈded.laɪn/";
            case "meeting": return "/ˈmiː.tɪŋ/";
            case "colleague": return "/ˈkɑː.liːɡ/";
            case "project": return "/ˈprɑː.dʒekt/";
            case "salary": return "/ˈsæl.ə.ri/";
            case "interview": return "/ˈɪn.t̬ɚ.vjuː/";
            case "task": return "/tæsk/";
            case "agenda": return "/əˈdʒen.də/";
            case "client": return "/ˈklaɪ.ənt/";
            case "report": return "/rɪˈpɔːrt/";
            case "presentation": return "/ˌprez.ənˈteɪ.ʃən/";
            case "ticket": return "/ˈtɪk.ɪt/";
            case "passport": return "/ˈpæs.pɔːrt/";
            case "hotel": return "/hoʊˈtel/";
            case "station": return "/ˈsteɪ.ʃən/";
            case "map": return "/mæp/";
            case "luggage": return "/ˈlʌɡ.ɪdʒ/";
            case "reservation": return "/ˌrez.ɚˈveɪ.ʃən/";
            case "direction": return "/dəˈrek.ʃən/";
            case "arrival": return "/əˈraɪ.vəl/";
            case "departure": return "/dɪˈpɑːr.tʃɚ/";
            case "booking": return "/ˈbʊk.ɪŋ/";
            case "itinerary": return "/aɪˈtɪn.ə.rer.i/";
            case "lesson": return "/ˈles.ən/";
            case "homework": return "/ˈhoʊm.wɝːk/";
            case "teacher": return "/ˈtiː.tʃɚ/";
            case "student": return "/ˈstuː.dənt/";
            case "library": return "/ˈlaɪ.brer.i/";
            case "exam": return "/ɪɡˈzæm/";
            case "grade": return "/ɡreɪd/";
            case "subject": return "/ˈsʌb.dʒekt/";
            case "question": return "/ˈkwes.tʃən/";
            case "answer": return "/ˈæn.sɚ/";
            case "score": return "/skɔːr/";
            case "strategy": return "/ˈstræt̬.ə.dʒi/";
            case "menu": return "/ˈmen.juː/";
            case "breakfast": return "/ˈbrek.fəst/";
            case "dinner": return "/ˈdɪn.ɚ/";
            case "rice": return "/raɪs/";
            case "vegetable": return "/ˈvedʒ.tə.bəl/";
            case "drink": return "/drɪŋk/";
            case "spicy": return "/ˈspaɪ.si/";
            case "delicious": return "/dɪˈlɪʃ.əs/";
            case "computer": return "/kəmˈpjuː.t̬ɚ/";
            case "phone": return "/foʊn/";
            case "password": return "/ˈpæs.wɝːd/";
            case "website": return "/ˈweb.saɪt/";
            case "download": return "/ˈdaʊn.loʊd/";
            case "software": return "/ˈsɑːft.wer/";
            case "message": return "/ˈmes.ɪdʒ/";
            case "battery": return "/ˈbæt̬.ɚ.i/";
            case "festival": return "/ˈfes.tə.vəl/";
            case "tradition": return "/trəˈdɪʃ.ən/";
            case "custom": return "/ˈkʌs.təm/";
            case "museum": return "/mjuːˈziː.əm/";
            case "music": return "/ˈmjuː.zɪk/";
            case "history": return "/ˈhɪs.tɚ.i/";
            case "art": return "/ɑːrt/";
            case "celebration": return "/ˌsel.əˈbreɪ.ʃən/";
            case "favorite": return "/ˈfeɪ.vər.ət/";
            case "enjoy": return "/ɪnˈdʒɔɪ/";
            case "weekend": return "/ˈwiːk.end/";
            case "activity": return "/ækˈtɪv.ə.t̬i/";
            case "daily": return "/ˈdeɪ.li/";
            case "useful": return "/ˈjuːs.fəl/";
            case "simple": return "/ˈsɪm.pəl/";
            case "confident": return "/ˈkɑːn.fə.dənt/";
            case "hello": return "/həˈloʊ/";
            case "name": return "/neɪm/";
            case "need": return "/niːd/";
            case "like": return "/laɪk/";
            case "friend": return "/frend/";
            case "home": return "/hoʊm/";
            case "city": return "/ˈsɪt̬.i/";
            case "learn": return "/lɝːn/";
            case "speak": return "/spiːk/";
            case "listen": return "/ˈlɪs.ən/";
            case "practice": return "/ˈpræk.tɪs/";
            case "review": return "/rɪˈvjuː/";
            case "remember": return "/rɪˈmem.bɚ/";
            case "improve": return "/ɪmˈpruːv/";
            case "usually": return "/ˈjuː.ʒu.ə.li/";
            case "because": return "/bɪˈkɑːz/";
            case "around": return "/əˈraʊnd/";
            case "plan": return "/plæn/";
            case "suggest": return "/səˈdʒest/";
            case "explain": return "/ɪkˈspleɪn/";
            case "compare": return "/kəmˈper/";
            case "prepare": return "/prɪˈper/";
            case "negotiate": return "/nəˈɡoʊ.ʃi.eɪt/";
            case "summarize": return "/ˈsʌm.ə.raɪz/";
            case "reliable": return "/rɪˈlaɪ.ə.bəl/";
            case "priority": return "/praɪˈɔːr.ə.t̬i/";
            default: return "IPA đang cập nhật";
        }
    }

    private String getVietnameseMeaning(String word) {
        String key = word == null ? "" : word.trim().toLowerCase(Locale.US);
        switch (key) {
            case "resume": return "sơ yếu lý lịch";
            case "deadline": return "hạn chót";
            case "meeting": return "cuộc họp";
            case "colleague": return "đồng nghiệp";
            case "project": return "dự án";
            case "salary": return "lương";
            case "interview": return "phỏng vấn";
            case "task": return "nhiệm vụ";
            case "agenda": return "chương trình họp";
            case "client": return "khách hàng";
            case "report": return "báo cáo";
            case "presentation": return "bài thuyết trình";
            case "ticket": return "vé";
            case "passport": return "hộ chiếu";
            case "hotel": return "khách sạn";
            case "station": return "nhà ga";
            case "map": return "bản đồ";
            case "luggage": return "hành lý";
            case "reservation": return "đặt chỗ";
            case "direction": return "hướng đi";
            case "arrival": return "sự đến nơi";
            case "departure": return "sự khởi hành";
            case "booking": return "việc đặt trước";
            case "itinerary": return "lịch trình";
            case "lesson": return "bài học";
            case "homework": return "bài tập về nhà";
            case "teacher": return "giáo viên";
            case "student": return "học sinh";
            case "library": return "thư viện";
            case "exam": return "kỳ thi";
            case "grade": return "điểm số";
            case "subject": return "môn học";
            case "question": return "câu hỏi";
            case "answer": return "câu trả lời";
            case "score": return "điểm";
            case "strategy": return "chiến lược";
            case "menu": return "thực đơn";
            case "breakfast": return "bữa sáng";
            case "dinner": return "bữa tối";
            case "rice": return "cơm";
            case "vegetable": return "rau củ";
            case "drink": return "đồ uống";
            case "spicy": return "cay";
            case "delicious": return "ngon";
            case "computer": return "máy tính";
            case "phone": return "điện thoại";
            case "password": return "mật khẩu";
            case "website": return "trang web";
            case "download": return "tải xuống";
            case "software": return "phần mềm";
            case "message": return "tin nhắn";
            case "battery": return "pin";
            case "festival": return "lễ hội";
            case "tradition": return "truyền thống";
            case "custom": return "phong tục";
            case "museum": return "bảo tàng";
            case "music": return "âm nhạc";
            case "history": return "lịch sử";
            case "art": return "nghệ thuật";
            case "celebration": return "lễ kỷ niệm";
            case "favorite": return "yêu thích";
            case "enjoy": return "thích thú";
            case "weekend": return "cuối tuần";
            case "activity": return "hoạt động";
            case "daily": return "hằng ngày";
            case "useful": return "hữu ích";
            case "simple": return "đơn giản";
            case "confident": return "tự tin";
            case "hello": return "xin chào";
            case "name": return "tên";
            case "need": return "cần";
            case "like": return "thích";
            case "friend": return "bạn bè";
            case "home": return "nhà";
            case "city": return "thành phố";
            case "learn": return "học";
            case "speak": return "nói";
            case "listen": return "nghe";
            case "practice": return "luyện tập";
            case "review": return "ôn lại";
            case "remember": return "ghi nhớ";
            case "improve": return "cải thiện";
            case "usually": return "thường xuyên";
            case "because": return "bởi vì";
            case "around": return "xung quanh";
            case "plan": return "kế hoạch";
            case "suggest": return "đề xuất";
            case "explain": return "giải thích";
            case "compare": return "so sánh";
            case "prepare": return "chuẩn bị";
            case "negotiate": return "đàm phán";
            case "summarize": return "tóm tắt";
            case "reliable": return "đáng tin cậy";
            case "priority": return "ưu tiên";
            default: return "nghĩa tiếng Việt";
        }
    }

    private void speakChallengeAnswer(Challenge challenge) {
        if (!isTtsReady || tts == null) return;
        String text = null;
        if (challenge.getOptions() != null) {
            for (Challenge.ChallengeOption option : challenge.getOptions()) {
                if (option.isCorrect()) {
                    text = option.getText();
                    break;
                }
            }
        }
        if (text != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "challenge_audio");
        }
    }

    private void nextChallenge() {
        currentChallengeIndex++;
        displayChallenge();
    }

    private void recordChallengeCompleted(Challenge challenge) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || challenge == null || challenge.getId() == null) return;

        Map<String, Object> progress = new HashMap<>();
        progress.put("id", uid + "_" + challenge.getId());
        progress.put("userId", uid);
        progress.put("challengeId", challenge.getId());
        progress.put("lessonId", lessonId);
        progress.put("courseId", courseId);
        progress.put("completed", true);
        progress.put("completedAt", new Date());
        progress.put("type", challenge.getType());
        progress.put("answerText", getCorrectText(challenge));

        db.collection("challengeProgress")
                .document(uid + "_" + challenge.getId())
                .set(progress, SetOptions.merge());
    }

    private void markLessonCompleted() {
        if (lessonCompletionSaved) return;
        lessonCompletionSaved = true;

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null && lessonId != null && courseId != null) {
            for (Challenge challenge : challenges) {
                recordChallengeCompleted(challenge);
            }
            int earnedXp = challenges == null ? 0 : challenges.size() * 10;
            db.collection("users").document(uid)
                    .update(
                            "xp", FieldValue.increment(earnedXp),
                            "totalPoints", FieldValue.increment(earnedXp),
                            "lastActive", new Date()
                    );
            studyPlanRepository.markLessonAsCompleted(uid, lessonId, courseId);
            db.collectionGroup("lessons")
                    .whereEqualTo("lessonId", lessonId)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            doc.getReference().update("completed", true, "isCompleted", true);
                        }
                    });
        }
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
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
