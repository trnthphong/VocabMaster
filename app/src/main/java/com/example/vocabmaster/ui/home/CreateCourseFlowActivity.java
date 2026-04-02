package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Challenge;
import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.Lesson;
import com.example.vocabmaster.data.model.Unit;
import com.example.vocabmaster.databinding.ActivityCreateCourseFlowBinding;
import com.example.vocabmaster.ui.library.CourseDetailActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class CreateCourseFlowActivity extends AppCompatActivity {
    private static final String TAG = "CreateCourseFlow";
    private ActivityCreateCourseFlowBinding binding;
    private int currentStep = 1;
    private final int totalSteps = 3; // Giảm xuống 3 bước
    
    private String selectedLanguage = "";
    private String selectedTheme = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateCourseFlowBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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
                    checkIfCourseExists();
                }
            }
        });
    }

    private void updateStepUI() {
        binding.layoutStep1.setVisibility(currentStep == 1 ? View.VISIBLE : View.GONE);
        binding.layoutStep2.setVisibility(currentStep == 2 ? View.VISIBLE : View.GONE);
        binding.layoutStep3.setVisibility(currentStep == 3 ? View.VISIBLE : View.GONE);

        binding.progressFlow.setProgress(currentStep);
        binding.progressFlow.setMax(totalSteps);

        if (currentStep == totalSteps) {
            binding.btnNextFlow.setText("Hoàn tất");
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
            RadioButton rb = findViewById(checkedId);
            selectedLanguage = rb.getText().toString();
            return true;
        } else if (currentStep == 2) {
            int checkedId = binding.radioGroupThemes.getCheckedRadioButtonId();
            if (checkedId == -1) {
                Toast.makeText(this, "Vui lòng chọn chủ đề", Toast.LENGTH_SHORT).show();
                return false;
            }
            RadioButton rb = findViewById(checkedId);
            selectedTheme = rb.getText().toString();
            return true;
        }
        return true;
    }

    private void checkIfCourseExists() {
        String courseTitle = selectedLanguage + " - " + selectedTheme;
        binding.btnNextFlow.setEnabled(false);
        binding.btnNextFlow.setText("Đang kiểm tra dữ liệu...");

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("courses")
                .whereEqualTo("title", courseTitle)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        QueryDocumentSnapshot doc = (QueryDocumentSnapshot) querySnapshot.getDocuments().get(0);
                        goToRoadmap(doc.getId(), doc.getString("title"), doc.getString("theme"));
                    } else {
                        createCourseAndFinish();
                    }
                })
                .addOnFailureListener(e -> createCourseAndFinish());
    }

    private void createCourseAndFinish() {
        String userId = FirebaseAuth.getInstance().getUid();
        binding.btnNextFlow.setText("Đang khởi tạo lộ trình...");

        Course course = new Course();
        course.setTitle(selectedLanguage + " - " + selectedTheme);
        course.setDescription("Lộ trình học tập " + selectedLanguage);
        course.setTheme(selectedTheme);
        course.setCreatorId(userId);
        course.setPublic(true);
        course.setCreatedAt(new Date());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("courses").add(course).addOnSuccessListener(docRef -> {
            String courseId = docRef.getId();
            fetchVocabAndGenerateDuolingoPath(db, courseId, selectedTheme, course);
        }).addOnFailureListener(e -> {
            resetButton();
            Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void fetchVocabAndGenerateDuolingoPath(FirebaseFirestore db, String courseId, String theme, Course course) {
        db.collection("vocabularies").limit(40).get().addOnSuccessListener(querySnapshot -> {
            List<QueryDocumentSnapshot> allVocabs = new ArrayList<>();
            for (QueryDocumentSnapshot doc : querySnapshot) allVocabs.add(doc);
            generateDuolingoUnits(db, courseId, theme, allVocabs, course);
        }).addOnFailureListener(e -> generateDuolingoUnits(db, courseId, theme, new ArrayList<>(), course));
    }

    private void generateDuolingoUnits(FirebaseFirestore db, String courseId, String theme, List<QueryDocumentSnapshot> vocabs, Course course) {
        WriteBatch batch = db.batch();
        String[] unitThemes = {"Khởi đầu", "Cơ bản", "Nâng cao " + theme};

        for (int i = 0; i < unitThemes.length; i++) {
            String unitId = db.collection("units").document().getId();
            Unit unit = new Unit("Chương " + (i + 1) + ": " + unitThemes[i], i + 1, (i == 0));
            unit.setCourseId(courseId);
            unit.setUnitId(unitId);
            batch.set(db.collection("units").document(unitId), unit);

            createLessons(db, batch, unitId, vocabs, i);
        }

        batch.commit().addOnSuccessListener(aVoid -> {
            goToRoadmap(courseId, course.getTitle(), course.getTheme());
        }).addOnFailureListener(e -> {
            resetButton();
            Toast.makeText(this, "Lỗi lưu dữ liệu: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void createLessons(FirebaseFirestore db, WriteBatch batch, String unitId, List<QueryDocumentSnapshot> allVocabs, int unitIndex) {
        String[] lessonTitles = {"Từ vựng", "Luyện nghe", "Kiểm tra"};
        for (int j = 0; j < lessonTitles.length; j++) {
            String lessonId = db.collection("lessons").document().getId();
            Lesson lesson = new Lesson(lessonTitles[j], "vocabulary", 10, 15);
            lesson.setUnitId(unitId);
            lesson.setLessonId(lessonId);
            lesson.setOrderNum(j + 1);
            batch.set(db.collection("lessons").document(lessonId), lesson);

            for (int k = 0; k < 2; k++) {
                Challenge challenge = new Challenge();
                challenge.setLessonId(lessonId);
                challenge.setOrderNum(k + 1);
                challenge.setType("SELECT");
                challenge.setQuestion("Nghĩa của từ này là gì?");
                String challengeId = db.collection("challenges").document().getId();
                challenge.setId(challengeId);
                batch.set(db.collection("challenges").document(challengeId), challenge);
            }
        }
    }

    private void goToRoadmap(String courseId, String title, String theme) {
        Intent intent = new Intent(this, CourseDetailActivity.class);
        intent.putExtra("course_id", courseId);
        intent.putExtra("course_title", title);
        intent.putExtra("course_theme", theme);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void resetButton() {
        binding.btnNextFlow.setEnabled(true);
        binding.btnNextFlow.setText("Hoàn tất");
    }
}
