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
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

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
    private boolean isPersonal = false;
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
        isPersonal = getIntent().getBooleanExtra("is_personal", false);
        String initialTitle = getIntent().getStringExtra("course_title");
        
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            if (initialTitle != null) {
                getSupportActionBar().setTitle(initialTitle);
            }
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
                        fetchCourseDetails();
                    }
                });
    }

    private void fetchCourseDetails() {
        String uid = FirebaseAuth.getInstance().getUid();
        DocumentSnapshot courseDoc;
        
        Task<DocumentSnapshot> courseTask;
        if (isPersonal && uid != null) {
            courseTask = db.collection("users").document(uid).collection("personal_courses").document(courseId).get();
        } else {
            courseTask = db.collection("courses").document(courseId).get();
        }

        courseTask.addOnSuccessListener(doc -> {
            if (doc.exists() && getSupportActionBar() != null) {
                String title = doc.getString("title");
                if (title != null) {
                    // Logic lọc tiêu đề chỉ lấy ngôn ngữ (ví dụ: "Lộ trình Tiếng Anh" -> "Tiếng Anh")
                    String cleanTitle = title.replace("Lộ trình ", "").split(" - ")[0];
                    getSupportActionBar().setTitle(cleanTitle);
                }
                String desc = doc.getString("description");
                if (desc != null) binding.textCourseDescription.setText(desc);
            }
            loadUnitsAndLessons();
        });
    }

    private void findLatestCourse() {
        String uid = FirebaseAuth.getInstance().getUid();
        db.collection("users").document(uid).collection("personal_courses")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        courseId = querySnapshot.getDocuments().get(0).getId();
                        isPersonal = true;
                        fetchCourseDetails();
                    } else {
                        db.collection("courses")
                                .whereEqualTo("creatorId", uid)
                                .get()
                                .addOnSuccessListener(globalSnapshot -> {
                                    if (!globalSnapshot.isEmpty()) {
                                        courseId = globalSnapshot.getDocuments().get(0).getId();
                                        isPersonal = false;
                                        fetchCourseDetails();
                                    } else {
                                        binding.progressRoadmap.setVisibility(View.GONE);
                                    }
                                });
                    }
                });
    }

    private void loadUnitsAndLessons() {
        if (courseId == null) return;
        String uid = FirebaseAuth.getInstance().getUid();
        
        CollectionReference unitsRef;
        if (isPersonal && uid != null) {
            unitsRef = db.collection("users").document(uid).collection("personal_courses").document(courseId).collection("units");
        } else {
            unitsRef = db.collection("units");
        }

        Query unitsQuery = isPersonal ? unitsRef.orderBy("orderNum") : unitsRef.whereEqualTo("courseId", courseId).orderBy("orderNum");

        unitsQuery.get().addOnSuccessListener(unitSnapshots -> {
            List<DocumentSnapshot> units = unitSnapshots.getDocuments();
            if (units.isEmpty()) {
                binding.progressRoadmap.setVisibility(View.GONE);
                return;
            }

            List<Task<QuerySnapshot>> lessonTasks = new ArrayList<>();
            for (DocumentSnapshot unitDoc : units) {
                if (isPersonal) {
                    lessonTasks.add(unitDoc.getReference().collection("lessons").orderBy("orderNum").get());
                } else {
                    lessonTasks.add(db.collection("lessons").whereEqualTo("unitId", unitDoc.getId()).orderBy("orderNum").get());
                }
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
                
                // Dữ liệu đã được sort theo orderNum từ Query
                fetchChallengesAndBuildRoadmap(allLessons);
            });
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error loading units", e);
            if (binding != null) binding.progressRoadmap.setVisibility(View.GONE);
        });
    }

    private void fetchChallengesAndBuildRoadmap(List<LessonWithChallenges> lessons) {
        if (lessons.isEmpty()) {
            if (binding != null) binding.progressRoadmap.setVisibility(View.GONE);
            return;
        }

        List<Task<QuerySnapshot>> challengeTasks = new ArrayList<>();
        for (LessonWithChallenges lc : lessons) {
            // Đối với khóa học cá nhân, challenge cũng có thể nằm trong sub-collection hoặc global.
            // Ở đây ta ưu tiên tìm trong sub-collection của lesson nếu có.
            challengeTasks.add(lc.lessonDoc.getReference().collection("challenges").get().continueWithTask(task -> {
                if (task.isSuccessful() && !task.getResult().isEmpty()) {
                    return task;
                }
                // Fallback về global challenges nếu không có sub-collection
                return db.collection("challenges").whereEqualTo("lessonId", lc.lessonDoc.getId()).get();
            }));
        }

        Tasks.whenAllComplete(challengeTasks).addOnCompleteListener(t -> {
            allStepsList.clear();
            boolean foundActive = false;
            String lastUnitId = "";
            int lessonIndexInUnit = 0;

            for (int i = 0; i < lessons.size(); i++) {
                LessonWithChallenges lc = lessons.get(i);
                Task<QuerySnapshot> task = challengeTasks.get(i);
                
                int totalChallenges = 0;
                int completedCount = 0;

                if (task.isSuccessful() && task.getResult() != null) {
                    for (DocumentSnapshot challengeDoc : task.getResult()) {
                        totalChallenges++;
                        if (completedChallenges.contains(challengeDoc.getId())) {
                            completedCount++;
                        }
                    }
                }

                if (totalChallenges == 0) totalChallenges = 1;

                boolean isCompleted = (completedCount >= totalChallenges);
                boolean isLocked = false;
                boolean isActive = false;

                if (!isCompleted && !foundActive) {
                    isActive = true;
                    foundActive = true;
                } else if (foundActive) {
                    isLocked = true;
                }

                // Chọn icon dựa trên vị trí TRONG UNIT: start (lesson 1), speedup (lesson 2), finish (lesson 3)
                String currentUnitId = lc.unitDoc.getId();
                if (!currentUnitId.equals(lastUnitId)) {
                    lastUnitId = currentUnitId;
                    lessonIndexInUnit = 0;
                } else {
                    lessonIndexInUnit++;
                }

                int iconRes;
                if (lessonIndexInUnit == 0) {
                    iconRes = R.drawable.start;
                } else if (lessonIndexInUnit == 1) {
                    iconRes = R.drawable.speedup;
                } else {
                    iconRes = R.drawable.finish;
                }

                String type = lc.lessonDoc.getString("type");
                allStepsList.add(new RoadmapStep(
                        lc.lessonDoc.getId(),
                        lc.unitTitle,
                        lc.lessonDoc.getString("title"),
                        completedCount + "/" + totalChallenges + " Challenges",
                        iconRes,
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
        String uid = FirebaseAuth.getInstance().getUid();
        
        Toast.makeText(this, "Đang xóa khóa học...", Toast.LENGTH_SHORT).show();
        
        Task<Void> deleteTask;
        if (isPersonal && uid != null) {
            deleteTask = db.collection("users").document(uid).collection("personal_courses").document(courseId).delete();
        } else {
            deleteTask = db.collection("courses").document(courseId).delete();
        }

        deleteTask.addOnSuccessListener(aVoid -> {
            if (uid != null) {
                db.collection("users").document(uid).update("activeCourseId", null);
            }
        }).addOnFailureListener(e -> Log.e(TAG, "Delete failed", e));

        finish();
    }

    private long getLong(DocumentSnapshot doc, String field) {
        if (doc == null) return 0;
        Long val = doc.getLong(field);
        return val != null ? val : 0;
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
