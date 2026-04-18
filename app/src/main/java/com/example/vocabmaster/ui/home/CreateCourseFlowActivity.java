package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.Lesson;
import com.example.vocabmaster.data.model.Unit;
import com.example.vocabmaster.data.model.StudyPlan;
import com.example.vocabmaster.data.model.LearningProfile;
import com.example.vocabmaster.data.remote.AIService;
import com.example.vocabmaster.data.repository.StudyPlanRepository;
import com.example.vocabmaster.databinding.ActivityCreateCourseFlowBinding;
import com.example.vocabmaster.ui.library.CourseDetailActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.DocumentReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class CreateCourseFlowActivity extends AppCompatActivity {
    private static final String TAG = "CreateCourseFlow";
    private ActivityCreateCourseFlowBinding binding;
    private int currentStep = 1;
    private final int totalSteps = 4;
    
    private String selectedLanguage = "";
    private String selectedLevel = "";
    private List<String> selectedTopics = new ArrayList<>();
    private int selectedTime = 10;

    private AIService aiService;
    private StudyPlanRepository studyPlanRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateCourseFlowBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize services (In a real app, use DI or ViewModel)
        aiService = new AIService(getString(R.string.gemini_api_key));
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
    }

    private void updateStepUI() {
        binding.layoutStep1.setVisibility(currentStep == 1 ? View.VISIBLE : View.GONE);
        binding.layoutStep2.setVisibility(currentStep == 2 ? View.VISIBLE : View.GONE);
        binding.layoutStep3.setVisibility(currentStep == 3 ? View.VISIBLE : View.GONE);
        binding.layoutStep4.setVisibility(currentStep == 4 ? View.VISIBLE : View.GONE);

        binding.dot1.setImageResource(currentStep >= 1 ? R.drawable.ic_dot_active : R.drawable.ic_dot_inactive);
        binding.dot2.setImageResource(currentStep >= 2 ? R.drawable.ic_dot_active : R.drawable.ic_dot_inactive);
        binding.dot3.setImageResource(currentStep >= 3 ? R.drawable.ic_dot_active : R.drawable.ic_dot_inactive);
        binding.dot4.setImageResource(currentStep >= 4 ? R.drawable.ic_dot_active : R.drawable.ic_dot_inactive);

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
            int checkedId = binding.radioGroupLevel.getCheckedRadioButtonId();
            if (checkedId == -1) {
                Toast.makeText(this, "Vui lòng chọn trình độ", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (checkedId == R.id.level_beginner) selectedLevel = "A1";
            else if (checkedId == R.id.level_elementary) selectedLevel = "A2";
            else if (checkedId == R.id.level_intermediate) selectedLevel = "B1";
            else if (checkedId == R.id.level_advanced) selectedLevel = "B2";
            return true;
        } else if (currentStep == 3) {
            selectedTopics.clear();
            if (binding.cbCareer.isChecked()) selectedTopics.add(binding.cbCareer.getText().toString());
            if (binding.cbSchool.isChecked()) selectedTopics.add(binding.cbSchool.getText().toString());
            if (binding.cbCulture.isChecked()) selectedTopics.add(binding.cbCulture.getText().toString());
            if (binding.cbTravel.isChecked()) selectedTopics.add(binding.cbTravel.getText().toString());
            if (binding.cbFood.isChecked()) selectedTopics.add(binding.cbFood.getText().toString());
            if (binding.cbTech.isChecked()) selectedTopics.add(binding.cbTech.getText().toString());
            
            if (selectedTopics.isEmpty()) {
                Toast.makeText(this, "Vui lòng chọn ít nhất một chủ đề", Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        } else if (currentStep == 4) {
            int checkedId = binding.radioGroupTime.getCheckedRadioButtonId();
            if (checkedId == R.id.radio_5min) selectedTime = 5;
            else if (checkedId == R.id.radio_15min) selectedTime = 15;
            else selectedTime = 10;
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
        course.setDescription("Lộ trình được tạo bởi AI dựa trên sở thích: " + String.join(", ", selectedTopics));
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
        WriteBatch batch = db.batch();
        List<Lesson> allLessonsForSchedule = new ArrayList<>();

        for (Unit unit : units) {
            DocumentReference unitRef = db.collection("units").document();
            unit.setUnitId(unitRef.getId());
            unit.setCourseId(courseId);
            batch.set(unitRef, unit);

            for (Lesson lesson : unit.getLessons()) {
                DocumentReference lessonRef = db.collection("lessons").document();
                lesson.setLessonId(lessonRef.getId());
                lesson.setUnitId(unitRef.getId());
                batch.set(lessonRef, lesson);
                allLessonsForSchedule.add(lesson);
            }
        }

        batch.commit().addOnSuccessListener(aVoid -> {
            createStudyPlanAndSchedule(courseId, allLessonsForSchedule);
        });
    }

    private void createStudyPlanAndSchedule(String courseId, List<Lesson> lessons) {
        String uid = FirebaseAuth.getInstance().getUid();
        StudyPlan plan = new StudyPlan();
        plan.setUserId(uid);
        plan.setCourseId(courseId);
        plan.setDailyMinutes(selectedTime);
        plan.setSessionsPerWeek(5); // Default
        plan.setDaysOfWeek(Arrays.asList(2, 3, 4, 5, 6)); // Mon to Fri
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
