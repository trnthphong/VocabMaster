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
import java.util.List;

public class CourseDetailActivity extends AppCompatActivity {
    private static final String TAG = "CourseDetailActivity";
    private ActivityCourseDetailBinding binding;
    private FirebaseFirestore db;
    private String courseId;
    private RoadmapAdapter adapter;
    private List<RoadmapStep> allStepsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCourseDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        
        courseId = getIntent().getStringExtra("course_id");
        String courseTitle = getIntent().getStringExtra("course_title");
        String courseTheme = getIntent().getStringExtra("course_theme");

        Log.d(TAG, "onCreate: courseId=" + courseId);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        adapter = new RoadmapAdapter(allStepsList);
        binding.recyclerRoadmap.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerRoadmap.setAdapter(adapter);

        if (courseTitle != null) {
            binding.collapsingToolbar.setTitle(courseTitle);
        }
        if (courseTheme != null) {
            binding.textCourseTheme.setText("Chủ đề: " + courseTheme);
        }

        if (courseId == null) {
            findLatestCourseAndLoad();
        } else {
            loadUnitsAndLessons();
        }
    }

    private void findLatestCourseAndLoad() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        binding.progressRoadmap.setVisibility(View.VISIBLE);
        db.collection("courses")
                .whereEqualTo("creatorId", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        List<DocumentSnapshot> docs = new ArrayList<>(querySnapshot.getDocuments());
                        
                        Collections.sort(docs, (d1, d2) -> {
                            long t1 = getTimestampLong(d1, "createdAt");
                            long t2 = getTimestampLong(d2, "createdAt");
                            return Long.compare(t2, t1); // Mới nhất lên đầu
                        });
                        
                        DocumentSnapshot latest = docs.get(0);
                        courseId = latest.getId();
                        Log.d(TAG, "Found course: " + courseId);
                        binding.collapsingToolbar.setTitle(latest.getString("title"));
                        binding.textCourseTheme.setText("Chủ đề: " + latest.getString("theme"));
                        loadUnitsAndLessons();
                    } else {
                        binding.progressRoadmap.setVisibility(View.GONE);
                        Toast.makeText(this, "Bạn chưa có khóa học nào. Hãy tạo mới!", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error finding course", e);
                    binding.progressRoadmap.setVisibility(View.GONE);
                });
    }

    private long getTimestampLong(DocumentSnapshot doc, String field) {
        Object value = doc.get(field);
        if (value instanceof Timestamp) {
            return ((Timestamp) value).getSeconds();
        } else if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0;
    }

    private void loadUnitsAndLessons() {
        if (courseId == null) return;
        binding.progressRoadmap.setVisibility(View.VISIBLE);
        
        db.collection("units")
                .whereEqualTo("courseId", courseId)
                .get()
                .addOnSuccessListener(unitSnapshots -> {
                    if (unitSnapshots.isEmpty()) {
                        Log.d(TAG, "No units found for " + courseId);
                        binding.progressRoadmap.setVisibility(View.GONE);
                        return;
                    }

                    List<DocumentSnapshot> units = new ArrayList<>(unitSnapshots.getDocuments());
                    Collections.sort(units, (u1, u2) -> {
                        long o1 = getLong(u1, "orderNum");
                        long o2 = getLong(u2, "orderNum");
                        return Long.compare(o1, o2);
                    });

                    List<Task<QuerySnapshot>> lessonTasks = new ArrayList<>();
                    for (DocumentSnapshot unitDoc : units) {
                        lessonTasks.add(db.collection("lessons")
                                .whereEqualTo("unitId", unitDoc.getId())
                                .get());
                    }

                    Tasks.whenAllComplete(lessonTasks).addOnCompleteListener(t -> {
                        allStepsList.clear();
                        for (int i = 0; i < units.size(); i++) {
                            DocumentSnapshot unitDoc = units.get(i);
                            Task<QuerySnapshot> task = lessonTasks.get(i);
                            
                            if (task.isSuccessful() && task.getResult() != null) {
                                List<DocumentSnapshot> lessons = new ArrayList<>(task.getResult().getDocuments());
                                Collections.sort(lessons, (l1, l2) -> {
                                    long o1 = getLong(l1, "orderNum");
                                    long o2 = getLong(l2, "orderNum");
                                    return Long.compare(o1, o2);
                                });

                                for (DocumentSnapshot lessonDoc : lessons) {
                                    String type = lessonDoc.getString("type");
                                    allStepsList.add(new RoadmapStep(
                                            lessonDoc.getId(),
                                            unitDoc.getString("title"),
                                            lessonDoc.getString("title"),
                                            getLessonDescription(type),
                                            getIconForType(type),
                                            !Boolean.TRUE.equals(unitDoc.getBoolean("unlocked")),
                                            Boolean.TRUE.equals(lessonDoc.getBoolean("completed")),
                                            type
                                    ));
                                }
                            }
                        }
                        adapter.notifyDataSetChanged();
                        binding.progressRoadmap.setVisibility(View.GONE);
                    });
                });
    }

    private long getLong(DocumentSnapshot doc, String field) {
        Long val = doc.getLong(field);
        return val != null ? val : 0;
    }

    private int getIconForType(String type) {
        if (type == null) return R.drawable.vocab;
        switch (type) {
            case "listening": return R.drawable.ic_listen;
            case "speaking": return R.drawable.brain;
            case "reading": return R.drawable.vocab;
            case "quiz": return R.drawable.exp;
            default: return R.drawable.vocab;
        }
    }

    private String getLessonDescription(String type) {
        if (type == null) return "Bài học";
        switch (type) {
            case "listening": return "Luyện kỹ năng nghe.";
            case "vocabulary": return "Học từ vựng mới.";
            case "reading": return "Đọc hiểu văn bản.";
            case "speaking": return "Thực hành giao tiếp.";
            case "quiz": return "Kiểm tra kiến thức.";
            default: return "Bài học trong lộ trình.";
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_course_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_delete_course) {
            confirmDeleteCourse();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmDeleteCourse() {
        new AlertDialog.Builder(this)
                .setTitle("Xóa khóa học")
                .setMessage("Bạn có chắc chắn muốn xóa khóa học này cùng tất cả bài học bên trong không? Hành động này không thể hoàn tác.")
                .setPositiveButton("Xóa", (dialog, which) -> deleteCourseData())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteCourseData() {
        if (courseId == null) return;
        binding.progressRoadmap.setVisibility(View.VISIBLE);

        // Logic xóa khóa học: Xóa Course -> Xóa các Unit của Course đó -> Xóa các Lesson của các Unit đó
        db.collection("units").whereEqualTo("courseId", courseId).get().addOnSuccessListener(unitSnapshots -> {
            WriteBatch batch = db.batch();
            
            // 1. Xóa các Units và Lessons
            List<Task<QuerySnapshot>> lessonFetchTasks = new ArrayList<>();
            for (DocumentSnapshot unitDoc : unitSnapshots) {
                batch.delete(unitDoc.getReference());
                lessonFetchTasks.add(db.collection("lessons").whereEqualTo("unitId", unitDoc.getId()).get());
            }

            Tasks.whenAllComplete(lessonFetchTasks).addOnCompleteListener(t -> {
                for (Task<QuerySnapshot> lessonTask : lessonFetchTasks) {
                    if (lessonTask.isSuccessful()) {
                        for (DocumentSnapshot lessonDoc : lessonTask.getResult()) {
                            batch.delete(lessonDoc.getReference());
                        }
                    }
                }

                // 2. Xóa chính khóa học
                batch.delete(db.collection("courses").document(courseId));

                // 3. Thực thi batch
                batch.commit().addOnSuccessListener(aVoid -> {
                    binding.progressRoadmap.setVisibility(View.GONE);
                    Toast.makeText(this, "Đã xóa khóa học thành công", Toast.LENGTH_SHORT).show();
                    finish();
                }).addOnFailureListener(e -> {
                    binding.progressRoadmap.setVisibility(View.GONE);
                    Log.e(TAG, "Error deleting course", e);
                    Toast.makeText(this, "Lỗi khi xóa khóa học", Toast.LENGTH_SHORT).show();
                });
            });
        });
    }
}
