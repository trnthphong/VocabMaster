package com.example.vocabmaster.data.remote;

import android.net.Uri;
import android.util.Log;

import com.example.vocabmaster.data.model.Flashcard;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AIService {
    private final GenerativeModelFutures model;

    public AIService(String apiKey) {
        GenerativeModel gm = new GenerativeModel("gemini-1.5-flash", apiKey);
        this.model = GenerativeModelFutures.from(gm);
    }

    public interface AICallback {
        void onSuccess(List<Flashcard> cards);
        void onError(Throwable t);
    }

    public interface ImageGenerationCallback {
        void onSuccess(String imageUrl);
        void onError(Throwable t);
    }

    public void generateFlashcards(String topic, int count, AICallback callback) {
        String prompt = String.format(
                "Generate %d English vocabulary flashcards about '%s'. " +
                "Return ONLY a JSON array of objects with 'term', 'definition', and 'example' fields. " +
                "Do not include markdown formatting or extra text.",
                count, topic
        );

        Content content = new Content.Builder().addText(prompt).build();
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Executor executor = Executors.newSingleThreadExecutor();
        response.addListener(() -> {
            try {
                GenerateContentResponse res = response.get();
                String text = res.getText();
                List<Flashcard> cards = parseJson(text);
                callback.onSuccess(cards);
            } catch (Exception e) {
                callback.onError(e);
            }
        }, executor);
    }

    public void generateImageFromText(String term, String definition, ImageGenerationCallback callback) {
        // Prompt cực kỳ nghiêm ngặt để lấy từ khóa tìm kiếm ảnh
        String promptForGemini = String.format(
                "You are an image search expert. Input: Term='%s', Definition='%s'.\n" +
                "Task: Translate to English if needed, then provide 1-2 SIMPLE English nouns for a photo search.\n" +
                "Constraint: Return ONLY the words, separated by commas. NO sentences, NO extra text.\n" +
                "Example: 'Quả táo' -> 'apple'. 'Học tập' -> 'study,book'.",
                term, definition
        );

        Content content = new Content.Builder().addText(promptForGemini).build();
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Executor executor = Executors.newSingleThreadExecutor();
        response.addListener(() -> {
            try {
                GenerateContentResponse res = response.get();
                String keywords = res.getText().trim().toLowerCase()
                        .replaceAll("[^a-z,]", "")
                        .replace(" ", "");
                
                // Sử dụng Unsplash qua Source (ổn định hơn cho việc lấy đúng đối tượng)
                // Hoặc giữ LoremFlickr nhưng làm sạch keyword
                String imageUrl = "https://loremflickr.com/1080/1920/" + keywords + "/all";
                
                Log.d("AIService", "Keywords: " + keywords + " -> URL: " + imageUrl);
                callback.onSuccess(imageUrl);
            } catch (Exception e) {
                String fallback = term.toLowerCase().replaceAll("[^a-z]", "");
                callback.onSuccess("https://loremflickr.com/1080/1920/" + fallback + "/all");
            }
        }, executor);
    }

    private List<Flashcard> parseJson(String json) {
        List<Flashcard> cards = new ArrayList<>();
        try {
            String cleanJson = json.replace("```json", "").replace("```", "").trim();
            org.json.JSONArray array = new org.json.JSONArray(cleanJson);
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject obj = array.getJSONObject(i);
                Flashcard card = new Flashcard(obj.getString("term"), obj.getString("definition"));
                card.setExample(obj.optString("example", ""));
                cards.add(card);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cards;
    }
}
