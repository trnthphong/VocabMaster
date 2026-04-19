package com.example.vocabmaster.data.remote;

import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.LearningProfile;
import com.example.vocabmaster.data.model.StudyPlan;
import com.example.vocabmaster.data.model.Unit;

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

    // --- 6.1 Assessment APIs (Sử dụng IRT/ELO theo roadmap) ---
    @POST("assessment/quiz")
    Call<Map<String, Object>> submitOnboardingQuiz(@Body Map<String, Object> quizResults);

    @POST("assessment/placement/start")
    Call<Map<String, Object>> startPlacementTest(@Body Map<String, String> params);

    @POST("assessment/placement/answer")
    Call<Map<String, Object>> submitPlacementAnswer(@Body Map<String, Object> answer);

    @POST("assessment/placement/complete")
    Call<LearningProfile> completePlacementTest(@Body Map<String, String> params);

    // --- 6.2 Course Generation APIs (Khuyên dùng Claude Sonnet ở Backend) ---
    @POST("courses/generate")
    Call<Map<String, List<Unit>>> generateCourse(@Body Map<String, Object> profileData);

    @POST("courses/generate")
    Call<Map<String, String>> generateCourseLegacy(@Body Map<String, Object> profileData);

    @GET("courses/generate/{jobId}/status")
    Call<Map<String, Object>> getGenerationStatus(@Path("jobId") String jobId);

    @GET("courses/{courseId}")
    Call<Course> getCourseStructure(@Path("courseId") String courseId);

    // --- 6.3 AI Content & Features ---
    @POST("ai/generate-image-prompt")
    Call<Map<String, String>> generateImagePrompt(@Body Map<String, String> data);

    @POST("ai/analyze-performance")
    Call<Map<String, Object>> analyzePerformance(@Body Map<String, Object> performanceData);

    @POST("ai/tts")
    Call<Map<String, String>> getSpeechUrl(@Body Map<String, String> data);

    // --- 6.4 Study Plan & Schedule ---
    @POST("plans")
    Call<Map<String, Object>> createStudyPlan(@Body StudyPlan plan);

    @GET("plans/{planId}/schedule/today")
    Call<Map<String, Object>> getTodaySchedule(@Path("planId") String planId);

    @PATCH("plans/{planId}")
    Call<StudyPlan> adjustPlan(@Path("planId") String planId, @Body Map<String, Object> updates);
}
