package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.data.model.Challenge;
import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.Lesson;
import com.example.vocabmaster.data.model.Unit;
import com.example.vocabmaster.databinding.ActivityCreateCourseFlowBinding;
import com.example.vocabmaster.ui.library.CourseDetailActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.Date;

public class CreateCourseFlowActivity extends AppCompatActivity {
    private ActivityCreateCourseFlowBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateCourseFlowBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupListeners();
    }

    private void setupListeners() {
        binding.btnBackFlow.setOnClickListener(v -> finish());

        binding.btnCompleteFlow.setOnClickListener(v -> {
            String themeName = binding.editThemeName.getText().toString().trim();
            if (TextUtils.isEmpty(themeName)) {
                binding.inputLayoutThemeName.setError("Vui lòng nhập tên chủ đề");
                return;
            }
            binding.inputLayoutThemeName.setError(null);
            createCourseAndFinish(themeName);
        });
    }

    private void createCourseAndFinish(String themeName) {
        String userId = FirebaseAuth.getInstance().getUid();
        binding.btnCompleteFlow.setEnabled(false);
        binding.btnCompleteFlow.setText("Đang khởi tạo...");

        Course course = new Course();
        course.setTitle(themeName);
        course.setTheme(themeName);
        course.setCreatorId(userId);
        course.setPublic(false);
        course.setCreatedAt(new Date());
        course.setTargetLanguageId(1); // Luôn là Tiếng Anh theo yêu cầu mới

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("courses").add(course).addOnSuccessListener(docRef -> {
            String courseId = docRef.getId();
            generateDefaultRoadmap(db, courseId, themeName, course);
        }).addOnFailureListener(e -> {
            binding.btnCompleteFlow.setEnabled(true);
            binding.btnCompleteFlow.setText("Khám phá ngay");
            Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void generateDefaultRoadmap(FirebaseFirestore db, String courseId, String theme, Course course) {
        WriteBatch batch = db.batch();
        String[] unitThemes = {"Khởi đầu", "Cơ bản", "Nâng cao"};

        for (int i = 0; i < unitThemes.length; i++) {
            String unitId = db.collection("units").document().getId();
            Unit unit = new Unit("Chương " + (i + 1) + ": " + unitThemes[i], i + 1, (i == 0));
            unit.setCourseId(courseId);
            unit.setUnitId(unitId);
            batch.set(db.collection("units").document(unitId), unit);

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

        batch.commit().addOnSuccessListener(aVoid -> {
            goToRoadmap(courseId, course.getTitle(), course.getTheme());
        }).addOnFailureListener(e -> {
            binding.btnCompleteFlow.setEnabled(true);
            binding.btnCompleteFlow.setText("Khám phá ngay");
            Toast.makeText(this, "Lỗi lưu dữ liệu: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
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
}
