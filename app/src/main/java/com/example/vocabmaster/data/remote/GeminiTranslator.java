package com.example.vocabmaster.data.remote;

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

public class GeminiTranslator {
    private static final String TAG = "GeminiTranslator";

    private final GenerativeModelFutures model;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public GeminiTranslator() {
        GenerativeModel gm = new GenerativeModel("gemini-2.0-flash-lite", BuildConfig.GEMINI_API_KEY);
        model = GenerativeModelFutures.from(gm);
    }

    public interface TranslationCallback {
        void onSuccess(String vietnamese);
        void onError(String error);
    }

    /**
     * Dịch từ tiếng Anh sang tiếng Việt
     * @param word Từ tiếng Anh
     * @param definition Nghĩa tiếng Anh
     * @param callback Callback nhận kết quả
     */
    public void translateToVietnamese(String word, String definition, TranslationCallback callback) {
        String prompt = "Translate this English word to Vietnamese. " +
                "Word: \"" + word + "\"\n" +
                "Definition: \"" + definition + "\"\n\n" +
                "Return ONLY the Vietnamese translation (1-3 words), nothing else. " +
                "Example: if word is 'apple', return 'quả táo'.";

        Content content = new Content.Builder().addText(prompt).build();
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String text = result.getText();
                if (text != null) {
                    String cleaned = text.trim()
                            .replace("\"", "")
                            .replace("*", "")
                            .replace("\n", " ")
                            .trim();
                    callback.onSuccess(cleaned);
                } else {
                    callback.onError("Empty response");
                }
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "Translation failed", t);
                callback.onError(t.getMessage());
            }
        }, executor);
    }

    /**
     * Dịch nhiều từ cùng lúc (batch)
     */
    public void translateBatch(List<String> words, List<String> definitions, BatchCallback callback) {
        if (words.size() != definitions.size()) {
            callback.onError("Words and definitions size mismatch");
            return;
        }

        StringBuilder prompt = new StringBuilder("Translate these English words to Vietnamese. " +
                "Return ONLY Vietnamese translations, one per line, in the same order.\n\n");
        
        for (int i = 0; i < words.size(); i++) {
            prompt.append((i + 1)).append(". ").append(words.get(i))
                    .append(" - ").append(definitions.get(i)).append("\n");
        }

        Content content = new Content.Builder().addText(prompt.toString()).build();
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String text = result.getText();
                if (text != null) {
                    String[] lines = text.trim().split("\n");
                    List<String> translations = new java.util.ArrayList<>();
                    for (String line : lines) {
                        String cleaned = line.replaceAll("^\\d+\\.\\s*", "")
                                .replace("\"", "").replace("*", "").trim();
                        if (!cleaned.isEmpty()) translations.add(cleaned);
                    }
                    callback.onSuccess(translations);
                } else {
                    callback.onError("Empty response");
                }
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "Batch translation failed", t);
                callback.onError(t.getMessage());
            }
        }, executor);
    }

    public interface BatchCallback {
        void onSuccess(List<String> translations);
        void onError(String error);
    }
}
