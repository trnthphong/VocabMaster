package com.example.vocabmaster.ui.library;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.CourseScheduleDay;
import com.example.vocabmaster.data.repository.StudyPlanRepository;
import com.example.vocabmaster.databinding.ActivityCourseDetailBinding;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CourseDetailActivity extends AppCompatActivity {
    private ActivityCourseDetailBinding binding;
    private FirebaseFirestore db;
    private String courseId;
    private boolean isPersonal = false;
    private RoadmapAdapter roadmapTodayAdapter;
    private RoadmapAdapter roadmapOverviewAdapter;
    private SessionAdapter sessionAdapter;
    private List<RoadmapStep> allStepsList = new ArrayList<>();
    private List<RoadmapStep> todayStepList = new ArrayList<>();
    private Set<String> completedChallenges = new HashSet<>();
    private StudyPlanRepository studyPlanRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCourseDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        studyPlanRepository = new StudyPlanRepository(getApplication());
        courseId = getIntent().getStringExtra("course_id");
        isPersonal = getIntent().getBooleanExtra("is_personal", false);
        String initialTitle = getIntent().getStringExtra("course_title");
        
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            if (initialTitle != null) {
                binding.textCourseName.setText(initialTitle);
            }
        }

        setupTabs();
        setupRecyclerViews();
        setupRealTimeCalendar();
        loadUserDataAndRoadmap();
        loadStudySessions();
    }

    private void setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                binding.layoutRoadmapTab.setVisibility(View.GONE);
                binding.layoutOverviewTab.setVisibility(View.GONE);
                binding.layoutSessionsTab.setVisibility(View.GONE);

                switch (tab.getPosition()) {
                    case 0: binding.layoutRoadmapTab.setVisibility(View.VISIBLE); break;
                    case 1: binding.layoutOverviewTab.setVisibility(View.VISIBLE); break;
                    case 2: binding.layoutSessionsTab.setVisibility(View.VISIBLE); break;
                }
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupRecyclerViews() {
        roadmapTodayAdapter = new RoadmapAdapter(todayStepList, RoadmapAdapter.VIEW_TYPE_TODAY);
        binding.recyclerRoadmap.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerRoadmap.setAdapter(roadmapTodayAdapter);

        roadmapOverviewAdapter = new RoadmapAdapter(allStepsList, RoadmapAdapter.VIEW_TYPE_OVERVIEW);
        binding.recyclerOverviewUnits.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerOverviewUnits.setAdapter(roadmapOverviewAdapter);

        sessionAdapter = new SessionAdapter(new ArrayList<>());
        binding.recyclerSessions.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerSessions.setAdapter(sessionAdapter);
    }

    private void loadStudySessions() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null && courseId != null) {
            studyPlanRepository.getSchedule(uid, courseId).observe(this, sessions -> {
                if (sessions != null && !sessions.isEmpty()) {
                    sessionAdapter.setSessions(sessions);
                }
            });
        }
    }

    private void setupRealTimeCalendar() {
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int activeColor = ContextCompat.getColor(this, R.color.brand_primary);
        View todayLayout = null;
        TextView todayText = null;

        switch (dayOfWeek) {
            case Calendar.MONDAY: todayLayout = findViewById(R.id.layout_mon); todayText = findViewById(R.id.text_mon); break;
            case Calendar.TUESDAY: todayLayout = findViewById(R.id.layout_tue); todayText = findViewById(R.id.text_tue); break;
            case Calendar.WEDNESDAY: todayLayout = findViewById(R.id.layout_wed); todayText = findViewById(R.id.text_wed); break;
            case Calendar.THURSDAY: todayLayout = findViewById(R.id.layout_thu); todayText = findViewById(R.id.text_thu); break;
            case Calendar.FRIDAY: todayLayout = findViewById(R.id.layout_fri); todayText = findViewById(R.id.text_fri); break;
            case Calendar.SATURDAY: todayLayout = findViewById(R.id.layout_sat); todayText = findViewById(R.id.text_sat); break;
            case Calendar.SUNDAY: todayLayout = findViewById(R.id.layout_sun); todayText = findViewById(R.id.text_sun); break;
        }

        if (todayLayout != null && todayText != null) {
            todayLayout.setBackgroundResource(R.drawable.bg_circle_glass);
            todayText.setTextColor(activeColor);
            todayText.setTypeface(null, Typeface.BOLD);
        }
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
                    if (courseId == null) findLatestCourse();
                    else fetchCourseDetails();
                });
    }

    private void fetchCourseDetails() {
        String uid = FirebaseAuth.getInstance().getUid();
        Task<DocumentSnapshot> courseTask;
        if (isPersonal && uid != null) {
            courseTask = db.collection("users").document(uid).collection("personal_courses").document(courseId).get();
        } else {
            courseTask = db.collection("courses").document(courseId).get();
        }

        courseTask.addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String title = doc.getString("title");
                if (title != null) {
                    binding.textCourseName.setText(title.replace("Lộ trình ", "").split(" - ")[0]);
                }
                String desc = doc.getString("description");
                if (desc != null) binding.textCourseDescriptionTab.setText(desc);
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
                        loadStudySessions();
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
                fetchChallengesAndBuildRoadmap(allLessons);
            });
        });
    }

    private void fetchChallengesAndBuildRoadmap(List<LessonWithChallenges> lessons) {
        if (lessons.isEmpty()) {
            binding.progressRoadmap.setVisibility(View.GONE);
            return;
        }

        List<Task<QuerySnapshot>> challengeTasks = new ArrayList<>();
        for (LessonWithChallenges lc : lessons) {
            challengeTasks.add(lc.lessonDoc.getReference().collection("challenges").get().continueWithTask(task -> {
                if (task.isSuccessful() && !task.getResult().isEmpty()) return task;
                return db.collection("challenges").whereEqualTo("lessonId", lc.lessonDoc.getId()).get();
            }));
        }

        Tasks.whenAllComplete(challengeTasks).addOnCompleteListener(t -> {
            allStepsList.clear();
            todayStepList.clear();
            boolean foundActive = false;
            String lastUnitId = "";
            int lessonIndexInUnit = 0;

            for (int i = 0; i < lessons.size(); i++) {
                LessonWithChallenges lc = lessons.get(i);
                Task<QuerySnapshot> task = challengeTasks.get(i);
                int totalChallenges = 0, completedCount = 0;
                if (task.isSuccessful() && task.getResult() != null) {
                    for (DocumentSnapshot challengeDoc : task.getResult()) {
                        totalChallenges++;
                        if (completedChallenges.contains(challengeDoc.getId())) completedCount++;
                    }
                }

                if (totalChallenges == 0) totalChallenges = 1;
                boolean isCompleted = (completedCount >= totalChallenges);
                boolean isLocked = false, isActive = false;

                if (!isCompleted && !foundActive) {
                    isActive = true; foundActive = true;
                    todayStepList.add(new RoadmapStep(lc.lessonDoc.getId(), lc.unitTitle, lc.lessonDoc.getString("title"), 
                        completedCount + "/" + totalChallenges + " Thử thách hoàn thành", 
                        (lessonIndexInUnit == 0) ? R.drawable.start : (lessonIndexInUnit == 1 ? R.drawable.speedup : R.drawable.finish), 
                        false, false, lc.lessonDoc.getString("type")));
                } else if (foundActive) isLocked = true;

                String currentUnitId = lc.unitDoc.getId();
                if (!currentUnitId.equals(lastUnitId)) { lastUnitId = currentUnitId; lessonIndexInUnit = 0; }
                else lessonIndexInUnit++;

                allStepsList.add(new RoadmapStep(lc.lessonDoc.getId(), lc.unitTitle, lc.lessonDoc.getString("title"), 
                    completedCount + "/" + totalChallenges + " Challenges", 
                    (lessonIndexInUnit == 0) ? R.drawable.start : (lessonIndexInUnit == 1 ? R.drawable.speedup : R.drawable.finish), 
                    isLocked, isCompleted, lc.lessonDoc.getString("type")));
            }
            
            roadmapTodayAdapter.notifyDataSetChanged();
            roadmapOverviewAdapter.notifyDataSetChanged();
            binding.progressRoadmap.setVisibility(View.GONE);
        });
    }

    private void deleteCourse() {
        if (courseId == null) return;
        String uid = FirebaseAuth.getInstance().getUid();
        Task<Void> deleteTask = (isPersonal && uid != null) ? 
                db.collection("users").document(uid).collection("personal_courses").document(courseId).delete() :
                db.collection("courses").document(courseId).delete();

        deleteTask.addOnSuccessListener(aVoid -> {
            if (uid != null) db.collection("users").document(uid).update("activeCourseId", null);
            finish();
        });
    }

    private static class LessonWithChallenges {
        DocumentSnapshot lessonDoc; DocumentSnapshot unitDoc; String unitTitle;
        LessonWithChallenges(DocumentSnapshot ld, DocumentSnapshot ud) {
            this.lessonDoc = ld; this.unitDoc = ud; this.unitTitle = ud != null ? ud.getString("title") : "";
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_course_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        else if (item.getItemId() == R.id.action_delete_course) { 
            new AlertDialog.Builder(this).setTitle("Xóa khóa học").setMessage("Bạn có chắc không?")
                .setPositiveButton("Xóa", (d, w) -> deleteCourse()).setNegativeButton("Hủy", null).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
