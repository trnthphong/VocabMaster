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
import com.example.vocabmaster.databinding.ActivityCourseDetailBinding;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CourseDetailActivity extends AppCompatActivity {
    private static final String TAG = "CourseDetailActivity";
    private ActivityCourseDetailBinding binding;
    private FirebaseFirestore db;
    private String courseId;
    private RoadmapAdapter adapter;
    private List<RoadmapStep> allStepsList = new ArrayList<>();
    private Set<String> completedChallenges = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCourseDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        courseId = getIntent().getStringExtra("course_id");
        
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        adapter = new RoadmapAdapter(allStepsList);
        binding.recyclerRoadmap.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerRoadmap.setAdapter(adapter);

        loadUserDataAndRoadmap();
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
                    
                    if (courseId == null) {
                        findLatestCourse();
                    } else {
                        loadUnitsAndLessons();
                    }
                });
    }

    private void findLatestCourse() {
        String uid = FirebaseAuth.getInstance().getUid();
        db.collection("courses")
                .whereEqualTo("creatorId", uid)
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
                        loadUnitsAndLessons();
                    }
                });
    }

    private void loadUnitsAndLessons() {
        if (courseId == null) return;
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
                    Log.e(TAG, "Error loading units", e);
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
                boolean isActive = false;

                if (!isCompleted && !foundActive) {
                    isActive = true;
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
            if (binding != null) binding.progressRoadmap.setVisibility(View.GONE);
        });
    }

    private void confirmDeleteCourse() {
        new AlertDialog.Builder(this)
                .setTitle("Xóa khóa học")
                .setMessage("Bạn có chắc chắn muốn xóa khóa học này không?")
                .setPositiveButton("Xóa", (dialog, which) -> deleteCourse())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteCourse() {
        if (courseId == null) return;
        
        // Thoát màn hình ngay lập tức để tạo cảm giác tức thì
        Toast.makeText(this, "Đang xóa khóa học...", Toast.LENGTH_SHORT).show();
        db.collection("courses").document(courseId).delete()
                .addOnFailureListener(e -> Log.e(TAG, "Delete failed in background", e));
        finish();
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
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_delete_course) {
            confirmDeleteCourse();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
