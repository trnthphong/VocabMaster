package com.example.vocabmaster.util;

import android.util.Log;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataSeeder {
    private static final String TAG = "DataSeeder";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void seedAllData() {
        Log.d(TAG, "Bắt đầu đẩy dữ liệu lên Firestore...");
        
        String[] languages = {"en", "ru", "ja", "zh"};
        String[] levels = {"beginner", "intermediate", "advanced"};
        
        for (String lang : languages) {
            for (String level : levels) {
                seedCourse(lang, level);
            }
        }
    }

    private void seedCourse(String lang, String level) {
        String courseId = lang + "_" + level + "_course";
        String title = getCourseTitle(lang, level);
        
        Map<String, Object> course = new HashMap<>();
        course.put("title", title);
        course.put("description", "Lộ trình học " + lang + " trình độ " + level);
        course.put("language", lang);
        course.put("proficiencyLevel", level);
        course.put("isPublic", true);

        db.collection("courses").document(courseId).set(course).addOnSuccessListener(aVoid -> {
            createUnitsForCourse(courseId, lang, level);
        });
    }

    private String getCourseTitle(String lang, String level) {
        String langName = "";
        switch (lang) {
            case "en": langName = "Tiếng Anh"; break;
            case "ru": langName = "Tiếng Nga"; break;
            case "ja": langName = "Tiếng Nhật"; break;
            case "zh": langName = "Tiếng Trung"; break;
        }
        
        String levelName = "";
        switch (level) {
            case "beginner": levelName = "Mới bắt đầu"; break;
            case "intermediate": levelName = "Trung cấp"; break;
            case "advanced": levelName = "Nâng cao"; break;
        }
        
        return "Lộ trình " + langName + " - " + levelName;
    }

    private void createUnitsForCourse(String courseId, String lang, String level) {
        for (int i = 1; i <= 10; i++) {
            String unitId = courseId + "_unit_" + i;
            final int unitIndex = i;
            
            Map<String, Object> unit = new HashMap<>();
            unit.put("courseId", courseId);
            unit.put("title", "Chương " + i);
            unit.put("description", "Mô tả chương " + i);
            unit.put("orderNum", i);
            unit.put("isUnlocked", i == 1);

            db.collection("units").document(unitId).set(unit).addOnSuccessListener(aVoid -> {
                createLessonsForUnit(unitId, new String[]{
                    "Bài " + unitIndex + ".1: Khởi động", 
                    "Bài " + unitIndex + ".2: Từ vựng trọng tâm", 
                    "Bài " + unitIndex + ".3: Kiểm tra"
                });
            });
        }
    }

    private void createLessonsForUnit(String unitId, String[] titles) {
        WriteBatch batch = db.batch();
        for (int i = 0; i < titles.length; i++) {
            String lessonId = unitId + "_L" + (i + 1);
            Map<String, Object> lesson = new HashMap<>();
            lesson.put("unitId", unitId);
            lesson.put("title", titles[i]);
            lesson.put("orderNum", i + 1);
            lesson.put("type", i == 2 ? "quiz" : "vocabulary");
            lesson.put("completed", false);
            
            DocumentReference lessonRef = db.collection("lessons").document(lessonId);
            batch.set(lessonRef, lesson);

            createChallengesForLesson(lessonId, batch);
        }
        batch.commit();
    }

    private void createChallengesForLesson(String lessonId, WriteBatch batch) {
        // Tạo 10 thử thách cho mỗi bài học
        for (int k = 1; k <= 10; k++) {
            String cId = lessonId + "_C" + k;
            Map<String, Object> challenge = new HashMap<>();
            challenge.put("lessonId", lessonId);
            challenge.put("question", "Câu hỏi mẫu " + k + " cho bài học này?");
            
            String correctAns = "Đáp án đúng " + k;
            List<String> options = Arrays.asList(
                correctAns,
                "Lựa chọn sai A-" + k,
                "Lựa chọn sai B-" + k,
                "Lựa chọn sai C-" + k
            );
            
            challenge.put("correctAnswer", correctAns);
            challenge.put("options", options);
            challenge.put("type", (k % 2 == 0) ? "SELECT" : "INPUT");
            challenge.put("orderNum", k);
            
            batch.set(db.collection("challenges").document(cId), challenge);
        }
    }
}
