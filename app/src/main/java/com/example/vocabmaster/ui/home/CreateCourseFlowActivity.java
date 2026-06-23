package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.learning.QuestionGenerationPolicy;
import com.example.vocabmaster.data.model.Challenge;
import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.Lesson;
import com.example.vocabmaster.data.model.Unit;
import com.example.vocabmaster.data.model.StudyPlan;
import com.example.vocabmaster.data.remote.AIService;
import com.example.vocabmaster.data.repository.StudyPlanRepository;
import com.example.vocabmaster.databinding.ActivityCreateCourseFlowBinding;
import com.example.vocabmaster.ui.library.CourseDetailActivity;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.DocumentReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CreateCourseFlowActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_PLACEMENT_TEST = 1001;
    private static final String TAG = "CreateCourseFlow";
    private ActivityCreateCourseFlowBinding binding;
    private int currentStep = 1;
    private final int totalSteps = 5;
    
    private String selectedLanguage = "";
    private String selectedGoal = "";
    private String selectedGoalDetail = "";
    private String selectedLevel = "";
    private List<String> selectedTopics = new ArrayList<>();
    private int selectedTime = 10;
    private int selectedFrequency = 5;

    private AIService aiService;
    private StudyPlanRepository studyPlanRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateCourseFlowBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        aiService = new AIService();
        studyPlanRepository = new StudyPlanRepository(getApplication());

        updateStepUI();
        setupListeners();
    }

    private void setupListeners() {
        binding.btnBackFlow.setOnClickListener(v -> {
            if (currentStep > 1) {
                currentStep--;
                updateStepUI();
            } else {
                finish();
            }
        });

        binding.btnNextFlow.setOnClickListener(v -> {
            if (validateAndSaveStepData()) {
                if (currentStep < totalSteps) {
                    currentStep++;
                    updateStepUI();
                } else {
                    generateAICourse();
                }
            }
        });

        binding.btnTakePlacementTest.setOnClickListener(v -> {
            if (selectedLanguage.isEmpty()) {
                Toast.makeText(this, "Vui lòng chọn ngôn ngữ trước", Toast.LENGTH_SHORT).show();
                currentStep = 1;
                updateStepUI();
                return;
            }
            Intent intent = new Intent(this, PlacementTestActivity.class);
            intent.putExtra("language", selectedLanguage);
            startActivityForResult(intent, REQUEST_CODE_PLACEMENT_TEST);
        });

        binding.radioGroupGoal.setOnCheckedChangeListener((group, checkedId) -> updateGoalDetailPreview(checkedId));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PLACEMENT_TEST && resultCode == RESULT_OK && data != null) {
            String cefrLevel = data.getStringExtra("cefr_level");
            if (cefrLevel != null) {
                selectedLevel = cefrLevel;
                updateLevelSelectionUI(cefrLevel);
                Toast.makeText(this, "Đã xác định trình độ: " + cefrLevel, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateLevelSelectionUI(String level) {
        switch (level) {
            case "A1": binding.radioGroupLevel.check(R.id.level_beginner); break;
            case "A2": binding.radioGroupLevel.check(R.id.level_elementary); break;
            case "B1": binding.radioGroupLevel.check(R.id.level_intermediate); break;
            case "B2": binding.radioGroupLevel.check(R.id.level_advanced); break;
        }
    }

    private void updateGoalDetailPreview(int checkedId) {
        String goal = "";
        if (checkedId == R.id.goal_work) goal = "Work";
        else if (checkedId == R.id.goal_travel) goal = "Travel";
        else if (checkedId == R.id.goal_exam) goal = "Exam";
        else if (checkedId == R.id.goal_hobby) goal = "Hobby";
        selectedGoalDetail = buildGoalDetail(goal);
        binding.textGoalDetail.setText(selectedGoalDetail);
    }

    private String buildGoalDetail(String goal) {
        if ("Work".equals(goal)) {
            return "Work: meetings, email, presentations, interviews, explaining problems, and polite workplace responses.";
        } else if ("Travel".equals(goal)) {
            return "Travel: airport, hotel, directions, transport, ordering, asking for help, and handling small problems abroad.";
        } else if ("Exam".equals(goal)) {
            return "Exam: grammar accuracy, reading inference, paraphrase, vocabulary in context, and answer strategy.";
        } else if ("Hobby".equals(goal)) {
            return "Hobby: daily conversations, personal interests, opinions, storytelling, media, and friendly small talk.";
        }
        return "Choose a goal so VocabMaster can tune situations, vocabulary, and question types.";
    }

    private void updateStepUI() {
        binding.layoutStep1.setVisibility(currentStep == 1 ? View.VISIBLE : View.GONE);
        binding.layoutStep2.setVisibility(currentStep == 2 ? View.VISIBLE : View.GONE);
        binding.layoutStep3.setVisibility(currentStep == 3 ? View.VISIBLE : View.GONE);
        binding.layoutStep4.setVisibility(currentStep == 4 ? View.VISIBLE : View.GONE);
        binding.layoutStep5.setVisibility(currentStep == 5 ? View.VISIBLE : View.GONE);

        binding.dot1.setImageResource(currentStep >= 1 ? R.drawable.ic_dot_active : R.drawable.ic_dot_inactive);
        binding.dot2.setImageResource(currentStep >= 2 ? R.drawable.ic_dot_active : R.drawable.ic_dot_inactive);
        binding.dot3.setImageResource(currentStep >= 3 ? R.drawable.ic_dot_active : R.drawable.ic_dot_inactive);
        binding.dot4.setImageResource(currentStep >= 4 ? R.drawable.ic_dot_active : R.drawable.ic_dot_inactive);
        binding.dot5.setImageResource(currentStep >= 5 ? R.drawable.ic_dot_active : R.drawable.ic_dot_inactive);

        if (currentStep == totalSteps) {
            binding.btnNextFlow.setText("Tạo lộ trình AI");
        } else {
            binding.btnNextFlow.setText("Tiếp tục");
        }
    }

    private boolean validateAndSaveStepData() {
        if (currentStep == 1) {
            int checkedId = binding.radioGroupLanguage.getCheckedRadioButtonId();
            if (checkedId == -1) {
                Toast.makeText(this, "Vui lòng chọn ngôn ngữ", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (checkedId == R.id.radio_en) selectedLanguage = "English";
            else if (checkedId == R.id.radio_ja) selectedLanguage = "Japanese";
            else if (checkedId == R.id.radio_rus) selectedLanguage = "Russian";
            else if (checkedId == R.id.radio_zh) selectedLanguage = "Chinese";
            return true;
        } else if (currentStep == 2) {
            int checkedId = binding.radioGroupGoal.getCheckedRadioButtonId();
            if (checkedId == -1) {
                Toast.makeText(this, "Vui lòng chọn mục tiêu học tập", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (checkedId == R.id.goal_work) selectedGoal = "Work";
            else if (checkedId == R.id.goal_travel) selectedGoal = "Travel";
            else if (checkedId == R.id.goal_exam) selectedGoal = "Exam";
            else if (checkedId == R.id.goal_hobby) selectedGoal = "Hobby";
            selectedGoalDetail = buildGoalDetail(selectedGoal);
            return true;
        } else if (currentStep == 3) {
            int checkedId = binding.radioGroupLevel.getCheckedRadioButtonId();
            if (checkedId == -1 && selectedLevel.isEmpty()) {
                Toast.makeText(this, "Vui lòng chọn trình độ hoặc làm bài kiểm tra", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (checkedId != -1) {
                if (checkedId == R.id.level_beginner) selectedLevel = "A1";
                else if (checkedId == R.id.level_elementary) selectedLevel = "A2";
                else if (checkedId == R.id.level_intermediate) selectedLevel = "B1";
                else if (checkedId == R.id.level_advanced) selectedLevel = "B2";
            }
            return true;
        } else if (currentStep == 4) {
            selectedTopics.clear();
            if (binding.cbCareer.isChecked()) selectedTopics.add("Career");
            if (binding.cbSchool.isChecked()) selectedTopics.add("School");
            if (binding.cbCulture.isChecked()) selectedTopics.add("Culture");
            if (binding.cbTravel.isChecked()) selectedTopics.add("Travel");
            if (binding.cbFood.isChecked()) selectedTopics.add("Food");
            if (binding.cbTech.isChecked()) selectedTopics.add("Technology");
            
            if (selectedTopics.isEmpty()) {
                Toast.makeText(this, "Vui lòng chọn ít nhất một chủ đề", Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        } else if (currentStep == 5) {
            int timeId = binding.radioGroupTime.getCheckedRadioButtonId();
            if (timeId == R.id.radio_5min) selectedTime = 5;
            else if (timeId == R.id.radio_15min) selectedTime = 15;
            else selectedTime = 10;

            int freqId = binding.radioGroupFrequency.getCheckedRadioButtonId();
            if (freqId == R.id.freq_3) selectedFrequency = 3;
            else if (freqId == R.id.freq_7) selectedFrequency = 7;
            else selectedFrequency = 5;
            return true;
        }
        return true;
    }

    private void generateAICourse() {
        binding.btnNextFlow.setEnabled(false);
        binding.btnNextFlow.setText("AI đang thiết kế lộ trình...");

        String courseGoal = selectedGoalDetail == null || selectedGoalDetail.trim().isEmpty()
                ? selectedGoal
                : selectedGoal + " - " + selectedGoalDetail;
        aiService.generateCurriculum(selectedLanguage, selectedLevel, courseGoal, selectedTopics, new AIService.CurriculumCallback() {
            @Override
            public void onSuccess(List<Unit> units) {
                saveGeneratedCourse(units);
            }

            @Override
            public void onError(Throwable t) {
                runOnUiThread(() -> {
                    Toast.makeText(CreateCourseFlowActivity.this, "Lỗi khi gọi AI: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    binding.btnNextFlow.setEnabled(true);
                    binding.btnNextFlow.setText("Thử lại");
                });
            }
        });
    }

    private void saveGeneratedCourse(List<Unit> units) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        Course course = new Course();
        course.setTitle("Lộ trình " + selectedLanguage + " cá nhân hóa");
        course.setDescription("Mục tiêu: " + selectedGoal + ". Lộ trình AI cho trình độ " + selectedLevel);
        course.setTitle(buildCourseTitle());
        course.setDescription(buildCourseDescription());
        course.setLanguage(selectedLanguage);
        course.setLearningGoal(selectedGoal);
        course.setProficiencyLevel(selectedLevel);
        course.setCreatorId(uid);
        course.setDailyTimeMinutes(selectedTime);
        course.setFavoriteTopics(selectedTopics);
        course.setStatus("active");
        course.setCreatedAt(new Date());
        course.setUpdatedAt(new Date());

        db.collection("users").document(uid).collection("personal_courses").add(course)
                .addOnSuccessListener(courseDoc -> {
                    String courseId = courseDoc.getId();
                    courseDoc.update("firestoreId", courseId);
                    saveUnitsAndLessons(db, courseId, units);
                });
    }

    private void saveUnitsAndLessons(FirebaseFirestore db, String courseId, List<Unit> units) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        List<Task<Void>> allTasks = new ArrayList<>();
        List<Lesson> allLessonsForSchedule = new ArrayList<>();
        Set<String> introducedWords = new HashSet<>();

        DocumentReference courseRef = db.collection("users").document(uid).collection("personal_courses").document(courseId);

        for (Unit unit : units) {
            DocumentReference unitRef = courseRef.collection("units").document();
            unit.setUnitId(unitRef.getId());
            unit.setCourseId(courseId);
            allTasks.add(unitRef.set(unit));

            if (unit.getLessons() != null) {
                for (Lesson lesson : unit.getLessons()) {
                    DocumentReference lessonRef = unitRef.collection("lessons").document();
                    lesson.setLessonId(lessonRef.getId());
                    lesson.setUnitId(unitRef.getId());
                    List<Challenge> lessonChallenges = buildChallenges(lesson, introducedWords);
                    List<String> challengeIds = new ArrayList<>();
                    for (Challenge challenge : lessonChallenges) {
                        DocumentReference challengeRef = lessonRef.collection("challenges").document();
                        challenge.setId(challengeRef.getId());
                        challenge.setLessonId(lesson.getLessonId());
                        if (!"INTRO".equalsIgnoreCase(challenge.getType())) {
                            challengeIds.add(challenge.getId());
                        }
                    }
                    lesson.setChallengeIds(challengeIds);
                    lesson.setChallengeCount(challengeIds.size());
                    allTasks.add(lessonRef.set(lesson));
                    allTasks.addAll(saveChallengesForLesson(db, lessonRef, lessonChallenges));
                    allLessonsForSchedule.add(lesson);
                }
            }
        }

        Tasks.whenAllSuccess(allTasks).addOnSuccessListener(aVoid -> {
            createStudyPlanAndSchedule(courseId, allLessonsForSchedule);
        });
    }

    private String buildCourseTitle() {
        String language = selectedLanguage == null || selectedLanguage.trim().isEmpty() ? "Language" : selectedLanguage.trim();
        String goal = selectedGoal == null || selectedGoal.trim().isEmpty() ? "Real Life" : selectedGoal.trim();
        String topicText = "Daily Skills";
        if (selectedTopics != null && !selectedTopics.isEmpty()) {
            if (selectedTopics.size() == 1) {
                topicText = selectedTopics.get(0);
            } else {
                topicText = selectedTopics.get(0) + " & " + selectedTopics.get(1);
            }
        }
        return language + " for " + goal + ": " + topicText;
    }

    private String buildCourseDescription() {
        String level = selectedLevel == null || selectedLevel.trim().isEmpty() ? "your level" : selectedLevel.trim();
        String goal = selectedGoal == null || selectedGoal.trim().isEmpty() ? "real situations" : selectedGoal.trim().toLowerCase(Locale.US);
        String topics = selectedTopics == null || selectedTopics.isEmpty() ? "daily vocabulary" : String.join(", ", selectedTopics);
        String detail = selectedGoalDetail == null || selectedGoalDetail.trim().isEmpty() ? "" : " " + selectedGoalDetail;
        return "Level " + level + " course focused on " + goal + " with " + topics + "." + detail;
    }

    private List<Task<Void>> saveChallengesForLesson(FirebaseFirestore db, DocumentReference lessonRef, List<Challenge> challenges) {
        List<Task<Void>> tasks = new ArrayList<>();

        for (Challenge challenge : challenges) {
            tasks.add(lessonRef.collection("challenges").document(challenge.getId()).set(challenge));
            tasks.add(db.collection("challenges").document(challenge.getId()).set(challenge));
        }

        return tasks;
    }

    private List<Challenge> buildChallenges(Lesson lesson, Set<String> introducedWords) {
        List<String> words = new ArrayList<>();
        if (lesson.getVocabWords() != null) {
            for (String word : lesson.getVocabWords()) {
                if (word != null && !word.trim().isEmpty()) {
                    words.add(word.trim());
                }
            }
        }

        if (words.isEmpty()) {
            words.addAll(Arrays.asList("learn", "practice", "review", "remember", "improve"));
        }

        List<Challenge> challenges = new ArrayList<>();
        String mainWord = words.get(0);
        String selectWord = words.get(Math.min(1, words.size() - 1));
        String listenWord = words.get(Math.min(2, words.size() - 1));
        String typeWord = words.get(Math.min(3, words.size() - 1));
        String reviewWord = words.get(Math.min(4, words.size() - 1));
        int order = 1;
        for (String word : words) {
            String key = word.toLowerCase(Locale.US);
            if (introducedWords.contains(key)) continue;
            challenges.add(buildSingleOptionChallenge("INTRO", order++, buildIntroQuestion(lesson, word), word));
            introducedWords.add(key);
            if (order > 3) break;
        }
        for (String template : QuestionGenerationPolicy.defaultLessonSequence()) {
            int variant = order;
            switch (template) {
                case "COMMUNICATION_SELECT":
                    challenges.add(buildConversationChoiceChallenge(order++, lesson));
                    break;
                case "COMMUNICATION_ARRANGE":
                    challenges.add(buildConversationArrangeChallenge(order++, lesson));
                    break;
                case "COMMUNICATION_SPEAK":
                    challenges.add(buildConversationSpeakingChallenge(order++, lesson));
                    break;
                case "MATCH":
                    challenges.add(buildMatchChallenge(order++, words));
                    break;
                case "ARRANGE":
                    challenges.add(buildArrangeChallenge(order++, buildPracticePhrase(mainWord, lesson)));
                    break;
                case "TYPE":
                    String typeTarget = variant % 3 == 0 ? reviewWord : (variant % 2 == 0 ? typeWord : selectWord);
                    challenges.add(buildChallenge("TYPE", order++, buildTypeQuestion(typeTarget, lesson, variant), words, typeTarget));
                    break;
                case "LISTEN":
                    String listenTarget = variant % 2 == 0 ? mainWord : listenWord;
                    challenges.add(buildChallenge("LISTEN", order++, buildListeningQuestion(listenTarget, lesson, variant), words, listenTarget));
                    break;
                case "SELECT":
                default:
                    String selectTarget = variant % 3 == 0 ? typeWord : (variant % 2 == 0 ? reviewWord : mainWord);
                    challenges.add(buildChallenge("SELECT", order++, buildMeaningQuestion(selectTarget, lesson, variant), words, selectTarget));
                    break;
            }
        }

        return challenges;
    }

    private Challenge buildChallenge(String type, int orderNum, String question, List<String> words, String correctWord) {
        Challenge challenge = new Challenge();
        challenge.setType(type);
        challenge.setOrderNum(orderNum);
        challenge.setQuestion(question);
        challenge.setOptions(buildOptions(words, correctWord));
        return annotateChallenge(challenge, correctWord);
    }

    private Challenge buildSingleOptionChallenge(String type, int orderNum, String question, String correctWord) {
        Challenge challenge = new Challenge();
        challenge.setType(type);
        challenge.setOrderNum(orderNum);
        challenge.setQuestion(question);

        Challenge.ChallengeOption option = new Challenge.ChallengeOption();
        option.setText(correctWord);
        option.setCorrect(true);
        challenge.setOptions(Collections.singletonList(option));
        return annotateChallenge(challenge, correctWord);
    }

    private Challenge buildMatchChallenge(int orderNum, List<String> words) {
        Challenge challenge = new Challenge();
        challenge.setType("MATCH");
        challenge.setOrderNum(orderNum);
        challenge.setQuestion("Nối từ với nghĩa tiếng Việt.");
        challenge.setOptions(buildAllCorrectOptions(words, 4));
        return annotateChallenge(challenge, joinTargets(words, 4));
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
        return annotateChallenge(challenge, sentence);
    }

    private Challenge buildSpeakingChallenge(int orderNum, String word, Lesson lesson) {
        Challenge challenge = new Challenge();
        challenge.setType("SPEAK");
        challenge.setOrderNum(orderNum);
        challenge.setQuestion("Hãy nói câu này thành tiếng: " + buildPracticePhrase(word, lesson));

        Challenge.ChallengeOption option = new Challenge.ChallengeOption();
        option.setText("Tôi đã nói");
        option.setCorrect(true);
        challenge.setOptions(Collections.singletonList(option));
        return annotateChallenge(challenge, buildPracticePhrase(word, lesson));
    }

    private Challenge buildConversationChoiceChallenge(int orderNum, Lesson lesson) {
        String question;
        String correct;
        List<String> answers;
        if ("Work".equals(selectedGoal)) {
            question = "Hội thoại tại nơi làm việc:\nA: Could you send me the report this afternoon?\nB: ___";
            correct = "Sure, I'll send it before 3 p.m.";
            answers = Arrays.asList(correct, "The report is a blue color.", "I sent a sandwich.", "Afternoon is next week.");
        } else if ("Travel".equals(selectedGoal)) {
            question = "Hội thoại khi đi du lịch:\nA: May I see your passport, please?\nB: ___";
            correct = "Of course. Here you are.";
            answers = Arrays.asList(correct, "I don't see the weather.", "The hotel is delicious.", "My passport can swim.");
        } else if ("Exam".equals(selectedGoal)) {
            question = "Hội thoại trong lớp học:\nA: Could you explain this question again?\nB: ___";
            correct = "Of course. Let's go through it together.";
            answers = Arrays.asList(correct, "The question is on Tuesday.", "I explain a sandwich.", "No, the classroom is blue.");
        } else {
            question = "Hội thoại hằng ngày:\nA: Hi! Is this your first time here?\nB: ___";
            correct = "Yes, it is. Nice to meet you.";
            answers = Arrays.asList(correct, "I am here at five kilos.", "No, I don't first.", "The weather meets you.");
        }
        return buildChoiceWithAnswers(orderNum, question, answers, correct);
    }

    private Challenge buildConversationArrangeChallenge(int orderNum, Lesson lesson) {
        String sentence;
        if ("Work".equals(selectedGoal)) {
            sentence = "Could you clarify that point, please?";
        } else if ("Travel".equals(selectedGoal)) {
            sentence = "Could you tell me how to get to the station?";
        } else if ("Exam".equals(selectedGoal)) {
            sentence = "Could you explain this question, please?";
        } else {
            sentence = "What do you like to do on weekends?";
        }
        Challenge challenge = buildArrangeChallenge(orderNum, sentence);
        challenge.setQuestion("Tình huống giao tiếp: Sắp xếp thành một câu tự nhiên.");
        return challenge;
    }

    private Challenge buildConversationSpeakingChallenge(int orderNum, Lesson lesson) {
        String sentence;
        if ("Work".equals(selectedGoal)) {
            sentence = "Could we discuss this after the meeting?";
        } else if ("Travel".equals(selectedGoal)) {
            sentence = "Excuse me, could you help me find my hotel?";
        } else if ("Exam".equals(selectedGoal)) {
            sentence = "Could you give me a moment to think?";
        } else {
            sentence = "It's nice to meet you. How are you today?";
        }
        Challenge challenge = new Challenge();
        challenge.setType("SPEAK");
        challenge.setOrderNum(orderNum);
        challenge.setQuestion("Luyện giao tiếp: Hãy nói câu này thành tiếng:\n" + sentence);
        Challenge.ChallengeOption option = new Challenge.ChallengeOption();
        option.setText("Tôi đã nói");
        option.setCorrect(true);
        challenge.setOptions(Collections.singletonList(option));
        return annotateChallenge(challenge, sentence);
    }

    private Challenge buildChoiceWithAnswers(int orderNum, String question, List<String> answers, String correct) {
        Challenge challenge = new Challenge();
        challenge.setType("SELECT");
        challenge.setOrderNum(orderNum);
        challenge.setQuestion(question);
        List<Challenge.ChallengeOption> options = new ArrayList<>();
        for (String answer : answers) {
            Challenge.ChallengeOption option = new Challenge.ChallengeOption();
            option.setText(answer);
            option.setCorrect(answer.equals(correct));
            options.add(option);
        }
        Collections.shuffle(options);
        challenge.setOptions(options);
        return annotateChallenge(challenge, correct);
    }

    private Challenge annotateChallenge(Challenge challenge, String targetText) {
        QuestionGenerationPolicy.applyMetadata(
                challenge,
                selectedLevel,
                QuestionGenerationPolicy.inferSkill(challenge.getType()),
                targetText,
                challenge.getOrderNum()
        );
        return challenge;
    }

    private String joinTargets(List<String> words, int maxCount) {
        if (words == null || words.isEmpty()) return "";
        List<String> targets = new ArrayList<>();
        for (String word : words) {
            if (word == null || word.trim().isEmpty()) continue;
            targets.add(word.trim());
            if (targets.size() >= maxCount) break;
        }
        return String.join(",", targets);
    }

    private String buildIntroQuestion(Lesson lesson, String word) {
        return "Từ mới: " + word + " = " + getWordVietnameseMeaning(word) + ".";
    }

    private String buildMeaningQuestion(String word, Lesson lesson) {
        return "Choose the word that best fits the clue.";
    }

    private String buildTypeQuestion(String word) {
        return "Complete the sentence with the missing word.";
    }

    private String buildListeningQuestion(String word) {
        return "Nghe và chọn từ đúng.";
    }

    private String buildReviewQuestion(String word, Lesson lesson) {
        return "Review: choose the word that fits this lesson clue.";
    }

    private String buildMeaningQuestion(String word, Lesson lesson, int variant) {
        String context = getLessonContext(lesson);
        switch (variant % 4) {
            case 0:
                return "In " + context + ", which word best fits this clue?";
            case 1:
                return "Choose the best word for this situation: " + buildSituationPrompt(word, lesson);
            case 2:
                return "You need a useful word during " + context + ". Which word fits?";
            default:
                return "Pick the word that completes this idea: " + buildClozeSentence(word, lesson);
        }
    }

    private String buildTypeQuestion(String word, Lesson lesson, int variant) {
        switch (variant % 4) {
            case 0:
                return "Complete the sentence\n" + buildClozeSentence(word, lesson, variant);
            case 1:
                return "Type the missing word\n" + buildClozeSentence(word, lesson, variant);
            case 2:
                return "Complete the sentence\n" + buildClozeSentence(word, lesson, variant + 1);
            default:
                return "Write the missing English word\n" + buildClozeSentence(word, lesson, variant + 2);
        }
    }

    private String buildListeningQuestion(String word, Lesson lesson, int variant) {
        if (variant % 2 == 0) {
            return "Listen and choose the keyword used in a " + getGoalLabelForPrompt() + " context.";
        }
        return "Listen for the word connected to this situation: " + getLessonContext(lesson) + ".";
    }

    private String buildSituationPrompt(String word, Lesson lesson) {
        String context = getLessonContext(lesson);
        if ("Work".equals(selectedGoal)) {
            return "Your manager needs the right word in " + context + ".";
        } else if ("Travel".equals(selectedGoal)) {
            return "You are at a travel desk and need the right travel word.";
        } else if ("Exam".equals(selectedGoal)) {
            return "A test item asks for the best word in context.";
        } else if ("Hobby".equals(selectedGoal)) {
            return "You are chatting about " + context + " and need a natural word.";
        }
        return "You are practicing " + context + " and need the best word.";
    }

    private String buildClozeSentence(String word, Lesson lesson) {
        return buildClozeSentence(word, lesson, 0);
    }

    private String buildClozeSentence(String word, Lesson lesson, int variant) {
        String context = getLessonContext(lesson);
        if ("Work".equals(selectedGoal)) {
            switch (variant % 4) {
                case 0: return "We should review the ___ before the meeting.";
                case 1: return "Please add the ___ to today's agenda.";
                case 2: return "The team discussed the ___ with the client.";
                default: return "I need to prepare the ___ before the presentation.";
            }
        } else if ("Travel".equals(selectedGoal)) {
            switch (variant % 4) {
                case 0: return "I need to check the ___ before I leave.";
                case 1: return "She packed the ___ before going to the station.";
                case 2: return "Can you confirm the ___ at the hotel desk?";
                default: return "We looked at the ___ before choosing a direction.";
            }
        } else if ("Exam".equals(selectedGoal)) {
            switch (variant % 4) {
                case 0: return "Read the ___ carefully before choosing an answer.";
                case 1: return "Write the ___ at the top of your page.";
                case 2: return "The teacher explained the ___ before the exam.";
                default: return "Review the ___ before you submit your work.";
            }
        } else if ("Hobby".equals(selectedGoal)) {
            switch (variant % 4) {
                case 0: return "This ___ makes the activity more enjoyable.";
                case 1: return "My favorite ___ happens on the weekend.";
                case 2: return "We talked about the ___ after class.";
                default: return "That ___ is part of my daily routine.";
            }
        }
        switch (variant % 4) {
            case 0: return "This ___ is useful when talking about " + context + ".";
            case 1: return "I wrote the ___ in my notebook.";
            case 2: return "Can you explain the ___ one more time?";
            default: return "We practiced the ___ during the lesson.";
        }
    }

    private String getGoalLabelForPrompt() {
        if ("Work".equals(selectedGoal)) return "work";
        if ("Travel".equals(selectedGoal)) return "travel";
        if ("Exam".equals(selectedGoal)) return "exam";
        if ("Hobby".equals(selectedGoal)) return "daily conversation";
        return "real life";
    }

    private String getLessonContext(Lesson lesson) {
        String title = lesson.getTitle();
        if (title == null || title.trim().isEmpty()) return "this situation";
        return title.replace("Warm up: ", "")
                .replace("Core words: ", "")
                .replace("Listen in context: ", "")
                .replace("Speak it out: ", "")
                .replace("Review mission: ", "");
    }

    private String buildPracticePhrase(String word, Lesson lesson) {
        String context = getLessonContext(lesson);
        if ("Work".equals(selectedGoal)) {
            return "I need to discuss the " + word + " during " + context + ".";
        } else if ("Travel".equals(selectedGoal)) {
            return "I ask about the " + word + " while handling " + context + ".";
        } else if ("Exam".equals(selectedGoal)) {
            return "I can explain the " + word + " clearly in an exam answer.";
        } else if ("Hobby".equals(selectedGoal)) {
            return "I talk about my " + word + " when sharing " + context + ".";
        }
        return "I can use " + word + " in " + getLessonContext(lesson) + ".";
    }

    private String getWordVietnameseMeaning(String word) {
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
            default: return "từ hữu ích trong bài học này";
        }
    }

    private List<Challenge.ChallengeOption> buildOptions(List<String> words, String correctWord) {
        List<String> optionTexts = new ArrayList<>();
        optionTexts.add(correctWord);

        for (String word : words) {
            if (!word.equals(correctWord) && optionTexts.size() < 4) {
                optionTexts.add(word);
            }
        }

        List<String> fallback = Arrays.asList("lesson", "practice", "review", "language", "meaning", "example");
        for (String word : fallback) {
            if (!optionTexts.contains(word) && optionTexts.size() < 4) {
                optionTexts.add(word);
            }
        }

        Collections.shuffle(optionTexts);

        List<Challenge.ChallengeOption> options = new ArrayList<>();
        for (String text : optionTexts) {
            Challenge.ChallengeOption option = new Challenge.ChallengeOption();
            option.setText(text);
            option.setCorrect(text.equals(correctWord));
            options.add(option);
        }
        return options;
    }

    private List<Challenge.ChallengeOption> buildAllCorrectOptions(List<String> words, int maxCount) {
        List<Challenge.ChallengeOption> options = new ArrayList<>();
        for (String word : words) {
            if (word == null || word.trim().isEmpty()) continue;
            Challenge.ChallengeOption option = new Challenge.ChallengeOption();
            option.setText(word.trim());
            option.setCorrect(true);
            options.add(option);
            if (options.size() >= maxCount) break;
        }
        return options;
    }

    private List<String> buildWordForms(String word) {
        List<String> forms = new ArrayList<>();
        forms.add(word);
        if (word != null && word.length() > 2) {
            forms.add(word + "ing");
            forms.add(word + "ed");
            forms.add(word + "s");
        }
        List<String> fallback = Arrays.asList("lesson", "practice", "review", "meaning");
        for (String item : fallback) {
            if (!forms.contains(item) && forms.size() < 4) forms.add(item);
        }
        return forms;
    }

    private void createStudyPlanAndSchedule(String courseId, List<Lesson> lessons) {
        String uid = FirebaseAuth.getInstance().getUid();
        StudyPlan plan = new StudyPlan();
        plan.setUserId(uid);
        plan.setCourseId(courseId);
        plan.setDailyMinutes(selectedTime);
        plan.setSessionsPerWeek(selectedFrequency);
        
        List<Integer> days = new ArrayList<>();
        if (selectedFrequency == 7) {
            days = Arrays.asList(1, 2, 3, 4, 5, 6, 7);
        } else if (selectedFrequency == 5) {
            days = Arrays.asList(2, 3, 4, 5, 6);
        } else {
            days = Arrays.asList(2, 4, 6);
        }
        plan.setDaysOfWeek(days);
        plan.setStartDate(new Date());

        studyPlanRepository.createStudyPlan(plan, lessons, () -> {
            runOnUiThread(() -> {
                updateUserActiveCourse(courseId);
                goToRoadmap(courseId, buildCourseTitle());
            });
        });
    }

    private void updateUserActiveCourse(String courseId) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                    .update("activeCourseId", courseId);
        }
    }

    private void goToRoadmap(String courseId, String title) {
        Intent intent = new Intent(this, CourseDetailActivity.class);
        intent.putExtra("course_id", courseId);
        intent.putExtra("course_title", title);
        intent.putExtra("is_personal", true);
        startActivity(intent);
        finish();
    }
}
