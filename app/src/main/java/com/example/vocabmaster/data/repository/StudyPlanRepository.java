package com.example.vocabmaster.data.repository;

import android.app.Application;
import androidx.lifecycle.LiveData;
import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.local.CourseDao;
import com.example.vocabmaster.data.local.CourseScheduleDayDao;
import com.example.vocabmaster.data.local.StudyPlanDao;
import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.CourseScheduleDay;
import com.example.vocabmaster.data.model.Lesson;
import com.example.vocabmaster.data.model.StudyPlan;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StudyPlanRepository {
    private final StudyPlanDao studyPlanDao;
    private final CourseScheduleDayDao courseScheduleDayDao;
    private final CourseDao courseDao;
    private final ExecutorService executorService;

    public StudyPlanRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        studyPlanDao = db.studyPlanDao();
        courseScheduleDayDao = db.courseScheduleDayDao();
        courseDao = db.courseDao();
        executorService = Executors.newFixedThreadPool(4);
    }

    public void createStudyPlan(StudyPlan plan, List<Lesson> allLessons, Runnable callback) {
        executorService.execute(() -> {
            studyPlanDao.insert(plan);
            generateSchedule(plan, allLessons);
            if (callback != null) callback.run();
        });
    }

    private void generateSchedule(StudyPlan plan, List<Lesson> allLessons) {
        List<CourseScheduleDay> scheduleDays = new ArrayList<>();
        int avgLessonDuration = 12; 
        int lessonsPerSession = Math.max(1, plan.getDailyMinutes() / avgLessonDuration);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(plan.getStartDate());
        int lessonIndex = 0;
        int totalLessons = allLessons.size();
        int sessionsPerWeek = Math.max(1, Math.min(7, plan.getSessionsPerWeek()));
        int currentWeek = calendar.get(Calendar.WEEK_OF_YEAR);
        int sessionsThisWeek = 0;
        while (lessonIndex < totalLessons) {
            int week = calendar.get(Calendar.WEEK_OF_YEAR);
            if (week != currentWeek) {
                currentWeek = week;
                sessionsThisWeek = 0;
            }

            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            if (sessionsThisWeek < sessionsPerWeek && plan.getDaysOfWeek().contains(dayOfWeek)) {
                CourseScheduleDay scheduleDay = new CourseScheduleDay();
                scheduleDay.setUserId(plan.getUserId());
                scheduleDay.setCourseId(plan.getCourseId());
                scheduleDay.setDate(calendar.getTime());
                scheduleDay.setDailyMinutesGoal(plan.getDailyMinutes());
                List<String> lessonIdsForDay = new ArrayList<>();
                for (int i = 0; i < lessonsPerSession && lessonIndex < totalLessons; i++) {
                    lessonIdsForDay.add(allLessons.get(lessonIndex).getLessonId());
                    lessonIndex++;
                }
                scheduleDay.setLessonIds(lessonIdsForDay);
                scheduleDays.add(scheduleDay);
                sessionsThisWeek++;
            }
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            if (scheduleDays.size() > 1000) break; 
        }
        courseScheduleDayDao.recreateSchedule(plan.getUserId(), plan.getCourseId(), scheduleDays);
    }

    public void markLessonAsCompleted(String userId, String lessonId, String firestoreCourseId) {
        executorService.execute(() -> {
            List<CourseScheduleDay> schedule = courseScheduleDayDao.getScheduleForCourseSync(userId);
            if (schedule == null) return;

            int completedDays = 0;
            int totalDays = schedule.size();

            for (CourseScheduleDay day : schedule) {
                if (day.getLessonIds() != null && day.getLessonIds().contains(lessonId)) {
                    day.setStatus("completed");
                    day.setActualMinutesSpent(day.getActualMinutesSpent() + 10);
                    courseScheduleDayDao.update(day);
                }
                if ("completed".equals(day.getStatus())) {
                    completedDays++;
                }
            }

            // Calculate overall course progress
            if (totalDays > 0) {
                double progress = (double) completedDays / totalDays * 100;
                Course course = courseDao.getCourseByFirestoreId(firestoreCourseId);
                if (course != null) {
                    course.setProgressPercentage(progress);
                    courseDao.update(course);
                }
            }
        });
    }

    public LiveData<StudyPlan> getActivePlan(String userId) {
        return studyPlanDao.getActivePlanByUserId(userId);
    }

    public LiveData<List<CourseScheduleDay>> getSchedule(String userId, String courseId) {
        return courseScheduleDayDao.getScheduleForCourse(userId, courseId);
    }
}
