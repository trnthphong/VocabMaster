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
import com.google.firebase.firestore.QueryDocumentSnapshot;
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
    private String langCode;
    
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
        langCode = getIntent().getStringExtra("lang_code");
        
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
        intent.putExtra("lang_code", "en"); // Luôn là tiếng Anh
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
        course.setTargetLanguageId(1); // Tiếng Anh

        db.collection("courses").add(course).addOnSuccessListener(docRef -> {
            courseId = docRef.getId();
            generateRoadmapData(courseId, course);
        }).addOnFailureListener(e -> {
            binding.btnAddToLibrary.setEnabled(true);
            binding.btnAddToLibrary.setText("Thêm vào thư viện");
            Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void generateRoadmapData(String courseId, Course course) {
        db.collection("vocabularies")
                .whereEqualTo("topic", courseTheme != null ? courseTheme.toLowerCase() : "")
                .limit(20)
                .get()
                .addOnSuccessListener(querySnapshot -> {
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
                        isCourseInLibrary = true;
                        binding.btnAddToLibrary.setVisibility(View.GONE);
                        binding.layoutEmptyRoadmap.setVisibility(View.GONE);
                        binding.labelRoadmap.setVisibility(View.VISIBLE);
                        loadUnitsAndLessons();
                        UiFeedback.showSnack(binding.getRoot(), "Đã thêm vào thư viện!");
                    });
                });
    }

    private void loadUserDataAndRoadmap() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        binding.progressRoadmap.setVisibility(View.VISIBLE);
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
                        binding.progressRoadmap.setVisibility(View.GONE);
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
                        List<DocumentSnapshot> docs = querySnapshot.getDocuments();
                        Collections.sort(docs, (d1, d2) -> {
                            Timestamp t1 = d1.getTimestamp("createdAt");
                            Timestamp t2 = d2.getTimestamp("createdAt");
                            if (t1 == null || t2 == null) return 0;
                            return t2.compareTo(t1);
                        });
                        courseId = docs.get(0).getId();
                        isCourseInLibrary = true;
                        loadCourseDetails();
                    } else {
                        isCourseInLibrary = false;
                        binding.progressRoadmap.setVisibility(View.GONE);
                        binding.layoutEmptyRoadmap.setVisibility(View.VISIBLE);
                        binding.btnAddToLibrary.setVisibility(View.VISIBLE); // Hiện nút thêm
                    }
                })
                .addOnFailureListener(e -> binding.progressRoadmap.setVisibility(View.GONE));
    }

    private void loadCourseDetails() {
        if (courseId == null) return;
        db.collection("courses").document(courseId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        isCourseInLibrary = true;
                        binding.btnAddToLibrary.setVisibility(View.GONE); // Ẩn nếu đã có
                        Course course = doc.toObject(Course.class);
                        if (course != null) {
                            if (courseTheme == null) courseTheme = course.getTheme();
                            if (displayTitle == null) displayTitle = course.getTitle();
                            binding.textCourseTheme.setText("Chủ đề: " + (displayTitle != null ? displayTitle : courseTheme));
                        }
                    } else {
                        isCourseInLibrary = false;
                        binding.btnAddToLibrary.setVisibility(View.VISIBLE);
                    }
                    loadUnitsAndLessons();
                });
    }

    private void loadUnitsAndLessons() {
        if (courseId == null) return;
        
        binding.progressRoadmap.setVisibility(View.VISIBLE);
        binding.layoutEmptyRoadmap.setVisibility(View.GONE);
        binding.labelRoadmap.setVisibility(View.VISIBLE);
        
        db.collection("units")
                .whereEqualTo("courseId", courseId)
                .get()
                .addOnSuccessListener(unitSnapshots -> {
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
                                List<DocumentSnapshot> lessons = task.getResult().getDocuments();
                                for (DocumentSnapshot lessonDoc : lessons) {
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
                })
                .addOnFailureListener(e -> {
                    if (binding != null) binding.progressRoadmap.setVisibility(View.GONE);
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
                
                int totalChallenges = 0;
                int completedCount = 0;

                if (task.isSuccessful()) {
                    for (DocumentSnapshot challengeDoc : task.getResult()) {
                        totalChallenges++;
                        if (completedChallenges.contains(challengeDoc.getId())) {
                            completedCount++;
                        }
                    }
                }

                boolean isCompleted = (totalChallenges > 0 && completedCount == totalChallenges);
                boolean isLocked = false;

                if (!isCompleted && !foundActive) {
                    foundActive = true;
                } else if (foundActive) {
                    isLocked = true;
                }

                String type = lc.lessonDoc.getString("type");
                allStepsList.add(new RoadmapStep(
                        lc.lessonDoc.getId(),
                        lc.unitTitle,
                        lc.lessonDoc.getString("title"),
                        completedCount + "/" + totalChallenges + " Challenges",
                        getIconForType(type),
                        isLocked,
                        isCompleted,
                        type
                ));
            }
            adapter.notifyDataSetChanged();
            if (binding != null) {
                binding.progressRoadmap.setVisibility(View.GONE);
                if (allStepsList.isEmpty() && isCourseInLibrary) {
                    binding.layoutEmptyRoadmap.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void confirmDeleteCourse() {
        new AlertDialog.Builder(this)
                .setTitle("Xóa chủ đề")
                .setMessage("Bạn có chắc chắn muốn xóa chủ đề này khỏi thư viện không?")
                .setPositiveButton("Xóa", (dialog, which) -> deleteCourse())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteCourse() {
        if (courseId == null) return;
        Toast.makeText(this, "Đang xóa chủ đề...", Toast.LENGTH_SHORT).show();
        db.collection("courses").document(courseId).delete()
                .addOnSuccessListener(aVoid -> finish())
                .addOnFailureListener(e -> Log.e(TAG, "Delete failed", e));
    }

    private long getLong(DocumentSnapshot doc, String field) {
        if (doc == null) return 0;
        Long val = doc.getLong(field);
        return val != null ? val : 0;
    }

    private int getIconForType(String type) {
        if ("quiz".equals(type)) return R.drawable.exp;
        if ("listening".equals(type)) return R.drawable.ic_listen;
        return R.drawable.vocab;
    }

    private static class LessonWithChallenges {
        DocumentSnapshot lessonDoc;
        DocumentSnapshot unitDoc;
        String unitTitle;
        LessonWithChallenges(DocumentSnapshot ld, DocumentSnapshot ud) {
            this.lessonDoc = ld;
            this.unitDoc = ud;
            this.unitTitle = ud != null ? ud.getString("title") : "";
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_course_detail, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem deleteItem = menu.findItem(R.id.action_delete_course);
        if (deleteItem != null) {
            deleteItem.setVisible(isCourseInLibrary);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_view_vocab) {
            openVocabList();
            return true;
        } else if (id == R.id.action_delete_course) {
            confirmDeleteCourse();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
