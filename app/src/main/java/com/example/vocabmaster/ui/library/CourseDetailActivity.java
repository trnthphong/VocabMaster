package com.example.vocabmaster.ui.library;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.vocabmaster.MainActivity;
import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.CourseScheduleDay;
import com.example.vocabmaster.data.repository.StudyPlanRepository;
import com.example.vocabmaster.databinding.ActivityCourseDetailBinding;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CourseDetailActivity extends AppCompatActivity {
    private static final String TAG = "CourseDetailActivity";
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
    private Set<String> todayLessonIds = new HashSet<>();
    private boolean scheduleLoaded = false;
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

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null && db != null) {
            loadUserDataAndRoadmap();
        }
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
        roadmapTodayAdapter.setCourseId(courseId);
        binding.recyclerRoadmap.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerRoadmap.setAdapter(roadmapTodayAdapter);

        roadmapOverviewAdapter = new RoadmapAdapter(allStepsList, RoadmapAdapter.VIEW_TYPE_OVERVIEW);
        roadmapOverviewAdapter.setCourseId(courseId);
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
                    scheduleLoaded = true;
                    updateTodayLessonIds(sessions);
                    sessionAdapter.setSessions(sessions);
                    updateWeeklyCalendar(sessions);
                    loadUserDataAndRoadmap();
                }
            });
        }
    }

    private void updateTodayLessonIds(List<CourseScheduleDay> sessions) {
        todayLessonIds.clear();
        Calendar today = Calendar.getInstance();
        resetTime(today);
        for (CourseScheduleDay session : sessions) {
            Date date = session.getDate();
            if (date == null) continue;
            Calendar sessionDay = Calendar.getInstance();
            sessionDay.setTime(date);
            resetTime(sessionDay);
            if (sessionDay.getTimeInMillis() == today.getTimeInMillis()
                    && session.getLessonIds() != null) {
                todayLessonIds.addAll(session.getLessonIds());
            }
        }
    }

    private void setupRealTimeCalendar() {
        resetWeeklyCalendar();

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

    private void resetWeeklyCalendar() {
        int[] trophyIds = {
                R.id.check_mon, R.id.check_tue, R.id.check_wed, R.id.check_thu,
                R.id.check_fri, R.id.check_sat, R.id.check_sun
        };

        for (int id : trophyIds) {
            ImageView trophy = findViewById(id);
            if (trophy != null) {
                trophy.setVisibility(View.INVISIBLE);
                trophy.setAlpha(1.0f);
                trophy.setImageResource(R.drawable.trophy);
                trophy.clearColorFilter();
            }
        }
    }

    private void updateWeeklyCalendar(List<CourseScheduleDay> sessions) {
        setupRealTimeCalendar();

        Calendar weekStart = Calendar.getInstance();
        weekStart.setFirstDayOfWeek(Calendar.MONDAY);
        weekStart.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        resetTime(weekStart);

        Calendar weekEnd = (Calendar) weekStart.clone();
        weekEnd.add(Calendar.DAY_OF_YEAR, 7);

        Set<Integer> plannedDays = new HashSet<>();
        Set<Integer> completedDaysThisWeek = new HashSet<>();

        for (CourseScheduleDay session : sessions) {
            Date date = session.getDate();
            if (date == null) continue;

            Calendar sessionCal = Calendar.getInstance();
            sessionCal.setTime(date);
            int dayOfWeek = sessionCal.get(Calendar.DAY_OF_WEEK);

            if (!date.before(weekStart.getTime())
                    && date.before(weekEnd.getTime())) {
                plannedDays.add(dayOfWeek);
                if ("completed".equals(session.getStatus())) {
                    completedDaysThisWeek.add(dayOfWeek);
                }
            }
        }

        for (Integer dayOfWeek : plannedDays) {
            ImageView trophy = findViewById(getTrophyIdForDay(dayOfWeek));
            if (trophy == null) continue;

            trophy.setVisibility(View.VISIBLE);
            trophy.setImageResource(R.drawable.trophy);
            if (completedDaysThisWeek.contains(dayOfWeek)) {
                trophy.setAlpha(1.0f);
                trophy.setColorFilter(ContextCompat.getColor(this, R.color.warning));
            } else {
                trophy.setAlpha(0.75f);
                trophy.setColorFilter(ContextCompat.getColor(this, R.color.gray_text));
            }
        }
    }

    private void resetTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private int getTrophyIdForDay(int dayOfWeek) {
        switch (dayOfWeek) {
            case Calendar.MONDAY: return R.id.check_mon;
            case Calendar.TUESDAY: return R.id.check_tue;
            case Calendar.WEDNESDAY: return R.id.check_wed;
            case Calendar.THURSDAY: return R.id.check_thu;
            case Calendar.FRIDAY: return R.id.check_fri;
            case Calendar.SATURDAY: return R.id.check_sat;
            case Calendar.SUNDAY: return R.id.check_sun;
            default: return R.id.check_mon;
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
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        courseId = querySnapshot.getDocuments().get(0).getId();
                        isPersonal = true;
                        roadmapTodayAdapter.setCourseId(courseId);
                        roadmapOverviewAdapter.setCourseId(courseId);
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
            roadmapTodayAdapter.setCourseId(courseId);
            roadmapOverviewAdapter.setCourseId(courseId);
            allStepsList.clear();
            todayStepList.clear();
            Map<String, SessionAdapter.LessonSessionInfo> lessonInfoMap = new HashMap<>();
            boolean foundActive = false;
            RoadmapStep todayFallbackStep = null;
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
                Boolean completedField = lc.lessonDoc.getBoolean("completed");
                Boolean isCompletedField = lc.lessonDoc.getBoolean("isCompleted");
                boolean lessonMarkedCompleted = Boolean.TRUE.equals(completedField) || Boolean.TRUE.equals(isCompletedField);
                boolean isCompleted = lessonMarkedCompleted || (completedCount >= totalChallenges);
                if (lessonMarkedCompleted && completedCount < totalChallenges) {
                    completedCount = totalChallenges;
                }
                int progressPercent = Math.round((completedCount * 100f) / totalChallenges);
                Long xpLong = lc.lessonDoc.getLong("xpPoints");
                int xpPoints = xpLong == null ? 10 : xpLong.intValue();
                String docLessonId = lc.lessonDoc.getId();
                String fieldLessonId = lc.lessonDoc.getString("lessonId");
                lessonInfoMap.put(docLessonId, new SessionAdapter.LessonSessionInfo(
                        lc.lessonDoc.getString("title"),
                        lc.lessonDoc.getString("type"),
                        progressPercent,
                        xpPoints
                ));
                boolean isLocked = false, isActive = false;

                boolean isScheduledToday = scheduleLoaded
                        && (todayLessonIds.contains(docLessonId)
                        || (fieldLessonId != null && todayLessonIds.contains(fieldLessonId)));
                if (isScheduledToday) {
                    todayStepList.add(new RoadmapStep(docLessonId, lc.unitTitle, lc.lessonDoc.getString("title"), 
                        isCompleted ? "Đã hoàn thành" : "Nhấn để hoàn thành bài học hôm nay", 
                        isCompleted ? R.drawable.ic_check : ((lessonIndexInUnit == 0) ? R.drawable.start : (lessonIndexInUnit == 1 ? R.drawable.speedup : R.drawable.finish)), 
                        false, isCompleted, lc.lessonDoc.getString("type")));
                }

                if (!isCompleted && !foundActive) {
                    isActive = true; foundActive = true;
                    todayFallbackStep = new RoadmapStep(docLessonId, lc.unitTitle, lc.lessonDoc.getString("title"),
                            "Nhấn để hoàn thành bài học hôm nay",
                            (lessonIndexInUnit == 0) ? R.drawable.start : (lessonIndexInUnit == 1 ? R.drawable.speedup : R.drawable.finish),
                            false, false, lc.lessonDoc.getString("type"));
                } else if (foundActive) isLocked = true;

                String currentUnitId = lc.unitDoc.getId();
                if (!currentUnitId.equals(lastUnitId)) { lastUnitId = currentUnitId; lessonIndexInUnit = 0; }
                else lessonIndexInUnit++;

                allStepsList.add(new RoadmapStep(lc.lessonDoc.getId(), lc.unitTitle, lc.lessonDoc.getString("title"), 
                    completedCount + "/" + totalChallenges + " Challenges", 
                    (lessonIndexInUnit == 0) ? R.drawable.start : (lessonIndexInUnit == 1 ? R.drawable.speedup : R.drawable.finish), 
                    isLocked, isCompleted, lc.lessonDoc.getString("type")));
            }

            if (todayStepList.isEmpty() && todayFallbackStep != null) {
                todayStepList.add(todayFallbackStep);
            }
            
            sessionAdapter.setLessonInfoMap(lessonInfoMap);
            roadmapTodayAdapter.notifyDataSetChanged();
            roadmapOverviewAdapter.notifyDataSetChanged();
            binding.progressRoadmap.setVisibility(View.GONE);
        });
    }

    private void deleteCourse() {
        if (courseId == null) return;
        String uid = FirebaseAuth.getInstance().getUid();
        if (isPersonal && uid != null) {
            DocumentReference courseRef = db.collection("users").document(uid).collection("personal_courses").document(courseId);
            courseRef.delete()
                    .addOnSuccessListener(aVoid -> {
                        clearActiveCourseIfNeeded(uid, courseId);
                        cleanupDeletedCourseInBackground(courseRef);
                        Toast.makeText(this, "Đã xóa khóa học", Toast.LENGTH_SHORT).show();
                        goBackToLibrary();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Delete personal course failed. courseId=" + courseId, e);
                        Toast.makeText(this, "Lỗi khi xóa khóa học: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        } else {
            db.collection("courses").document(courseId).delete()
                    .addOnSuccessListener(aVoid -> goBackToLibrary())
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Delete public course failed. courseId=" + courseId, e);
                        Toast.makeText(this, "Lỗi khi xóa khóa học: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        }
    }

    private void clearActiveCourseIfNeeded(String uid, String deletedCourseId) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {
                    String activeCourseId = userDoc.getString("activeCourseId");
                    if (deletedCourseId.equals(activeCourseId)) {
                        db.collection("users").document(uid).update("activeCourseId", null);
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Active course cleanup skipped. courseId=" + deletedCourseId, e));
    }

    private void cleanupDeletedCourseInBackground(DocumentReference courseRef) {
        deletePersonalCourseTree(courseRef)
                .addOnFailureListener(e -> Log.w(TAG, "Background course subtree cleanup failed. courseId=" + courseId, e));
    }

    private void goBackToLibrary() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("navigate_to_library", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private Task<Void> deletePersonalCourseTree(DocumentReference courseRef) {
        return courseRef.collection("units").get().continueWithTask(unitTask -> {
            List<Task<Void>> deleteTasks = new ArrayList<>();
            if (!unitTask.isSuccessful() || unitTask.getResult() == null) {
                return Tasks.whenAll(deleteTasks);
            }

            for (DocumentSnapshot unitDoc : unitTask.getResult()) {
                deleteTasks.add(unitDoc.getReference().collection("lessons").get().continueWithTask(lessonTask -> {
                    List<Task<Void>> lessonDeleteTasks = new ArrayList<>();
                    if (lessonTask.isSuccessful() && lessonTask.getResult() != null) {
                        for (DocumentSnapshot lessonDoc : lessonTask.getResult()) {
                            lessonDeleteTasks.add(deleteLessonChallenges(lessonDoc));
                            lessonDeleteTasks.add(lessonDoc.getReference().delete());
                        }
                    }
                    return Tasks.whenAll(lessonDeleteTasks).continueWithTask(done -> unitDoc.getReference().delete());
                }));
            }

            return Tasks.whenAll(deleteTasks);
        });
    }

    private Task<Void> deleteLessonChallenges(DocumentSnapshot lessonDoc) {
        String lessonId = lessonDoc.getId();
        Task<Void> subcollectionDelete = lessonDoc.getReference().collection("challenges").get().continueWithTask(task -> {
            List<Task<Void>> deletes = new ArrayList<>();
            if (task.isSuccessful() && task.getResult() != null) {
                for (DocumentSnapshot challengeDoc : task.getResult()) {
                    deletes.add(challengeDoc.getReference().delete());
                }
            }
            return Tasks.whenAll(deletes);
        });

        Task<Void> globalDelete = db.collection("challenges").whereEqualTo("lessonId", lessonId).get().continueWithTask(task -> {
            List<Task<Void>> deletes = new ArrayList<>();
            if (task.isSuccessful() && task.getResult() != null) {
                for (DocumentSnapshot challengeDoc : task.getResult()) {
                    deletes.add(challengeDoc.getReference().delete());
                }
            }
            return Tasks.whenAll(deletes);
        }).continueWith(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "Global challenge cleanup skipped for lessonId=" + lessonId, task.getException());
            }
            return null;
        });

        return Tasks.whenAll(subcollectionDelete, globalDelete);
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
