package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.R;
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
import java.util.Date;
import java.util.List;

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

        aiService.generateCurriculum(selectedLanguage, selectedLevel, selectedTopics, new AIService.CurriculumCallback() {
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
        course.setLanguage(selectedLanguage);
        course.setProficiencyLevel(selectedLevel);
        course.setCreatorId(uid);
        course.setDailyTimeMinutes(selectedTime);
        course.setFavoriteTopics(selectedTopics);
        course.setStatus("active");

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
                    allLessonsForSchedule.add(lesson);
                }
            }
        }

        Tasks.whenAllSuccess(allTasks).addOnSuccessListener(aVoid -> {
            createStudyPlanAndSchedule(courseId, allLessonsForSchedule);
        });
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
                goToRoadmap(courseId, "Lộ trình AI");
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
