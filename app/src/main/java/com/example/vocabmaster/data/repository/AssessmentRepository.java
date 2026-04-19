package com.example.vocabmaster.data.repository;

import com.example.vocabmaster.data.api.RetrofitClient;
import com.example.vocabmaster.data.model.LearningProfile;
import com.example.vocabmaster.data.remote.VocabMasterApiService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AssessmentRepository {
    private final VocabMasterApiService apiService;

    public AssessmentRepository() {
        this.apiService = RetrofitClient.getClient().create(VocabMasterApiService.class);
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(Throwable t);
    }

    public void startPlacementTest(String userId, String language, ApiCallback<Map<String, Object>> callback) {
        Map<String, String> data = new HashMap<>();
        data.put("userId", userId);
        data.put("language", language);

        apiService.startPlacementTest(data).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(new Exception("Server error: " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t);
            }
        });
    }

    public void submitPlacementAnswer(String testId, String questionId, String answer, ApiCallback<Map<String, Object>> callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("testId", testId);
        data.put("questionId", questionId);
        data.put("answer", answer);

        apiService.submitPlacementAnswer(data).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(new Exception("Server error: " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t);
            }
        });
    }

    public void completePlacementTest(String testId, ApiCallback<LearningProfile> callback) {
        Map<String, String> data = new HashMap<>();
        data.put("testId", testId);

        apiService.completePlacementTest(data).enqueue(new Callback<LearningProfile>() {
            @Override
            public void onResponse(Call<LearningProfile> call, Response<LearningProfile> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(new Exception("Server error: " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<LearningProfile> call, Throwable t) {
                callback.onError(t);
            }
        });
    }

    public void submitOnboarding(String userId, String language, String goal, int time, List<String> topics, ApiCallback<Map<String, Object>> callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("language", language);
        data.put("goal", goal);
        data.put("dailyMinutes", time);
        data.put("topics", topics);

        apiService.submitOnboardingQuiz(data).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) callback.onSuccess(response.body());
                else callback.onError(new Exception("Failed to submit onboarding: " + response.code()));
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t);
            }
        });
    }

    public void checkGenerationStatus(String jobId, ApiCallback<Map<String, Object>> callback) {
        apiService.getGenerationStatus(jobId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) callback.onSuccess(response.body());
                else callback.onError(new Exception("Failed to check status: " + response.code()));
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t);
            }
        });
    }
}
