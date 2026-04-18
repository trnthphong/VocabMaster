package com.example.vocabmaster.data.remote;

import android.util.Log;

import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.data.model.Lesson;
import com.example.vocabmaster.data.model.Unit;
import com.example.vocabmaster.data.model.Exercise;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.BlockThreshold;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.ai.client.generativeai.type.GenerationConfig;
import com.google.ai.client.generativeai.type.SafetySetting;
import com.google.ai.client.generativeai.type.HarmCategory;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AIService {
    private static final String TAG = "AIService";
    private final GenerativeModelFutures model;

    public AIService(String apiKey) {
        // Google AI SDK 0.9.0 uses @JvmField for Builder properties, 
        // so we set them directly in Java instead of using setter methods.
        GenerationConfig.Builder configBuilder = new GenerationConfig.Builder();
        configBuilder.temperature = 0.7f;
        GenerationConfig config = configBuilder.build();

        GenerativeModel gm = new GenerativeModel(
            "gemini-1.5-flash", 
            apiKey, 
            config
        );
        this.model = GenerativeModelFutures.from(gm);
    }

    public interface AICallback<T> {
        void onSuccess(T result);
        void onError(Throwable t);
    }

    public interface CurriculumCallback {
        void onSuccess(List<Unit> units);
        void onError(Throwable t);
    }

    public void generateImageFromText(String term, String definition, AICallback<String> callback) {
        String prompt = String.format(
                "Generate a short, descriptive prompt (max 15 words) for an image representing the word '%s' (definition: %s). " +
                "The prompt should be in English and focus on a single, clear subject. Return ONLY the prompt text.",
                term, definition
        );

        Content content = new Content.Builder().addText(prompt).build();
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Executor executor = Executors.newSingleThreadExecutor();
        response.addListener(() -> {
            try {
                GenerateContentResponse res = response.get();
                String aiPrompt = res.getText().trim();
                // Clean up the response just in case AI adds quotes or extra text
                aiPrompt = aiPrompt.replaceAll("^\"|\"$", "");
                
                String encodedPrompt = URLEncoder.encode(aiPrompt, StandardCharsets.UTF_8.toString());
                String imageUrl = "https://image.pollinations.ai/prompt/" + encodedPrompt + "?width=1024&height=1024&nologo=true";
                
                callback.onSuccess(imageUrl);
            } catch (Exception e) {
                Log.e(TAG, "Error generating AI image prompt: " + e.getMessage());
                callback.onError(e);
            }
        }, executor);
    }

    public void generateCurriculum(String language, String level, List<String> topics, CurriculumCallback callback) {
        String prompt = String.format(
                "Create a language learning curriculum for %s (level %s) about %s. " +
                "Return a JSON object: { \"units\": [ { \"title\": \"...\", \"orderNum\": 1, \"lessons\": [ { \"title\": \"...\", \"type\": \"vocabulary\", \"durationMinutes\": 10 } ] } ] }.",
                language, level, String.join(", ", topics)
        );

        Content content = new Content.Builder().addText(prompt).build();
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Executor executor = Executors.newSingleThreadExecutor();
        response.addListener(() -> {
            try {
                GenerateContentResponse res = response.get();
                String text = res.getText();
                Log.d(TAG, "AI Response: " + text);
                List<Unit> units = parseCurriculumJson(text);
                if (units.isEmpty()) {
                    callback.onError(new Exception("Empty result from AI"));
                } else {
                    callback.onSuccess(units);
                }
            } catch (Exception e) {
                Log.e(TAG, "AI SDK Error: " + e.getMessage());
                if (e.getCause() != null) {
                    Log.e(TAG, "Cause: " + e.getCause().getMessage());
                }
                callback.onError(e);
            }
        }, executor);
    }

    private String cleanJsonResponse(String json) {
        if (json == null) return "";
        String cleaned = json.trim();
        if (cleaned.startsWith("```")) {
            int firstLineEnd = cleaned.indexOf("\n");
            int lastBackticks = cleaned.lastIndexOf("```");
            if (firstLineEnd != -1 && lastBackticks > firstLineEnd) {
                cleaned = cleaned.substring(firstLineEnd, lastBackticks).trim();
            } else {
                cleaned = cleaned.replace("```json", "").replace("```", "").trim();
            }
        }
        int firstBrace = cleaned.indexOf("{");
        int lastBrace = cleaned.lastIndexOf("}");
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1);
        }
        return cleaned;
    }

    private List<Unit> parseCurriculumJson(String json) {
        List<Unit> units = new ArrayList<>();
        try {
            String cleanJson = cleanJsonResponse(json);
            JSONObject root = new JSONObject(cleanJson);
            JSONArray unitsArray = root.getJSONArray("units");
            
            for (int i = 0; i < unitsArray.length(); i++) {
                JSONObject unitObj = unitsArray.getJSONObject(i);
                Unit unit = new Unit(unitObj.getString("title"), unitObj.getInt("orderNum"), i == 0);
                
                JSONArray lessonsArray = unitObj.getJSONArray("lessons");
                List<Lesson> lessons = new ArrayList<>();
                for (int j = 0; j < lessonsArray.length(); j++) {
                    JSONObject lessonObj = lessonsArray.getJSONObject(j);
                    Lesson lesson = new Lesson(lessonObj.getString("title"), lessonObj.getString("type"), 
                                               lessonObj.getInt("durationMinutes"), 10);
                    lesson.setOrderNum(j + 1);
                    lessons.add(lesson);
                }
                unit.setLessons(lessons);
                units.add(unit);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing curriculum JSON: " + e.getMessage(), e);
        }
        return units;
    }
}
