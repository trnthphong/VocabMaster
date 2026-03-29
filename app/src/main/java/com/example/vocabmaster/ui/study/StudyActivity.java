package com.example.vocabmaster.ui.study;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.example.vocabmaster.R;
import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.data.model.Lesson;
import com.example.vocabmaster.data.model.StudyTask;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.databinding.ActivityStudyBinding;
import com.example.vocabmaster.databinding.LayoutTaskChoiceBinding;
import com.example.vocabmaster.ui.common.MotionSystem;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StudyActivity extends AppCompatActivity {
    private static final String TAG = "StudyActivity";
    private ActivityStudyBinding binding;
    private final List<StudyTask> tasks = new ArrayList<>();
    private final List<Vocabulary> allVocabs = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isShowingFront = true;
    private FirebaseFirestore db;
    private int correctAnswers = 0;
    private int xpEarned = 0;
    private int heartsLost = 0;
    private String lessonId;
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        lessonId = getIntent().getStringExtra("lesson_id");
        String lessonTitle = getIntent().getStringExtra("lesson_title");
        if (lessonTitle != null) {
            binding.textHeaderTitle.setText(lessonTitle);
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            binding.topBar.setPadding(binding.topBar.getPaddingLeft(), statusBarHeight,
                    binding.topBar.getPaddingRight(), binding.topBar.getPaddingBottom());
            return insets;
        });

        db = FirebaseFirestore.getInstance();
        mediaPlayer = new MediaPlayer();

        setupInitialState();
        if (lessonId != null) {
            loadLessonVocab(lessonId);
        } else {
            loadFromScheduler();
        }
        setupListeners();
    }

    private void setupInitialState() {
        binding.topBar.setTranslationY(-200f);
        binding.taskContainer.setScaleX(0.8f);
        binding.taskContainer.setScaleY(0.8f);
        binding.taskContainer.setAlpha(0f);
        binding.bottomControls.setTranslationY(300f);
    }

    private void runEntryAnimation() {
        binding.topBar.animate().translationY(0).setDuration(600).setInterpolator(new DecelerateInterpolator()).start();
        binding.taskContainer.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(800)
                .setInterpolator(new AnticipateOvershootInterpolator()).start();
        binding.bottomControls.animate().translationY(0).setDuration(700).setStartDelay(200)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void setupListeners() {
        MotionSystem.applyPressState(binding.btnClose);
        MotionSystem.applyPressState(binding.btnSkip);
        MotionSystem.applyPressState(binding.btnNext);
        MotionSystem.applyPressState(binding.btnHard);
        MotionSystem.applyPressState(binding.btnGood);

        binding.cardFlashcard.setOnClickListener(v -> flipCard());
        binding.btnSkip.setOnClickListener(v -> nextTask(false));
        binding.btnNext.setOnClickListener(v -> checkAnswer());
        binding.btnHard.setOnClickListener(v -> nextTask(false));
        binding.btnGood.setOnClickListener(v -> nextTask(true));
        binding.btnClose.setOnClickListener(v -> finish());
        binding.btnAddFlashcard.setOnClickListener(v -> addToFlashcards());
    }

    private void loadLessonVocab(String lessonId) {
        db.collection("lessons").document(lessonId).get().addOnSuccessListener(snapshot -> {
            Lesson lesson = snapshot.toObject(Lesson.class);
            if (lesson != null && lesson.getVocabWords() != null && !lesson.getVocabWords().isEmpty()) {
                fetchVocabDetails(lesson.getVocabWords());
            } else {
                UiFeedback.showSnack(binding.getRoot(), getString(R.string.lesson_no_vocab));
                new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1500);
            }
        });
    }

    private void fetchVocabDetails(List<String> words) {
        List<Task<DocumentSnapshot>> firestoreTasks = new ArrayList<>();
        for (String word : words) {
            firestoreTasks.add(db.collection("vocabularies").document(word.toLowerCase()).get());
        }

        Tasks.whenAllComplete(firestoreTasks).addOnCompleteListener(t -> {
            allVocabs.clear();
            for (Task<DocumentSnapshot> task : firestoreTasks) {
                if (task.isSuccessful() && task.getResult().exists()) {
                    Vocabulary v = task.getResult().toObject(Vocabulary.class);
                    if (v != null) {
                        v.setVocabularyId(task.getResult().getId());
                        allVocabs.add(v);
                    }
                }
            }

            if (allVocabs.isEmpty()) {
                UiFeedback.showSnack(binding.getRoot(), getString(R.string.vocab_data_not_found));
                return;
            }

            generateStudyTasks();
            currentIndex = 0;
            updateUI();
            runEntryAnimation();
        });
    }

    private void generateStudyTasks() {
        tasks.clear();
        for (Vocabulary vocab : allVocabs) {
            // 1. Flashcard task
            tasks.add(new StudyTask(StudyTask.Type.FLASHCARD, vocab));

            // 2. Multiple choice task (Image or Text)
            StudyTask choiceTask = new StudyTask(
                vocab.getImageUrl() != null ? StudyTask.Type.IMAGE_CHOICE : StudyTask.Type.AUDIO_CHOICE, 
                vocab
            );
            choiceTask.setOptions(generateOptions(vocab));
            tasks.add(choiceTask);
        }
        Collections.shuffle(tasks);
    }

    private List<String> generateOptions(Vocabulary correct) {
        List<String> options = new ArrayList<>();
        options.add(correct.getWord());
        
        List<Vocabulary> pool = new ArrayList<>(allVocabs);
        pool.remove(correct);
        Collections.shuffle(pool);
        
        for (int i = 0; i < Math.min(3, pool.size()); i++) {
            options.add(pool.get(i).getWord());
        }
        
        while (options.size() < 4) {
            options.add("Distractor " + options.size());
        }
        
        Collections.shuffle(options);
        return options;
    }

    private void loadFromScheduler() {
        // Implementation for SRS review mode
    }

    private void updateUI() {
        if (currentIndex < tasks.size()) {
            StudyTask currentTask = tasks.get(currentIndex);
            
            // Reset visibility
            binding.cardFlashcard.setVisibility(View.GONE);
            binding.dynamicTaskLayout.setVisibility(View.GONE);
            binding.btnNext.setVisibility(View.VISIBLE);
            binding.btnSkip.setVisibility(View.VISIBLE);
            binding.btnHard.setVisibility(View.GONE);
            binding.btnGood.setVisibility(View.GONE);

            if (currentTask.getType() == StudyTask.Type.FLASHCARD) {
                showFlashcard(currentTask.getTargetVocab());
            } else {
                showChoiceTask(currentTask);
            }

            int progress = (int) (((float) (currentIndex + 1) / tasks.size()) * 100);
            binding.studyProgress.setProgress(progress, true);
        } else {
            openSummary();
        }
    }

    private void showFlashcard(Vocabulary vocab) {
        binding.cardFlashcard.setVisibility(View.VISIBLE);
        binding.textTerm.setText(vocab.getWord());
        binding.textDefinition.setText(vocab.getDefinitionEn());
        
        if (vocab.getImageUrl() != null) {
            binding.imageVocab.setVisibility(View.VISIBLE);
            Glide.with(this).load(vocab.getImageUrl()).into(binding.imageVocab);
        } else {
            binding.imageVocab.setVisibility(View.GONE);
        }

        binding.cardFlashcard.setRotationY(0);
        binding.cardFront.setVisibility(View.VISIBLE);
        binding.cardBack.setVisibility(View.GONE);
        isShowingFront = true;

        binding.btnNext.setVisibility(View.GONE);
        binding.btnSkip.setVisibility(View.GONE);
        binding.btnHard.setVisibility(View.VISIBLE);
        binding.btnGood.setVisibility(View.VISIBLE);
    }

    private void showChoiceTask(StudyTask task) {
        binding.dynamicTaskLayout.setVisibility(View.VISIBLE);
        binding.dynamicTaskLayout.removeAllViews();
        
        LayoutTaskChoiceBinding taskBinding = LayoutTaskChoiceBinding.inflate(getLayoutInflater(), binding.dynamicTaskLayout, true);
        
        if (task.getType() == StudyTask.Type.IMAGE_CHOICE) {
            taskBinding.textQuestion.setText(R.string.task_what_is_this);
            taskBinding.imageTask.setVisibility(View.VISIBLE);
            Glide.with(this).load(task.getTargetVocab().getImageUrl()).into(taskBinding.imageTask);
        } else if (task.getType() == StudyTask.Type.AUDIO_CHOICE) {
            taskBinding.textQuestion.setText(R.string.task_listen_choose);
            taskBinding.btnAudioTask.setVisibility(View.VISIBLE);
            taskBinding.btnAudioTask.setOnClickListener(v -> playAudio(task.getTargetVocab().getAudioUrl()));
            playAudio(task.getTargetVocab().getAudioUrl());
        }

        List<String> options = task.getOptions();
        taskBinding.option1.setText(options.get(0));
        taskBinding.option2.setText(options.get(1));
        taskBinding.option3.setText(options.get(2));
        taskBinding.option4.setText(options.get(3));

        View.OnClickListener optionListener = v -> {
            MaterialButton btn = (MaterialButton) v;
            // Clear others
            taskBinding.option1.setChecked(false);
            taskBinding.option2.setChecked(false);
            taskBinding.option3.setChecked(false);
            taskBinding.option4.setChecked(false);
            btn.setChecked(true);
            btn.setTag("selected");
        };

        taskBinding.option1.setOnClickListener(optionListener);
        taskBinding.option2.setOnClickListener(optionListener);
        taskBinding.option3.setOnClickListener(optionListener);
        taskBinding.option4.setOnClickListener(optionListener);
    }

    private void playAudio(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
        } catch (IOException e) {
            Log.e(TAG, "Audio play failed", e);
        }
    }

    private void checkAnswer() {
        StudyTask currentTask = tasks.get(currentIndex);
        if (currentTask.getType() == StudyTask.Type.FLASHCARD) return;

        LayoutTaskChoiceBinding taskBinding = LayoutTaskChoiceBinding.bind(binding.dynamicTaskLayout.getChildAt(0));
        MaterialButton selectedBtn = null;
        if (taskBinding.option1.isChecked()) selectedBtn = taskBinding.option1;
        else if (taskBinding.option2.isChecked()) selectedBtn = taskBinding.option2;
        else if (taskBinding.option3.isChecked()) selectedBtn = taskBinding.option3;
        else if (taskBinding.option4.isChecked()) selectedBtn = taskBinding.option4;

        if (selectedBtn == null) {
            UiFeedback.showSnack(binding.getRoot(), getString(R.string.please_select_answer));
            return;
        }

        boolean isCorrect = selectedBtn.getText().toString().equals(currentTask.getCorrectWord());
        if (isCorrect) {
            selectedBtn.setStrokeColorResource(R.color.brand_secondary);
            selectedBtn.setBackgroundColor(ContextCompat.getColor(this, R.color.brand_secondary_light));
            nextTask(true);
        } else {
            selectedBtn.setStrokeColorResource(R.color.error);
            UiFeedback.showSnack(binding.getRoot(), getString(R.string.wrong_answer, currentTask.getCorrectWord()));
            nextTask(false);
        }
    }

    private void addToFlashcards() {
        Vocabulary current = tasks.get(currentIndex).getTargetVocab();
        Flashcard card = new Flashcard(current.getWord(), current.getDefinitionEn());
        card.setAudioUrl(current.getAudioUrl());
        card.setImageUrl(current.getImageUrl());
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            AppDatabase.getDatabase(this).flashcardDao().insert(card);
            runOnUiThread(() -> {
                binding.btnAddFlashcard.setImageResource(android.R.drawable.btn_star_big_on);
                UiFeedback.showSnack(binding.getRoot(), getString(R.string.added_to_flashcards));
            });
            executor.shutdown();
        });
    }

    private void flipCard() {
        UiFeedback.performHaptic(this, 20);
        float endRotation = isShowingFront ? 180f : 0f;
        
        binding.cardFlashcard.animate()
                .rotationY(endRotation)
                .setDuration(500)
                .setInterpolator(new AnticipateOvershootInterpolator(1.2f))
                .withStartAction(() -> new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (isShowingFront) {
                        binding.cardFront.setVisibility(View.GONE);
                        binding.cardBack.setVisibility(View.VISIBLE);
                        binding.cardBack.setRotationY(180f);
                    } else {
                        binding.cardFront.setVisibility(View.VISIBLE);
                        binding.cardBack.setVisibility(View.GONE);
                    }
                    isShowingFront = !isShowingFront;
                }, 250))
                .start();
    }

    private void nextTask(boolean success) {
        if (tasks.isEmpty()) return;
        
        processResult(success);
        
        binding.taskContainer.animate()
                .translationX(success ? 1000f : -1000f)
                .alpha(0f)
                .setDuration(400)
                .withEndAction(() -> {
                    currentIndex++;
                    binding.taskContainer.setTranslationX(0);
                    binding.taskContainer.setAlpha(0f);
                    updateUI();
                    binding.taskContainer.animate().alpha(1f).setDuration(300).start();
                })
                .start();
    }

    private void processResult(boolean success) {
        if (success) {
            correctAnswers++;
            xpEarned += 5;
            UiFeedback.performHaptic(this, 50);
        } else {
            heartsLost++;
            UiFeedback.performHaptic(this, 100);
        }
    }

    private void openSummary() {
        Intent intent = new Intent(this, StudySummaryActivity.class);
        intent.putExtra("total", tasks.size());
        intent.putExtra("correct", correctAnswers);
        intent.putExtra("xp", xpEarned);
        intent.putExtra("hearts_lost", heartsLost);
        if (lessonId != null) {
            intent.putExtra("lesson_id", lessonId);
            db.collection("lessons").document(lessonId).update("completed", true);
        }
        startActivity(intent);
        finish();
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
