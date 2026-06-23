package com.example.vocabmaster.ui.library;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private Set<Long> plannedStudyDateKeys = new HashSet<>();
    private Set<Long> completedStudyDateKeys = new HashSet<>();
    private boolean scheduleLoaded = false;
    private StudyPlanRepository studyPlanRepository;

    // Calendar state for dialog
    private Calendar currentDisplayMonth = Calendar.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCourseDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        studyPlanRepository = new StudyPlanRepository(getApplication());
        courseId = getIntent().getStringExtra("course_id");
        isPersonal = getIntent().getBooleanExtra("is_personal", false);
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        setupTabs();
        setupRecyclerViews();
        setupRealTimeCalendar();
        View weeklyCalendar = findViewById(R.id.calendar_include);
        if (weeklyCalendar != null) {
            weeklyCalendar.setOnClickListener(v -> showMonthlyStudyCalendar());
        }
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
        TextView todayDateText = null;

        switch (dayOfWeek) {
            case Calendar.MONDAY: todayLayout = findViewById(R.id.layout_mon); todayText = findViewById(R.id.text_mon); todayDateText = findViewById(R.id.text_mon_date); break;
            case Calendar.TUESDAY: todayLayout = findViewById(R.id.layout_tue); todayText = findViewById(R.id.text_tue); todayDateText = findViewById(R.id.text_tue_date); break;
            case Calendar.WEDNESDAY: todayLayout = findViewById(R.id.layout_wed); todayText = findViewById(R.id.text_wed); todayDateText = findViewById(R.id.text_wed_date); break;
            case Calendar.THURSDAY: todayLayout = findViewById(R.id.layout_thu); todayText = findViewById(R.id.text_thu); todayDateText = findViewById(R.id.text_thu_date); break;
            case Calendar.FRIDAY: todayLayout = findViewById(R.id.layout_fri); todayText = findViewById(R.id.text_fri); todayDateText = findViewById(R.id.text_fri_date); break;
            case Calendar.SATURDAY: todayLayout = findViewById(R.id.layout_sat); todayText = findViewById(R.id.text_sat); todayDateText = findViewById(R.id.text_sat_date); break;
            case Calendar.SUNDAY: todayLayout = findViewById(R.id.layout_sun); todayText = findViewById(R.id.text_sun); todayDateText = findViewById(R.id.text_sun_date); break;
        }

        if (todayLayout != null && todayText != null) {
            todayText.setTextColor(activeColor);
            todayText.setTypeface(null, Typeface.BOLD);
            if (todayDateText != null) {
                todayDateText.setTextColor(activeColor);
                todayDateText.setTypeface(null, Typeface.NORMAL);
            }
        }
    }

    private void resetWeeklyCalendar() {
        populateWeeklyCalendarDates();

        int defaultTextColor = ContextCompat.getColor(this, R.color.text_secondary);
        int defaultDateColor = ContextCompat.getColor(this, R.color.text_primary);
        int[] layoutIds = {
                R.id.layout_mon, R.id.layout_tue, R.id.layout_wed, R.id.layout_thu,
                R.id.layout_fri, R.id.layout_sat, R.id.layout_sun
        };
        int[] dayTextIds = {
                R.id.text_mon, R.id.text_tue, R.id.text_wed, R.id.text_thu,
                R.id.text_fri, R.id.text_sat, R.id.text_sun
        };
        int[] dateTextIds = {
                R.id.text_mon_date, R.id.text_tue_date, R.id.text_wed_date, R.id.text_thu_date,
                R.id.text_fri_date, R.id.text_sat_date, R.id.text_sun_date
        };
        int[] trophyIds = {
                R.id.check_mon, R.id.check_tue, R.id.check_wed, R.id.check_thu,
                R.id.check_fri, R.id.check_sat, R.id.check_sun
        };

        for (int id : layoutIds) {
            View dayLayout = findViewById(id);
            if (dayLayout != null) dayLayout.setBackground(null);
        }
        for (int id : dayTextIds) {
            TextView dayText = findViewById(id);
            if (dayText != null) {
                dayText.setTextColor(defaultTextColor);
                dayText.setTypeface(null, Typeface.BOLD);
            }
        }
        for (int id : dateTextIds) {
            TextView dateText = findViewById(id);
            if (dateText != null) {
                dateText.setTextColor(defaultDateColor);
                dateText.setTypeface(null, Typeface.NORMAL);
            }
        }
        for (int id : trophyIds) {
            ImageView trophy = findViewById(id);
            if (trophy != null) {
                trophy.setVisibility(View.INVISIBLE);
                trophy.setAlpha(1.0f);
                trophy.setImageResource(R.drawable.studyunit);
                trophy.clearColorFilter();
            }
        }
    }

    private void populateWeeklyCalendarDates() {
        Calendar weekDay = Calendar.getInstance();
        weekDay.setFirstDayOfWeek(Calendar.MONDAY);
        weekDay.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        resetTime(weekDay);

        int[] dateTextIds = {
                R.id.text_mon_date, R.id.text_tue_date, R.id.text_wed_date, R.id.text_thu_date,
                R.id.text_fri_date, R.id.text_sat_date, R.id.text_sun_date
        };
        for (int id : dateTextIds) {
            TextView dateText = findViewById(id);
            if (dateText != null) {
                dateText.setText(String.valueOf(weekDay.get(Calendar.DAY_OF_MONTH)));
            }
            weekDay.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    private void updateWeeklyCalendar(List<CourseScheduleDay> sessions) {
        setupRealTimeCalendar();
        plannedStudyDateKeys.clear();
        completedStudyDateKeys.clear();

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
            resetTime(sessionCal);
            long dateKey = sessionCal.getTimeInMillis();
            plannedStudyDateKeys.add(dateKey);
            if ("completed".equals(session.getStatus())) {
                completedStudyDateKeys.add(dateKey);
            }
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
            if (completedDaysThisWeek.contains(dayOfWeek)) {
                trophy.setImageResource(R.drawable.trophy);
                trophy.setAlpha(1.0f);
                trophy.clearColorFilter();
            } else {
                trophy.setImageResource(R.drawable.studyunit);
                trophy.setAlpha(1.0f);
                trophy.clearColorFilter();
            }
        }
    }

    private void resetTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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

    private void showMonthlyStudyCalendar() {
        currentDisplayMonth = Calendar.getInstance();
        resetTime(currentDisplayMonth);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_monthly_calendar, null);
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.VocabMaster_Dialog_Transparent)
                .setView(dialogView)
                .create();

        TextView textMonthYear = dialogView.findViewById(R.id.text_month_year);
        GridLayout calendarGrid = dialogView.findViewById(R.id.calendar_grid);
        ImageView btnPrev = dialogView.findViewById(R.id.btn_prev_month);
        ImageView btnNext = dialogView.findViewById(R.id.btn_next_month);
        ImageView btnClose = dialogView.findViewById(R.id.btn_close_calendar);
        TextView textCurrentStreak = dialogView.findViewById(R.id.text_current_streak);
        TextView textLongestStreak = dialogView.findViewById(R.id.text_longest_streak);

        updateCalendarDialog(textMonthYear, calendarGrid);

        btnPrev.setOnClickListener(v -> {
            currentDisplayMonth.add(Calendar.MONTH, -1);
            updateCalendarDialog(textMonthYear, calendarGrid);
        });

        btnNext.setOnClickListener(v -> {
            currentDisplayMonth.add(Calendar.MONTH, 1);
            updateCalendarDialog(textMonthYear, calendarGrid);
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener(snapshot -> {
                Long streak = snapshot.getLong("streak");
                Long longestStreak = snapshot.getLong("longestStreak");
                textCurrentStreak.setText(String.valueOf(streak == null ? 0 : streak));
                long longest = longestStreak == null ? 0 : longestStreak;
                textLongestStreak.setText(longest + " ngày");
            });
        }

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.94f);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void updateCalendarDialog(TextView textMonthYear, GridLayout calendarGrid) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        textMonthYear.setText(sdf.format(currentDisplayMonth.getTime()));

        calendarGrid.removeAllViews();
        calendarGrid.setColumnCount(7);
        calendarGrid.setRowCount(7); // Weekday header + six calendar weeks.

        // Add Weekday Headers
        String[] weekdays = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
        for (int column = 0; column < weekdays.length; column++) {
            TextView header = new TextView(this);
            header.setText(weekdays[column]);
            header.setGravity(android.view.Gravity.CENTER);
            header.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            header.setPadding(0, dp(10), 0, dp(10));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(0), GridLayout.spec(column, 1f));
            params.width = 0;
            calendarGrid.addView(header, params);
        }

        Calendar cal = (Calendar) currentDisplayMonth.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // Sunday=1, Monday=2
        int offset = (firstDayOfWeek + 5) % 7; // Adjust to Monday start (0=Mon, 6=Sun)

        // Empty cells for leading days
        for (int i = 0; i < offset; i++) {
            addEmptyCalendarCell(calendarGrid, i);
        }

        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar today = Calendar.getInstance();
        resetTime(today);

        for (int day = 1; day <= daysInMonth; day++) {
            View dayView = LayoutInflater.from(this).inflate(R.layout.item_calendar_day, calendarGrid, false);
            TextView textDay = dayView.findViewById(R.id.text_day);
            ImageView imgIndicator = dayView.findViewById(R.id.img_indicator);

            textDay.setText(String.valueOf(day));
            cal.set(Calendar.DAY_OF_MONTH, day);
            long timeKey = cal.getTimeInMillis();

            // Hide icon as requested
            imgIndicator.setVisibility(View.GONE);

            if (cal.getTimeInMillis() == today.getTimeInMillis()) {
                // Today: thin orange outline, matching the monthly streak calendar.
                textDay.setBackgroundResource(R.drawable.bg_calendar_day_today);
                textDay.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                textDay.setTypeface(null, Typeface.BOLD);
            } else if (completedStudyDateKeys.contains(timeKey)) {
                textDay.setBackgroundResource(R.drawable.bg_calendar_day);
                textDay.setTypeface(null, Typeface.BOLD);
                textDay.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            } else if (plannedStudyDateKeys.contains(timeKey)) {
                textDay.setBackgroundResource(R.drawable.bg_calendar_lesson_planned);
                textDay.setTypeface(null, Typeface.NORMAL);
                textDay.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            } else if (cal.before(today)) {
                textDay.setBackgroundResource(R.drawable.bg_calendar_day);
                textDay.setTypeface(null, Typeface.NORMAL);
                textDay.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            } else {
                textDay.setBackground(null);
                textDay.setTypeface(null, Typeface.NORMAL);
                textDay.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            }

            int cellIndex = offset + day - 1;
            GridLayout.LayoutParams dayParams = new GridLayout.LayoutParams(
                    GridLayout.spec(1 + cellIndex / 7),
                    GridLayout.spec(cellIndex % 7, 1f));
            dayParams.width = 0;
            dayView.setLayoutParams(dayParams);
            calendarGrid.addView(dayView);
        }

        // Keep all months the same height so the dialog does not jump while navigating.
        int usedCells = offset + daysInMonth;
        for (int cellIndex = usedCells; cellIndex < 42; cellIndex++) {
            addEmptyCalendarCell(calendarGrid, cellIndex);
        }
    }

    private void addEmptyCalendarCell(GridLayout calendarGrid, int cellIndex) {
        View emptyCell = new View(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(1 + cellIndex / 7),
                GridLayout.spec(cellIndex % 7, 1f));
        params.width = 0;
        params.height = dp(44);
        calendarGrid.addView(emptyCell, params);
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
                        if (!"INTRO".equalsIgnoreCase(doc.getString("type"))) {
                            completedChallenges.add(doc.getString("challengeId"));
                        }
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
            updateCourseOverviewProgress(0, 0);
            binding.progressRoadmap.setVisibility(View.GONE);
            return;
        }

        List<Task<LessonChallengeSummary>> challengeTasks = new ArrayList<>();
        for (LessonWithChallenges lc : lessons) {
            Object cachedIds = lc.lessonDoc.get("challengeIds");
            if (cachedIds instanceof List) {
                List<String> ids = new ArrayList<>();
                for (Object id : (List<?>) cachedIds) {
                    if (id != null) ids.add(String.valueOf(id));
                }
                challengeTasks.add(Tasks.forResult(new LessonChallengeSummary(ids)));
                continue;
            }

            Task<QuerySnapshot> challengeQuery = lc.lessonDoc.getReference().collection("challenges").get().continueWithTask(task -> {
                if (task.isSuccessful() && !task.getResult().isEmpty()) return task;
                return db.collection("challenges").whereEqualTo("lessonId", lc.lessonDoc.getId()).get();
            });
            challengeTasks.add(challengeQuery.continueWith(task -> {
                List<String> ids = new ArrayList<>();
                if (task.isSuccessful() && task.getResult() != null) {
                    for (DocumentSnapshot challengeDoc : task.getResult()) {
                        if (!"INTRO".equalsIgnoreCase(challengeDoc.getString("type"))) {
                            ids.add(challengeDoc.getId());
                        }
                    }
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("challengeIds", ids);
                    metadata.put("challengeCount", ids.size());
                    lc.lessonDoc.getReference().update(metadata);
                }
                return new LessonChallengeSummary(ids);
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
            int completedLessons = 0;

            for (int i = 0; i < lessons.size(); i++) {
                LessonWithChallenges lc = lessons.get(i);
                Task<LessonChallengeSummary> task = challengeTasks.get(i);
                int totalChallenges = 0, completedCount = 0;
                if (task.isSuccessful() && task.getResult() != null) {
                    for (String challengeId : task.getResult().challengeIds) {
                        totalChallenges++;
                        if (completedChallenges.contains(challengeId)) completedCount++;
                    }
                }

                if (totalChallenges == 0) totalChallenges = 1;
                Boolean completedField = lc.lessonDoc.getBoolean("completed");
                Boolean isCompletedField = lc.lessonDoc.getBoolean("isCompleted");
                boolean lessonMarkedCompleted = Boolean.TRUE.equals(completedField) || Boolean.TRUE.equals(isCompletedField);
                boolean isCompleted = lessonMarkedCompleted || (completedCount >= totalChallenges);
                int unlockThreshold = Math.max(1, (int) Math.ceil(totalChallenges * 0.7f));
                boolean unlockRequirementMet = lessonMarkedCompleted || completedCount >= unlockThreshold;
                if (isCompleted) completedLessons++;
                if (lessonMarkedCompleted && completedCount < totalChallenges) {
                    completedCount = totalChallenges;
                }
                int progressPercent = Math.round((completedCount * 100f) / totalChallenges);
                int cupsEarned = progressPercent >= 100 ? 3 : (progressPercent >= 67 ? 2 : (progressPercent > 0 ? 1 : 0));
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
                        isCompleted ? "Đã hoàn thành bài học hôm nay" : "Bài học được lên lịch hôm nay", 
                        isCompleted ? R.drawable.ic_check : R.drawable.ebook, 
                        false, isCompleted, lc.lessonDoc.getString("type"), xpPoints,
                        progressPercent, completedCount, totalChallenges, cupsEarned));
                }

                if (!unlockRequirementMet && !foundActive) {
                    isActive = true; foundActive = true;
                    todayFallbackStep = new RoadmapStep(docLessonId, lc.unitTitle, lc.lessonDoc.getString("title"),
                            "Bài học tiếp theo dành cho bạn",
                            R.drawable.ebook,
                            false, false, lc.lessonDoc.getString("type"), xpPoints,
                            progressPercent, completedCount, totalChallenges, cupsEarned);
                } else if (foundActive) isLocked = true;

                String currentUnitId = lc.unitDoc.getId();
                if (!currentUnitId.equals(lastUnitId)) { lastUnitId = currentUnitId; lessonIndexInUnit = 0; }
                else lessonIndexInUnit++;

                allStepsList.add(new RoadmapStep(lc.lessonDoc.getId(), lc.unitTitle, lc.lessonDoc.getString("title"), 
                    completedCount + "/" + totalChallenges + " Challenges", 
                    (lessonIndexInUnit == 0) ? R.drawable.start : (lessonIndexInUnit == 1 ? R.drawable.speedup : R.drawable.finish), 
                    isLocked, isCompleted, lc.lessonDoc.getString("type")));
            }

            updateCourseOverviewProgress(completedLessons, lessons.size());

            if (todayStepList.isEmpty() && todayFallbackStep != null) {
                todayStepList.add(todayFallbackStep);
            }
            
            sessionAdapter.setLessonInfoMap(lessonInfoMap);
            roadmapTodayAdapter.notifyDataSetChanged();
            roadmapOverviewAdapter.notifyDataSetChanged();
            binding.progressRoadmap.setVisibility(View.GONE);
        });
    }

    private void updateCourseOverviewProgress(int completedLessons, int totalLessons) {
        int safeTotal = Math.max(1, totalLessons);
        int clampedCompleted = Math.max(0, Math.min(completedLessons, safeTotal));
        float percent = (clampedCompleted * 100f) / safeTotal;
        
        // Update values
        binding.textCourseCurrentValue.setText(String.valueOf(clampedCompleted));
        binding.textCourseTotalValue.setText(String.valueOf(safeTotal));
        binding.textCourseStartValue.setText("0");
        binding.labelMid.setText(Math.round(percent) + "%");

        // Move dot and current value together
        ConstraintLayout.LayoutParams paramsDot = (ConstraintLayout.LayoutParams) binding.dotMid.getLayoutParams();
        ConstraintLayout.LayoutParams paramsValue = (ConstraintLayout.LayoutParams) binding.textCourseCurrentValue.getLayoutParams();
        
        float bias = (float) clampedCompleted / safeTotal;
        paramsDot.horizontalBias = bias;
        paramsValue.horizontalBias = bias;
        
        binding.dotMid.setLayoutParams(paramsDot);
        binding.textCourseCurrentValue.setLayoutParams(paramsValue);

        // Keep legacy for safety
        binding.progressCourseOverview.setProgress(Math.round(percent));
        binding.textCourseOverviewProgress.setText(Math.round(percent) + "% complete");
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

    private static class LessonChallengeSummary {
        final List<String> challengeIds;
        LessonChallengeSummary(List<String> challengeIds) {
            this.challengeIds = challengeIds == null ? new ArrayList<>() : challengeIds;
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
