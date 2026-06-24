package com.example.vocabmaster.data.remote;

import android.text.TextUtils;
import android.util.Log;

import com.example.vocabmaster.BuildConfig;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GeminiFlashcardGenerator {
    public static final int DEFAULT_CARD_COUNT = 20;
    private static final String TAG = "GeminiFlashcardGenerator";
    private static final String MODEL_NAME = "gemini-2.0-flash-lite";

    private final GenerativeModelFutures model;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public GeminiFlashcardGenerator() {
        GenerativeModel generativeModel = new GenerativeModel(MODEL_NAME, BuildConfig.GEMINI_API_KEY);
        model = GenerativeModelFutures.from(generativeModel);
    }

    public interface Callback {
        void onSuccess(List<GeneratedFlashcard> cards);
        void onError(Throwable t);
    }

    public void generateCards(String topic, int count, Callback callback) {
        if (TextUtils.isEmpty(BuildConfig.GEMINI_API_KEY)) {
            callback.onError(new IllegalStateException("GEMINI_API_KEY is empty"));
            return;
        }

        String normalizedTopic = topic == null ? "" : topic.trim();
        if (normalizedTopic.isEmpty()) {
            callback.onError(new IllegalArgumentException("Topic is empty"));
            return;
        }

        int requestedCount = Math.min(Math.max(count, 1), GeneratedFlashcardParser.MAX_CARDS);
        Content content = new Content.Builder()
                .addText(buildPrompt(normalizedTopic, requestedCount))
                .build();
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                try {
                    String text = result != null ? result.getText() : null;
                    List<GeneratedFlashcard> cards =
                            GeneratedFlashcardParser.parse(text, requestedCount);
                    if (cards.isEmpty()) {
                        callback.onError(new IllegalStateException("AI did not return valid cards"));
                    } else {
                        callback.onSuccess(cards);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse AI flashcards", e);
                    callback.onError(e);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "Flashcard generation failed", t);
                callback.onError(t);
            }
        }, executor);
    }

    private String buildPrompt(String topic, int count) {
        return "Create " + count + " English vocabulary flashcards for the topic: \"" + topic + "\".\n"
                + "Return ONLY valid JSON. Do not include markdown, comments, or extra text.\n"
                + "The response must match this exact shape:\n"
                + "{\n"
                + "  \"cards\": [\n"
                + "    {\n"
                + "      \"word\": \"...\",\n"
                + "      \"definition\": \"...\",\n"
                + "      \"vietnamese_translation\": \"...\",\n"
                + "      \"part_of_speech\": \"...\",\n"
                + "      \"phonetic\": \"...\",\n"
                + "      \"example_sentence\": \"...\"\n"
                + "    }\n"
                + "  ]\n"
                + "}\n"
                + "Rules:\n"
                + "- Use common, useful words for learners.\n"
                + "- Keep definitions short and clear in English.\n"
                + "- Vietnamese translations should be natural and concise.\n"
                + "- Example sentences must be simple and include the word.";
    }
}
