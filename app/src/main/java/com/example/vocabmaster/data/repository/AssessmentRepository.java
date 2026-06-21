package com.example.vocabmaster.data.repository;

import com.example.vocabmaster.BuildConfig;
import com.example.vocabmaster.data.api.RetrofitClient;
import com.example.vocabmaster.data.model.LearningProfile;
import com.example.vocabmaster.data.remote.VocabMasterApiService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AssessmentRepository {
    private final VocabMasterApiService apiService;
    private final List<Map<String, Object>> localQuestions = new ArrayList<>();
    private int localScore = 0;
    private String localUserId = "";

    public AssessmentRepository() {
        this.apiService = RetrofitClient.getClient().create(VocabMasterApiService.class);
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(Throwable t);
    }

    public void startPlacementTest(String userId, String language, ApiCallback<Map<String, Object>> callback) {
        if (!BuildConfig.USE_REMOTE_PLACEMENT_TEST) {
            callback.onSuccess(startLocalPlacementTest(userId, language));
            return;
        }

        Map<String, String> data = new HashMap<>();
        data.put("userId", userId);
        data.put("language", language);

        apiService.startPlacementTest(data).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onSuccess(startLocalPlacementTest(userId, language));
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onSuccess(startLocalPlacementTest(userId, language));
            }
        });
    }

    public void submitPlacementAnswer(String testId, String questionId, String answer, ApiCallback<Map<String, Object>> callback) {
        if (isLocalTest(testId)) {
            callback.onSuccess(submitLocalAnswer(questionId, answer));
            return;
        }

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
        if (isLocalTest(testId)) {
            callback.onSuccess(completeLocalPlacementTest(testId));
            return;
        }

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

    private Map<String, Object> startLocalPlacementTest(String userId, String language) {
        localUserId = userId != null ? userId : "";
        localScore = 0;
        localQuestions.clear();
        localQuestions.add(question("q1", "A1 Grammar", "Choose the correct word: I ___ a student.", "am", "is", "are", "be"));
        localQuestions.add(question("q2", "A1 Vocabulary", "Which word means a place where you live?", "home", "book", "water", "teacher"));
        localQuestions.add(question("q3", "A2 Grammar", "She usually ___ coffee in the morning.", "drinks", "drink", "drank", "drinking"));
        localQuestions.add(question("q4", "A2 Vocabulary", "Choose the best synonym for 'quick'.", "fast", "late", "quiet", "heavy"));
        localQuestions.add(question("q5", "B1 Grammar", "If it rains tomorrow, we ___ at home.", "will stay", "stay", "stayed", "would stayed"));
        localQuestions.add(question("q6", "B1 Reading", "He missed the bus because he left home late. Why did he miss it?", "He left late", "He was sick", "The bus was early", "He forgot the route"));
        localQuestions.add(question("q7", "B2 Grammar", "By next month, I ___ this course.", "will have finished", "finish", "finished", "am finishing"));
        localQuestions.add(question("q8", "B2 Vocabulary", "Choose the closest meaning of 'reliable'.", "trustworthy", "expensive", "temporary", "confusing"));

        Map<String, Object> result = new HashMap<>();
        result.put("test_id", "local_" + System.currentTimeMillis());
        result.put("total_questions", localQuestions.size());
        result.put("first_question", publicQuestion(localQuestions.get(0)));
        return result;
    }

    private Map<String, Object> submitLocalAnswer(String questionId, String answer) {
        int index = findQuestionIndex(questionId);
        if (index >= 0) {
            String correctAnswer = (String) localQuestions.get(index).get("answer");
            if (correctAnswer != null && correctAnswer.equals(answer)) {
                localScore++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        int nextIndex = index + 1;
        boolean finished = nextIndex >= localQuestions.size();
        result.put("is_finished", finished);
        if (!finished) {
            result.put("next_question", publicQuestion(localQuestions.get(nextIndex)));
        }
        return result;
    }

    private LearningProfile completeLocalPlacementTest(String testId) {
        String cefrLevel;
        if (localScore <= 2) cefrLevel = "A1";
        else if (localScore <= 4) cefrLevel = "A2";
        else if (localScore <= 6) cefrLevel = "B1";
        else cefrLevel = "B2";

        LearningProfile profile = new LearningProfile(testId + "_profile", cefrLevel);
        profile.setUserId(localUserId);
        profile.setSuggestedCefr(cefrLevel);
        profile.setActive(true);
        Map<String, Double> skillScores = new HashMap<>();
        skillScores.put("Placement", localQuestions.isEmpty() ? 0.0 : (localScore * 100.0 / localQuestions.size()));
        profile.setSkillScores(skillScores);
        return profile;
    }

    private boolean isLocalTest(String testId) {
        return testId != null && testId.startsWith("local_");
    }

    private int findQuestionIndex(String questionId) {
        for (int i = 0; i < localQuestions.size(); i++) {
            if (questionId != null && questionId.equals(localQuestions.get(i).get("id"))) {
                return i;
            }
        }
        return -1;
    }

    private Map<String, Object> question(String id, String skill, String text, String answer, String wrong1, String wrong2, String wrong3) {
        Map<String, Object> question = new HashMap<>();
        question.put("id", id);
        question.put("skill", skill);
        question.put("text", text);
        question.put("answer", answer);
        List<String> options = new ArrayList<>(Arrays.asList(answer, wrong1, wrong2, wrong3));
        Collections.shuffle(options);
        question.put("options", options);
        return question;
    }

    private Map<String, Object> publicQuestion(Map<String, Object> source) {
        Map<String, Object> question = new HashMap<>();
        question.put("id", source.get("id"));
        question.put("skill", source.get("skill"));
        question.put("text", source.get("text"));
        question.put("options", source.get("options"));
        return question;
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
