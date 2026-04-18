package com.example.vocabmaster.data.remote;

import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.LearningProfile;
import com.example.vocabmaster.data.model.StudyPlan;
import com.example.vocabmaster.data.model.CourseScheduleDay;
import com.example.vocabmaster.data.model.Exercise;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface VocabMasterApiService {

    // --- 6.1 Assessment APIs ---
    @POST("assessment/quiz")
    Call<Map<String, Object>> submitOnboardingQuiz(@Body Map<String, Object> quizResults);

    @POST("assessment/placement/start")
    Call<Map<String, Object>> startPlacementTest(@Body Map<String, String> params);

    @POST("assessment/placement/answer")
    Call<Map<String, Object>> submitPlacementAnswer(@Body Map<String, Object> answer);

    @POST("assessment/placement/complete")
    Call<LearningProfile> completePlacementTest(@Body Map<String, String> params);

    // --- 6.2 Course Generation APIs ---
    @POST("courses/generate")
    Call<Map<String, String>> generateCourse(@Body Map<String, Object> profileData);

    @GET("courses/generate/{jobId}/status")
    Call<Map<String, Object>> getGenerationStatus(@Path("jobId") String jobId);

    @GET("courses/{courseId}")
    Call<Course> getCourseStructure(@Path("courseId") String courseId);

    @POST("courses/{courseId}/adapt")
    Call<Map<String, String>> adaptCourse(@Path("courseId") String courseId);

    // --- 6.3 Study Plan & Schedule APIs ---
    @POST("plans")
    Call<Map<String, Object>> createStudyPlan(@Body StudyPlan plan);

    @GET("plans/{planId}/schedule")
    Call<Map<String, Object>> getFullSchedule(@Path("planId") String planId);

    @GET("plans/{planId}/schedule/today")
    Call<Map<String, Object>> getTodaySchedule(@Path("planId") String planId);

    @POST("plans/{planId}/progress/today")
    Call<Map<String, Object>> updateDailyProgress(@Path("planId") String planId, @Body Map<String, Object> progress);

    @GET("plans/{planId}/progress/summary")
    Call<Map<String, Object>> getProgressSummary(@Path("planId") String planId);

    @PATCH("plans/{planId}")
    Call<StudyPlan> adjustPlan(@Path("planId") String planId, @Body Map<String, Object> updates);
}
