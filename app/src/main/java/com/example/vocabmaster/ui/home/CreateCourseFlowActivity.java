package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.databinding.ActivityCreateCourseFlowBinding;
import com.example.vocabmaster.ui.library.CourseDetailActivity;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class CreateCourseFlowActivity extends AppCompatActivity {
    private static final String TAG = "CreateCourseFlow";
    private ActivityCreateCourseFlowBinding binding;
    private int currentStep = 1;
    private final int totalSteps = 4;
    
    private String selectedLanguage = "";
    private String selectedLevel = "";
    private List<String> selectedTopics = new ArrayList<>();
    private int selectedTime = 10;

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
                    startCourseCreationFlow();
                }
            }
        });
    }

    private void updateStepUI() {
        binding.layoutStep1.setVisibility(currentStep == 1 ? View.VISIBLE : View.GONE);
        binding.layoutStep2.setVisibility(currentStep == 2 ? View.VISIBLE : View.GONE);
        binding.layoutStep3.setVisibility(currentStep == 3 ? View.VISIBLE : View.GONE);
        binding.layoutStep4.setVisibility(currentStep == 4 ? View.VISIBLE : View.GONE);

        // Update dot indicators
        binding.dot1.setImageResource(currentStep >= 1 ? R.drawable.ic_dot_active : R.drawable.ic_dot_inactive);
        binding.dot2.setImageResource(currentStep >= 2 ? R.drawable.ic_dot_active : R.drawable.ic_dot_inactive);
        binding.dot3.setImageResource(currentStep >= 3 ? R.drawable.ic_dot_active : R.drawable.ic_dot_inactive);
        binding.dot4.setImageResource(currentStep >= 4 ? R.drawable.ic_dot_active : R.drawable.ic_dot_inactive);

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
            if (checkedId == R.id.radio_en) selectedLanguage = "en";
            else if (checkedId == R.id.radio_ja) selectedLanguage = "ja";
            else if (checkedId == R.id.radio_rus) selectedLanguage = "ru";
            else if (checkedId == R.id.radio_zh) selectedLanguage = "zh";
            return true;
        } else if (currentStep == 2) {
            int checkedId = binding.radioGroupLevel.getCheckedRadioButtonId();
            if (checkedId == -1) {
                Toast.makeText(this, "Vui lòng chọn trình độ", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (checkedId == R.id.level_beginner) selectedLevel = "beginner";
            else if (checkedId == R.id.level_elementary) selectedLevel = "beginner";
            else if (checkedId == R.id.level_intermediate) selectedLevel = "intermediate";
            else if (checkedId == R.id.level_advanced) selectedLevel = "advanced";
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

    private void startCourseCreationFlow() {
        binding.btnNextFlow.setEnabled(false);
        binding.btnNextFlow.setText("Đang khởi tạo bài học...");

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        CollectionReference personalCoursesRef = db.collection("users").document(uid).collection("personal_courses");

        personalCoursesRef
                .whereEqualTo("language", selectedLanguage)
                .whereEqualTo("proficiencyLevel", selectedLevel)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        String courseId = querySnapshot.getDocuments().get(0).getId();
                        updateUserActiveCourse(courseId);
                        goToRoadmap(courseId, querySnapshot.getDocuments().get(0).getString("title"));
                    } else {
                        cloneTemplateToPersonal(db, personalCoursesRef);
                    }
                })
                .addOnFailureListener(e -> cloneTemplateToPersonal(db, personalCoursesRef));
    }

    private void cloneTemplateToPersonal(FirebaseFirestore db, CollectionReference personalRef) {
        String templateId = selectedLanguage + "_" + selectedLevel + "_course";
        
        db.collection("courses").document(templateId).get().addOnSuccessListener(templateDoc -> {
            if (templateDoc.exists()) {
                Map<String, Object> courseData = templateDoc.getData();
                if (courseData != null) {
                    courseData.put("creatorId", FirebaseAuth.getInstance().getUid());
                    courseData.put("createdAt", new Date());
                    courseData.put("updatedAt", new Date());
                    courseData.put("isPublic", false);
                    courseData.put("dailyTimeMinutes", selectedTime);
                    courseData.put("status", "active");
                    if (!selectedTopics.isEmpty()) {
                        courseData.put("theme", selectedTopics.get(0));
                    }

                    personalRef.add(courseData).addOnSuccessListener(docRef -> {
                        String newCourseId = docRef.getId();
                        updateUserActiveCourse(newCourseId);
                        performDeepClone(db, templateId, docRef);
                    });
                }
            } else {
                createNewEmptyCourse(personalRef);
            }
        }).addOnFailureListener(e -> createNewEmptyCourse(personalRef));
    }

    private void performDeepClone(FirebaseFirestore db, String templateCourseId, DocumentReference newCourseRef) {
        db.collection("units").whereEqualTo("courseId", templateCourseId).get().addOnSuccessListener(unitSnapshots -> {
            if (unitSnapshots.isEmpty()) {
                goToRoadmap(newCourseRef.getId(), "Lộ trình mới");
                return;
            }

            List<Task<Void>> allCloneTasks = new ArrayList<>();

            for (QueryDocumentSnapshot unitDoc : unitSnapshots) {
                Map<String, Object> unitData = unitDoc.getData();
                unitData.put("courseId", newCourseRef.getId());
                String originalUnitId = unitDoc.getId();
                
                TaskCompletionSource<Void> unitAndLessonsSc = new TaskCompletionSource<>();
                allCloneTasks.add(unitAndLessonsSc.getTask());

                newCourseRef.collection("units").add(unitData).addOnSuccessListener(newUnitRef -> {
                    db.collection("lessons").whereEqualTo("unitId", originalUnitId).get().addOnSuccessListener(lessonSnapshots -> {
                        if (lessonSnapshots.isEmpty()) {
                            unitAndLessonsSc.setResult(null);
                            return;
                        }
                        
                        WriteBatch batch = db.batch();
                        for (QueryDocumentSnapshot lessonDoc : lessonSnapshots) {
                            Map<String, Object> lessonData = lessonDoc.getData();
                            lessonData.put("unitId", newUnitRef.getId());
                            lessonData.put("completed", false);
                            DocumentReference newLessonRef = newUnitRef.collection("lessons").document();
                            batch.set(newLessonRef, lessonData);
                        }
                        batch.commit().addOnCompleteListener(task -> unitAndLessonsSc.setResult(null));
                    }).addOnFailureListener(e -> unitAndLessonsSc.setResult(null));
                }).addOnFailureListener(e -> unitAndLessonsSc.setResult(null));
            }

            Tasks.whenAllComplete(allCloneTasks).addOnCompleteListener(t -> {
                newCourseRef.get().addOnSuccessListener(d -> goToRoadmap(newCourseRef.getId(), d.getString("title")));
            });
        });
    }

    private void createNewEmptyCourse(CollectionReference personalRef) {
        Course course = new Course();
        course.setTitle("Lộ trình " + selectedLanguage);
        course.setLanguage(selectedLanguage);
        course.setProficiencyLevel(selectedLevel);
        course.setCreatorId(FirebaseAuth.getInstance().getUid());
        course.setCreatedAt(new Date());
        course.setStatus("active");
        
        personalRef.add(course).addOnSuccessListener(docRef -> {
            updateUserActiveCourse(docRef.getId());
            goToRoadmap(docRef.getId(), course.getTitle());
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
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void resetButton() {
        binding.btnNextFlow.setEnabled(true);
        binding.btnNextFlow.setText("Hoàn tất");
    }
}
