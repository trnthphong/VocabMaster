package com.example.vocabmaster.data.remote;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GeneratedFlashcardParser {
    public static final int MAX_CARDS = 50;
    private static final Gson GSON = new Gson();

    private GeneratedFlashcardParser() {}

    public static List<GeneratedFlashcard> parse(String rawResponse, int limit) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw new IllegalArgumentException("AI response is empty");
        }

        String json = extractJson(rawResponse);
        List<GeneratedFlashcard> cards = parseCards(json);
        return sanitize(cards, limit);
    }

    private static String extractJson(String rawResponse) {
        String text = rawResponse.trim();
        int fenceStart = text.indexOf("```");
        if (fenceStart >= 0) {
            int contentStart = text.indexOf('\n', fenceStart);
            if (contentStart >= 0) {
                int fenceEnd = text.indexOf("```", contentStart + 1);
                if (fenceEnd > contentStart) {
                    text = text.substring(contentStart + 1, fenceEnd).trim();
                }
            }
        }

        int objectStart = text.indexOf('{');
        int objectEnd = text.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return text.substring(objectStart, objectEnd + 1);
        }

        int arrayStart = text.indexOf('[');
        int arrayEnd = text.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return text.substring(arrayStart, arrayEnd + 1);
        }

        return text;
    }

    private static List<GeneratedFlashcard> parseCards(String json) {
        try {
            CardsResponse response = GSON.fromJson(json, CardsResponse.class);
            if (response != null && response.cards != null) {
                return response.cards;
            }
        } catch (RuntimeException ignored) {
            // Some models return a raw JSON array even when asked for a wrapper object.
        }

        Type listType = new TypeToken<List<GeneratedFlashcard>>() {}.getType();
        List<GeneratedFlashcard> cards = GSON.fromJson(json, listType);
        if (cards != null) {
            return cards;
        }

        throw new IllegalArgumentException("AI response does not contain cards");
    }

    private static List<GeneratedFlashcard> sanitize(List<GeneratedFlashcard> cards, int limit) {
        int max = Math.min(Math.max(limit, 1), MAX_CARDS);
        List<GeneratedFlashcard> cleaned = new ArrayList<>();
        Set<String> seenWords = new HashSet<>();

        if (cards == null) {
            return cleaned;
        }

        for (GeneratedFlashcard card : cards) {
            if (card == null || cleaned.size() >= max) {
                continue;
            }

            String word = trim(card.getWord());
            String definition = trim(card.getDefinition());
            if (word.isEmpty() || definition.isEmpty()) {
                continue;
            }

            String wordKey = word.toLowerCase(Locale.ROOT);
            if (!seenWords.add(wordKey)) {
                continue;
            }

            GeneratedFlashcard item = new GeneratedFlashcard();
            item.setWord(word);
            item.setDefinition(definition);
            item.setVietnamese_translation(trim(card.getVietnamese_translation()));
            item.setPart_of_speech(trim(card.getPart_of_speech()));
            item.setPhonetic(trim(card.getPhonetic()));
            item.setExample_sentence(trim(card.getExample_sentence()));
            item.setImage_url(trim(card.getImage_url()));
            cleaned.add(item);
        }

        return cleaned;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static class CardsResponse {
        List<GeneratedFlashcard> cards;
    }
}
