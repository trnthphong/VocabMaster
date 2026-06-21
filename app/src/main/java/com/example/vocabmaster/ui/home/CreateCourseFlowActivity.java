package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.R;
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

        aiService.generateCurriculum(selectedLanguage, selectedLevel, selectedGoal, selectedTopics, new AIService.CurriculumCallback() {
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
                    allTasks.add(lessonRef.set(lesson));
                    allTasks.addAll(createChallengesForLesson(db, lessonRef, lesson, introducedWords));
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
        return "Level " + level + " course focused on " + goal + " with " + topics + ".";
    }

    private List<Task<Void>> createChallengesForLesson(FirebaseFirestore db, DocumentReference lessonRef, Lesson lesson, Set<String> introducedWords) {
        List<Task<Void>> tasks = new ArrayList<>();
        List<Challenge> challenges = buildChallenges(lesson, introducedWords);

        for (Challenge challenge : challenges) {
            DocumentReference challengeRef = lessonRef.collection("challenges").document();
            challenge.setId(challengeRef.getId());
            challenge.setLessonId(lesson.getLessonId());

            tasks.add(challengeRef.set(challenge));
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
        challenges.add(buildChallenge("SELECT", order++, buildMeaningQuestion(mainWord, lesson), words, mainWord));
        challenges.add(buildChallenge("TYPE", order++, buildTypeQuestion(selectWord), words, selectWord));
        challenges.add(buildChallenge("LISTEN", order++, buildListeningQuestion(listenWord), words, listenWord));
        challenges.add(buildArrangeChallenge(order++, buildPracticePhrase(listenWord, lesson)));
        challenges.add(buildMatchChallenge(order++, words));
        challenges.add(buildChallenge("TYPE", order++, buildTypeQuestion(typeWord), words, typeWord));
        challenges.add(buildChallenge("SELECT", order++, buildMeaningQuestion(reviewWord, lesson), words, reviewWord));
        challenges.add(buildChallenge("LISTEN", order++, buildListeningQuestion(mainWord), words, mainWord));
        challenges.add(buildArrangeChallenge(order++, buildPracticePhrase(mainWord, lesson)));
        challenges.add(buildChallenge("TYPE", order++, buildTypeQuestion(reviewWord), words, reviewWord));
        challenges.add(buildChallenge("SELECT", order++, buildMeaningQuestion(typeWord, lesson), words, typeWord));
        challenges.add(buildChallenge("LISTEN", order++, buildListeningQuestion(selectWord), words, selectWord));

        return challenges;
    }

    private Challenge buildChallenge(String type, int orderNum, String question, List<String> words, String correctWord) {
        Challenge challenge = new Challenge();
        challenge.setType(type);
        challenge.setOrderNum(orderNum);
        challenge.setQuestion(question);
        challenge.setOptions(buildOptions(words, correctWord));
        return challenge;
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
        return challenge;
    }

    private Challenge buildMatchChallenge(int orderNum, List<String> words) {
        Challenge challenge = new Challenge();
        challenge.setType("MATCH");
        challenge.setOrderNum(orderNum);
        challenge.setQuestion("Nối từ với nghĩa tiếng Việt.");
        challenge.setOptions(buildAllCorrectOptions(words, 4));
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

    private Challenge buildSpeakingChallenge(int orderNum, String word, Lesson lesson) {
        Challenge challenge = new Challenge();
        challenge.setType("SPEAK");
        challenge.setOrderNum(orderNum);
        challenge.setQuestion("Hãy nói câu này thành tiếng: " + buildPracticePhrase(word, lesson));

        Challenge.ChallengeOption option = new Challenge.ChallengeOption();
        option.setText("Tôi đã nói");
        option.setCorrect(true);
        challenge.setOptions(Collections.singletonList(option));
        return challenge;
    }

    private String buildIntroQuestion(Lesson lesson, String word) {
        return "Từ mới: " + word + " = " + getWordVietnameseMeaning(word) + ".";
    }

    private String buildMeaningQuestion(String word, Lesson lesson) {
        return "Chọn từ có nghĩa là \"" + getWordVietnameseMeaning(word) + "\".";
    }

    private String buildTypeQuestion(String word) {
        return "Viết từ có nghĩa là \"" + getWordVietnameseMeaning(word) + "\".";
    }

    private String buildListeningQuestion(String word) {
        return "Nghe và chọn từ đúng.";
    }

    private String buildReviewQuestion(String word, Lesson lesson) {
        return "Ôn lại: chọn từ tương ứng với nghĩa \"" + getWordVietnameseMeaning(word) + "\".";
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
