package com.example.vocabmaster.data.remote;

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
                // Basic manual parsing of JSON (In a real app, use Gson)
                List<Flashcard> cards = parseJson(text);
                callback.onSuccess(cards);
            } catch (Exception e) {
                callback.onError(e);
            }
        }, executor);
    }

    private List<Flashcard> parseJson(String json) {
        List<Flashcard> cards = new ArrayList<>();
        // Note: For simplicity in this demo, we assume the AI returns clean JSON.
        // In a real project, use: new Gson().fromJson(json, new TypeToken<List<Flashcard>>(){}.getType());
        try {
            // Very basic extraction logic if Gson isn't fully configured yet
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