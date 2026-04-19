package com.example.vocabmaster.data.remote;

import android.util.Log;

import com.example.vocabmaster.data.api.RetrofitClient;
import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.Lesson;
import com.example.vocabmaster.data.model.Unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * AIService hiện tại đóng vai trò là Proxy để gọi các dịch vụ AI thông qua Backend.
 * Backend sẽ điều phối giữa Claude (Curriculum), GPT-4o (Content), và Azure (Speech).
 */
public class AIService {
    private static final String TAG = "AIService";
    private final VocabMasterApiService apiService;

    public AIService() {
        this.apiService = RetrofitClient.getClient().create(VocabMasterApiService.class);
    }

    public AIService(String apiKey) {
        this();
    }

    public interface AICallback<T> {
        void onSuccess(T result);
        void onError(Throwable t);
    }

    public interface CurriculumCallback {
        void onSuccess(List<Unit> units);
        void onError(Throwable t);
    }

    /**
     * Sinh ảnh minh họa cho từ vựng.
     */
    public void generateImageFromText(String term, String definition, AICallback<String> callback) {
        Map<String, String> data = new HashMap<>();
        data.put("term", term);
        data.put("definition", definition);

        apiService.generateImagePrompt(data).enqueue(new Callback<Map<String, String>>() {
            @Override
            public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body().get("imageUrl"));
                } else {
                    callback.onError(new Exception("Failed to generate image prompt: " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<Map<String, String>> call, Throwable t) {
                callback.onError(t);
            }
        });
    }

    /**
     * Sinh giáo trình học tập (Curriculum).
     * Đã gỡ bỏ Mock Data, sử dụng API thật từ server.
     */
    public void generateCurriculum(String language, String level, List<String> topics, CurriculumCallback callback) {
        Map<String, Object> profileData = new HashMap<>();
        profileData.put("language", language);
        profileData.put("level", level);
        profileData.put("topics", topics);

        apiService.generateCourse(profileData).enqueue(new Callback<Map<String, List<Unit>>>() {
            @Override
            public void onResponse(Call<Map<String, List<Unit>>> call, Response<Map<String, List<Unit>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body().get("units"));
                } else {
                    callback.onError(new Exception("Server error: " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<Map<String, List<Unit>>> call, Throwable t) {
                callback.onError(t);
            }
        });
    }

    /**
     * Phân tích hiệu suất người dùng.
     */
    public void analyzeUserPerformance(Map<String, Object> performanceData, AICallback<Map<String, Object>> callback) {
        apiService.analyzePerformance(performanceData).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(new Exception("Performance analysis failed: " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t);
            }
        });
    }
}
