package com.example.vocabmaster.data.remote;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class GeneratedFlashcardParserTest {
    @Test
    public void parseReadsWrappedCards() {
        String json = "{\"cards\":[{\"word\":\"travel\",\"definition\":\"to go from one place to another\","
                + "\"vietnamese_translation\":\"du lich\",\"part_of_speech\":\"verb\","
                + "\"phonetic\":\"/ˈtrævəl/\",\"example_sentence\":\"I travel by train.\"}]}";

        List<GeneratedFlashcard> cards = GeneratedFlashcardParser.parse(json, 20);

        assertEquals(1, cards.size());
        assertEquals("travel", cards.get(0).getWord());
        assertEquals("du lich", cards.get(0).getVietnamese_translation());
    }

    @Test
    public void parseReadsMarkdownFencedJson() {
        String response = "```json\n"
                + "{\"cards\":[{\"word\":\"hotel\",\"definition\":\"a place where visitors sleep\"}]}\n"
                + "```";

        List<GeneratedFlashcard> cards = GeneratedFlashcardParser.parse(response, 20);

        assertEquals(1, cards.size());
        assertEquals("hotel", cards.get(0).getWord());
    }

    @Test
    public void parseFiltersMissingRequiredFieldsAndDuplicates() {
        String json = "{\"cards\":["
                + "{\"word\":\" beach \",\"definition\":\" an area of sand by the sea \"},"
                + "{\"word\":\"beach\",\"definition\":\"duplicate\"},"
                + "{\"word\":\"ticket\",\"definition\":\"\"},"
                + "{\"word\":\"\",\"definition\":\"missing word\"}"
                + "]}";

        List<GeneratedFlashcard> cards = GeneratedFlashcardParser.parse(json, 20);

        assertEquals(1, cards.size());
        assertEquals("beach", cards.get(0).getWord());
        assertEquals("an area of sand by the sea", cards.get(0).getDefinition());
    }

    @Test
    public void parseLimitsCardsToFifty() {
        StringBuilder json = new StringBuilder("{\"cards\":[");
        for (int i = 0; i < 60; i++) {
            if (i > 0) json.append(',');
            json.append("{\"word\":\"word").append(i).append("\",\"definition\":\"definition")
                    .append(i).append("\"}");
        }
        json.append("]}");

        List<GeneratedFlashcard> cards = GeneratedFlashcardParser.parse(json.toString(), 99);

        assertEquals(50, cards.size());
    }
}
