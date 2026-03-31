package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.R;
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

public class CreateCourseFlowActivity extends AppCompatActivity {
    private ActivityCreateCourseFlowBinding binding;
    private int currentStep = 1;
    private final int totalSteps = 4;
    
    private String selectedLanguage = "";
    private String selectedTheme = "";
    private int selectedTime = 10;
    private boolean isPremiumUser = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateCourseFlowBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        checkPremiumStatus();
        updateStepUI();
        setupListeners();
    }

    private void checkPremiumStatus() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                    .addOnSuccessListener(snapshot -> {
                        isPremiumUser = Boolean.TRUE.equals(snapshot.getBoolean("premium"));
                    });
        }
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
                    createCourseAndFinish();
                }
            }
        });
    }

    private void updateStepUI() {
        binding.layoutStep1.setVisibility(currentStep == 1 ? View.VISIBLE : View.GONE);
        binding.layoutStep2.setVisibility(currentStep == 2 ? View.VISIBLE : View.GONE);
        binding.layoutStep3.setVisibility(currentStep == 3 ? View.VISIBLE : View.GONE);
        binding.layoutStep4.setVisibility(currentStep == 4 ? View.VISIBLE : View.GONE);

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
        } else if (currentStep == 3) {
            int checkedId = binding.radioGroupTime.getCheckedRadioButtonId();
            if (checkedId == R.id.radio_5min) selectedTime = 5;
            else if (checkedId == R.id.radio_15min) selectedTime = 15;
            else selectedTime = 10;
            return true;
        }
        return true;
    }

    private void createCourseAndFinish() {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        binding.btnNextFlow.setEnabled(false);
        binding.btnNextFlow.setText("Đang khởi tạo...");

        Course course = new Course();
        course.setTitle(selectedLanguage + " - " + selectedTheme);
        course.setDescription("Lộ trình học tập cá nhân hóa " + selectedLanguage);
        course.setTheme(selectedTheme);
        course.setCreatorId(userId);
        course.setPublic(false);
        course.setCreatedAt(new Date());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("courses").add(course).addOnSuccessListener(docRef -> {
            String courseId = docRef.getId();
            fetchVocabAndGenerateDuolingoPath(db, courseId, selectedTheme, course);
        }).addOnFailureListener(e -> {
            binding.btnNextFlow.setEnabled(true);
            binding.btnNextFlow.setText("Hoàn tất");
            Toast.makeText(this, "Lỗi khi tạo khóa học", Toast.LENGTH_SHORT).show();
        });
    }

    private void fetchVocabAndGenerateDuolingoPath(FirebaseFirestore db, String courseId, String theme, Course course) {
        db.collection("vocabularies")
                .limit(100)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> allWords = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String word = doc.getString("word");
                        if (word != null) allWords.add(word);
                    }
                    
                    if (allWords.isEmpty()) {
                        allWords.add("Hello"); allWords.add("Goodbye"); allWords.add("Thank you");
                    }

                    generateDuolingoUnits(db, courseId, theme, allWords, course);
                });
    }

    private void generateDuolingoUnits(FirebaseFirestore db, String courseId, String theme, List<String> words, Course course) {
        WriteBatch batch = db.batch();
        Collections.shuffle(words);

        String[] unitThemes = {
            "Làm quen & Chào hỏi",
            "Giao tiếp Cơ bản",
            "Mở rộng vốn từ " + theme,
            "Tình huống thực tế",
            "Ôn tập tổng hợp"
        };

        for (int i = 0; i < unitThemes.length; i++) {
            String unitId = db.collection("units").document().getId();
            Unit unit = new Unit("Chương " + (i + 1) + ": " + unitThemes[i], i + 1, (i == 0));
            unit.setCourseId(courseId);
            unit.setUnitId(unitId);
            batch.set(db.collection("units").document(unitId), unit);

            createDuolingoLessons(db, batch, unitId, words, i);
        }

        batch.commit().addOnSuccessListener(aVoid -> {
            Intent intent = new Intent(this, CourseDetailActivity.class);
            intent.putExtra("course_id", courseId);
            intent.putExtra("course_title", course.getTitle());
            intent.putExtra("course_theme", course.getTheme());
            startActivity(intent);
            finish();
        }).addOnFailureListener(e -> {
            binding.btnNextFlow.setEnabled(true);
            binding.btnNextFlow.setText("Hoàn tất");
        });
    }

    private void createDuolingoLessons(FirebaseFirestore db, WriteBatch batch, String unitId, List<String> allWords, int unitIndex) {
        String[] lessonTitles = {"Từ vựng 1", "Luyện nghe", "Từ vựng 2", "Hội thoại", "Kiểm tra cuối chương"};
        String[] types = {"vocabulary", "listening", "vocabulary", "speaking", "quiz"};
        
        int wordsPerUnit = 10;
        int startIdx = (unitIndex * wordsPerUnit) % allWords.size();
        
        List<String> unitWords = new ArrayList<>();
        for (int k = 0; k < wordsPerUnit; k++) {
            unitWords.add(allWords.get((startIdx + k) % allWords.size()));
        }

        for (int j = 0; j < lessonTitles.length; j++) {
            Lesson lesson = new Lesson(lessonTitles[j], types[j], 10, 15);
            lesson.setUnitId(unitId);
            lesson.setVocabWords(unitWords);
            lesson.setOrderNum(j + 1);
            
            String lessonId = db.collection("lessons").document().getId();
            lesson.setLessonId(lessonId);
            batch.set(db.collection("lessons").document(lessonId), lesson);
        }
    }
}
