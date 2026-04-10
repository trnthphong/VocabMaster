package com.example.vocabmaster.ui.library;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Challenge;
import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.Lesson;
import com.example.vocabmaster.data.model.Unit;
import com.example.vocabmaster.databinding.ActivityCourseDetailBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.example.vocabmaster.ui.home.TopicWordListActivity;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CourseDetailActivity extends AppCompatActivity {
    private static final String TAG = "CourseDetailActivity";
    private ActivityCourseDetailBinding binding;
    private FirebaseFirestore db;
    private String courseId;
    private String courseTheme;
    private String displayTitle;
    
    private RoadmapAdapter adapter;
    private List<RoadmapStep> allStepsList = new ArrayList<>();
    private Set<String> completedChallenges = new HashSet<>();
    private boolean isCourseInLibrary = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCourseDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        courseId = getIntent().getStringExtra("course_id");
        courseTheme = getIntent().getStringExtra("course_theme");
        displayTitle = getIntent().getStringExtra("display_title");
        
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }

        if (displayTitle != null) {
            binding.textCourseTheme.setText("Chủ đề: " + displayTitle);
        } else if (courseTheme != null) {
            binding.textCourseTheme.setText("Chủ đề: " + courseTheme);
        }

        adapter = new RoadmapAdapter(allStepsList);
        binding.recyclerRoadmap.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerRoadmap.setAdapter(adapter);

        setupClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // LUÔN TẢI LẠI DỮ LIỆU KHI QUAY LẠI MÀN HÌNH NÀY
        loadUserDataAndRoadmap();
    }

    private void setupClickListeners() {
        binding.btnViewVocabulary.setOnClickListener(v -> openVocabList());
        binding.btnAddToLibrary.setOnClickListener(v -> {
            if (!isCourseInLibrary) {
                addCourseToLibrary();
            }
        });
    }

    private void openVocabList() {
        Intent intent = new Intent(this, TopicWordListActivity.class);
        intent.putExtra("topic", courseTheme);
        intent.putExtra("display_title", displayTitle);
        intent.putExtra("lang_code", "en");
        startActivity(intent);
    }

    private void addCourseToLibrary() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        binding.btnAddToLibrary.setEnabled(false);
        binding.btnAddToLibrary.setText("Đang thêm...");

        Course course = new Course();
        course.setTitle(displayTitle != null ? displayTitle : courseTheme);
        course.setTheme(courseTheme);
        course.setCreatorId(uid);
        course.setPublic(false);
        course.setStatus("active");
        course.setCreatedAt(new Date());
        course.setTargetLanguageId(1);

        db.collection("courses").add(course).addOnSuccessListener(docRef -> {
            courseId = docRef.getId();
            generateRoadmapData(courseId, course);
        }).addOnFailureListener(e -> {
            binding.btnAddToLibrary.setEnabled(true);
            binding.btnAddToLibrary.setText("Thêm vào thư viện");
        });
    }

    private void generateRoadmapData(String courseId, Course course) {
        db.collection("vocabularies")
                .whereEqualTo("topic", courseTheme != null ? courseTheme.toLowerCase() : "")
                .limit(50)
                .get()
                .addOnSuccessListener(vocabSnapshot -> {
                    List<String> allVocabIds = new ArrayList<>();
                    for (DocumentSnapshot doc : vocabSnapshot) allVocabIds.add(doc.getId());
                    proceedWithGeneration(courseId, allVocabIds);
                });
    }

    private void proceedWithGeneration(String courseId, List<String> allVocabIds) {
        WriteBatch batch = db.batch();
        String[] unitThemes = {"Khởi đầu", "Cơ bản", "Nâng cao"};
        Collections.shuffle(allVocabIds);

        int vocabPerLesson = 5;
        int vocabIndex = 0;

        for (int i = 0; i < unitThemes.length; i++) {
            String unitId = db.collection("units").document().getId();
            Unit unit = new Unit("Chương " + (i + 1) + ": " + unitThemes[i], i + 1, (i == 0));
            unit.setCourseId(courseId);
            unit.setUnitId(unitId);
            batch.set(db.collection("units").document(unitId), unit);

            String[] lessonTitles = {"Từ vựng 1", "Từ vựng 2", "Kiểm tra"};
            for (int j = 0; j < lessonTitles.length; j++) {
                String lessonId = db.collection("lessons").document().getId();
                Lesson lesson = new Lesson(lessonTitles[j], "vocabulary", 10, 15);
                lesson.setUnitId(unitId);
                lesson.setLessonId(lessonId);
                lesson.setOrderNum(j + 1);

                List<String> lessonVocabs = new ArrayList<>();
                for (int k = 0; k < vocabPerLesson && vocabIndex < allVocabIds.size(); k++) {
                    lessonVocabs.add(allVocabIds.get(vocabIndex++));
                }
                if (lessonVocabs.isEmpty() && !allVocabIds.isEmpty()) {
                    lessonVocabs.add(allVocabIds.get(0));
                }
                
                lesson.setVocabWords(lessonVocabs);
                batch.set(db.collection("lessons").document(lessonId), lesson);

                for (int k = 0; k < 2; k++) {
                    Challenge challenge = new Challenge();
                    challenge.setLessonId(lessonId);
                    challenge.setOrderNum(k + 1);
                    challenge.setType("SELECT");
                    String challengeId = db.collection("challenges").document().getId();
                    challenge.setId(challengeId);
                    batch.set(db.collection("challenges").document(challengeId), challenge);
                }
            }
        }

        batch.commit().addOnSuccessListener(aVoid -> {
            loadUserDataAndRoadmap(); // Tải lại để hiển thị roadmap mới tạo
            UiFeedback.showSnack(binding.getRoot(), "Đã tạo lộ trình!");
        });
    }

    private void loadUserDataAndRoadmap() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        if (binding != null) binding.progressRoadmap.setVisibility(View.VISIBLE);
        
        db.collection("challengeProgress")
                .whereEqualTo("userId", uid)
                .whereEqualTo("completed", true)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    completedChallenges.clear();
                    for (DocumentSnapshot doc : querySnapshot) {
                        completedChallenges.add(doc.getString("challengeId"));
                    }
                    
                    if (courseId != null) {
                        loadCourseDetails();
                    } else if (courseTheme != null) {
                        findCourseByTheme(courseTheme);
                    } else {
                        if (binding != null) binding.progressRoadmap.setVisibility(View.GONE);
                    }
                });
    }

    private void findCourseByTheme(String theme) {
        String uid = FirebaseAuth.getInstance().getUid();
        db.collection("courses")
                .whereEqualTo("creatorId", uid)
                .whereEqualTo("theme", theme)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        courseId = querySnapshot.getDocuments().get(0).getId();
                        loadCourseDetails();
                    } else {
                        isCourseInLibrary = false;
                        if (binding != null) {
                            binding.progressRoadmap.setVisibility(View.GONE);
                            binding.layoutEmptyRoadmap.setVisibility(View.VISIBLE);
                            binding.btnAddToLibrary.setVisibility(View.VISIBLE);
                        }
                    }
                });
    }

    private void loadCourseDetails() {
        if (courseId == null) return;
        db.collection("courses").document(courseId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        isCourseInLibrary = true;
                        if (binding != null) binding.btnAddToLibrary.setVisibility(View.GONE);
                        Course course = doc.toObject(Course.class);
                        if (course != null) {
                            if (courseTheme == null) courseTheme = course.getTheme();
                            if (displayTitle == null) displayTitle = course.getTitle();
                        }
                    }
                    loadUnitsAndLessons();
                });
    }

    private void loadUnitsAndLessons() {
        if (courseId == null) return;
        db.collection("units").whereEqualTo("courseId", courseId).get().addOnSuccessListener(unitSnapshots -> {
            List<DocumentSnapshot> units = new ArrayList<>(unitSnapshots.getDocuments());
            Collections.sort(units, (u1, u2) -> Long.compare(getLong(u1, "orderNum"), getLong(u2, "orderNum")));

            List<Task<QuerySnapshot>> lessonTasks = new ArrayList<>();
            for (DocumentSnapshot unitDoc : units) {
                lessonTasks.add(db.collection("lessons").whereEqualTo("unitId", unitDoc.getId()).get());
            }

            Tasks.whenAllComplete(lessonTasks).addOnCompleteListener(t -> {
                List<LessonWithChallenges> allLessons = new ArrayList<>();
                for (int i = 0; i < units.size(); i++) {
                    Task<QuerySnapshot> task = lessonTasks.get(i);
                    if (task.isSuccessful()) {
                        for (DocumentSnapshot lessonDoc : task.getResult().getDocuments()) {
                            allLessons.add(new LessonWithChallenges(lessonDoc, units.get(i)));
                        }
                    }
                }
                Collections.sort(allLessons, (l1, l2) -> {
                    int unitComp = Long.compare(getLong(l1.unitDoc, "orderNum"), getLong(l2.unitDoc, "orderNum"));
                    if (unitComp != 0) return unitComp;
                    return Long.compare(getLong(l1.lessonDoc, "orderNum"), getLong(l2.lessonDoc, "orderNum"));
                });
                fetchChallengesAndBuildRoadmap(allLessons);
            });
        });
    }

    private void fetchChallengesAndBuildRoadmap(List<LessonWithChallenges> lessons) {
        List<Task<QuerySnapshot>> challengeTasks = new ArrayList<>();
        for (LessonWithChallenges lc : lessons) {
            challengeTasks.add(db.collection("challenges").whereEqualTo("lessonId", lc.lessonDoc.getId()).get());
        }

        Tasks.whenAllComplete(challengeTasks).addOnCompleteListener(t -> {
            allStepsList.clear();
            boolean foundActive = false;

            for (int i = 0; i < lessons.size(); i++) {
                LessonWithChallenges lc = lessons.get(i);
                Task<QuerySnapshot> task = challengeTasks.get(i);
                int total = 0, completed = 0;
                if (task.isSuccessful()) {
                    for (DocumentSnapshot d : task.getResult()) {
                        total++;
                        if (completedChallenges.contains(d.getId())) completed++;
                    }
                }

                boolean isCompleted = (total > 0 && completed == total);
                boolean isLocked = false;
                if (!isCompleted && !foundActive) foundActive = true;
                else if (foundActive) isLocked = true;

                allStepsList.add(new RoadmapStep(lc.lessonDoc.getId(), lc.unitTitle, lc.lessonDoc.getString("title"), 
                        completed + "/" + total + " Challenges", getIconForType(lc.lessonDoc.getString("type")), 
                        isLocked, isCompleted, lc.lessonDoc.getString("type")));
            }
            adapter.notifyDataSetChanged();
            if (binding != null) binding.progressRoadmap.setVisibility(View.GONE);
        });
    }

    private long getLong(DocumentSnapshot doc, String field) {
        Long val = doc.getLong(field);
        return val != null ? val : 0;
    }

    private int getIconForType(String type) {
        if ("quiz".equals(type)) return R.drawable.exp;
        return R.drawable.vocab;
    }

    private static class LessonWithChallenges {
        DocumentSnapshot lessonDoc, unitDoc; String unitTitle;
        LessonWithChallenges(DocumentSnapshot ld, DocumentSnapshot ud) {
            this.lessonDoc = ld; this.unitDoc = ud;
            this.unitTitle = ud != null ? ud.getString("title") : "";
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
