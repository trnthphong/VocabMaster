package com.example.vocabmaster.data.remote;

import android.util.Log;

import com.example.vocabmaster.BuildConfig;
import com.example.vocabmaster.data.api.RetrofitClient;
import com.example.vocabmaster.data.model.Lesson;
import com.example.vocabmaster.data.model.Unit;

import java.util.ArrayList;
import java.util.Arrays;
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
    public void generateCurriculum(String language, String level, String goal, List<String> topics, CurriculumCallback callback) {
        if (!BuildConfig.USE_REMOTE_COURSE_GENERATION) {
            callback.onSuccess(generateLocalCurriculum(language, level, goal, topics));
            return;
        }

        Map<String, Object> profileData = new HashMap<>();
        profileData.put("language", language);
        profileData.put("level", level);
        profileData.put("goal", goal);
        profileData.put("topics", topics);

        apiService.generateCourse(profileData).enqueue(new Callback<Map<String, List<Unit>>>() {
            @Override
            public void onResponse(Call<Map<String, List<Unit>>> call, Response<Map<String, List<Unit>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().get("units") != null) {
                    callback.onSuccess(response.body().get("units"));
                } else {
                    Log.w(TAG, "Backend course generation failed, using local generator. Code: " + response.code());
                    callback.onSuccess(generateLocalCurriculum(language, level, goal, topics));
                }
            }

            @Override
            public void onFailure(Call<Map<String, List<Unit>>> call, Throwable t) {
                Log.w(TAG, "Backend unavailable, using local course generator.", t);
                callback.onSuccess(generateLocalCurriculum(language, level, goal, topics));
            }
        });
    }

    public void generateCurriculum(String language, String level, List<String> topics, CurriculumCallback callback) {
        generateCurriculum(language, level, "", topics, callback);
    }

    private List<Unit> generateLocalCurriculum(String language, String level, String goal, List<String> topics) {
        List<String> safeTopics = new ArrayList<>();
        if (topics != null) {
            for (String topic : topics) {
                if (topic != null && !topic.trim().isEmpty()) safeTopics.add(topic.trim());
            }
        }
        if (safeTopics.isEmpty()) safeTopics.add("General");

        int unitCount = Math.max(10, Math.min(12, safeTopics.size() * 3));
        List<Unit> units = new ArrayList<>();

        for (int unitIndex = 1; unitIndex <= unitCount; unitIndex++) {
            String topic = safeTopics.get((unitIndex - 1) % safeTopics.size());
            String focus = getThemes(topic).get((unitIndex - 1) % getThemes(topic).size());

            Unit unit = new Unit();
            unit.setTitle("Unit " + unitIndex + ": " + focus + " for " + getGoalLabel(goal));
            unit.setOrderNum(unitIndex);
            unit.setUnlocked(unitIndex == 1);

            List<Lesson> lessons = new ArrayList<>();
            for (int lessonIndex = 1; lessonIndex <= 5; lessonIndex++) {
                lessons.add(buildLocalLesson(topic, unitIndex, lessonIndex, language, level, goal));
            }
            unit.setLessons(lessons);
            units.add(unit);
        }

        return units;
    }

    private Lesson buildLocalLesson(String topic, int unitIndex, int lessonIndex, String language, String level, String goal) {
        List<String> themes = getThemes(topic);
        String goalTheme = getGoalThemes(goal).get((unitIndex + lessonIndex - 2) % getGoalThemes(goal).size());
        String theme = themes.get((unitIndex + lessonIndex - 2) % themes.size());
        String[] types = {"intro", "vocabulary", "listening", "speaking", "quiz"};
        String type = types[(lessonIndex - 1) % types.length];

        Lesson lesson = new Lesson();
        lesson.setTitle(buildLessonTitle(lessonIndex, topic, theme, goalTheme));
        lesson.setType(type);
        lesson.setDurationMinutes("quiz".equals(type) ? 8 : 12);
        lesson.setXpPoints("quiz".equals(type) ? 25 : 15);
        lesson.setOrderNum(lessonIndex);
        lesson.setCompleted(false);
        lesson.setVocabWords(pickWords(topic, goal, level, (unitIndex - 1) * 3 + lessonIndex - 1, 5));
        return lesson;
    }

    private String buildLessonTitle(int lessonIndex, String topic, String theme, String goalTheme) {
        switch (lessonIndex) {
            case 1:
                return "Warm up: " + theme;
            case 2:
                return "Core words: " + topic;
            case 3:
                return "Listen in context: " + goalTheme;
            case 4:
                return "Speak it out: " + theme;
            default:
                return "Review mission: " + goalTheme;
        }
    }

    private String getGoalLabel(String goal) {
        if ("Work".equals(goal)) return "work";
        if ("Travel".equals(goal)) return "travel";
        if ("Exam".equals(goal)) return "exam prep";
        if ("Hobby".equals(goal)) return "daily life";
        return "real life";
    }

    private List<String> getGoalThemes(String goal) {
        if ("Work".equals(goal)) {
            return Arrays.asList("professional use", "clear communication", "work scenarios");
        } else if ("Travel".equals(goal)) {
            return Arrays.asList("real travel situations", "survival phrases", "local interactions");
        } else if ("Exam".equals(goal)) {
            return Arrays.asList("test readiness", "accuracy practice", "exam vocabulary");
        } else if ("Hobby".equals(goal)) {
            return Arrays.asList("casual conversation", "personal interests", "daily enjoyment");
        }
        return Arrays.asList("daily use", "guided practice", "confidence building");
    }

    private List<String> getThemes(String topic) {
        switch (topic) {
            case "Career":
                return Arrays.asList("workplace communication", "meetings", "emails", "interviews");
            case "School":
                return Arrays.asList("classroom basics", "assignments", "campus life", "exams");
            case "Culture":
                return Arrays.asList("traditions", "festivals", "customs", "daily etiquette");
            case "Travel":
                return Arrays.asList("airport phrases", "hotel check-in", "directions", "local transport");
            case "Food":
                return Arrays.asList("ordering food", "ingredients", "restaurants", "cooking");
            case "Technology":
                return Arrays.asList("devices", "apps", "online safety", "technical support");
            default:
                return Arrays.asList("daily phrases", "people", "places", "common actions");
        }
    }

    private List<String> pickWords(String topic, String goal, String level, int start, int count) {
        List<String> words = new ArrayList<>(getWords(topic));
        words.addAll(getGoalWords(goal));
        words.addAll(getLevelWords(level));
        List<String> picked = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            picked.add(words.get((start + i) % words.size()));
        }
        return picked;
    }

    private List<String> getGoalWords(String goal) {
        if ("Work".equals(goal)) {
            return Arrays.asList("agenda", "client", "report", "presentation");
        } else if ("Travel".equals(goal)) {
            return Arrays.asList("arrival", "departure", "booking", "itinerary");
        } else if ("Exam".equals(goal)) {
            return Arrays.asList("question", "answer", "score", "strategy");
        } else if ("Hobby".equals(goal)) {
            return Arrays.asList("favorite", "enjoy", "weekend", "activity");
        }
        return Arrays.asList("daily", "useful", "simple", "confident");
    }

    private List<String> getLevelWords(String level) {
        if ("A1".equals(level)) {
            return Arrays.asList("hello", "name", "need", "like");
        } else if ("A2".equals(level)) {
            return Arrays.asList("usually", "because", "around", "plan");
        } else if ("B1".equals(level)) {
            return Arrays.asList("suggest", "explain", "compare", "prepare");
        } else if ("B2".equals(level)) {
            return Arrays.asList("negotiate", "summarize", "reliable", "priority");
        }
        return Arrays.asList("practice", "meaning", "example", "context");
    }

    private List<String> getWords(String topic) {
        switch (topic) {
            case "Career":
                return Arrays.asList("resume", "deadline", "meeting", "colleague", "project", "salary", "interview", "task");
            case "School":
                return Arrays.asList("lesson", "homework", "teacher", "student", "library", "exam", "grade", "subject");
            case "Culture":
                return Arrays.asList("festival", "tradition", "custom", "museum", "music", "history", "art", "celebration");
            case "Travel":
                return Arrays.asList("ticket", "passport", "hotel", "station", "map", "luggage", "reservation", "direction");
            case "Food":
                return Arrays.asList("menu", "breakfast", "dinner", "rice", "vegetable", "drink", "spicy", "delicious");
            case "Technology":
                return Arrays.asList("computer", "phone", "password", "website", "download", "software", "message", "battery");
            default:
                return Arrays.asList("hello", "friend", "home", "city", "learn", "speak", "listen", "practice");
        }
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
