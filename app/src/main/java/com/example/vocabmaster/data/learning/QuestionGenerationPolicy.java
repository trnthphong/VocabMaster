package com.example.vocabmaster.data.learning;

import com.example.vocabmaster.data.model.Challenge;
import com.example.vocabmaster.data.srs.SpacedRepetitionCalculator;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class QuestionGenerationPolicy {
    private static final List<String> DEFAULT_SEQUENCE = Arrays.asList(
            "SELECT",
            "COMMUNICATION_SELECT",
            "TYPE",
            "LISTEN",
            "COMMUNICATION_ARRANGE",
            "MATCH",
            "COMMUNICATION_SPEAK",
            "TYPE",
            "SELECT",
            "LISTEN",
            "ARRANGE",
            "TYPE",
            "SELECT",
            "LISTEN"
    );

    private QuestionGenerationPolicy() {
    }

    public static List<String> defaultLessonSequence() {
        return DEFAULT_SEQUENCE;
    }

    public static void applyMetadata(Challenge challenge, String cefrLevel, String skill, String targetText, int orderNum) {
        if (challenge == null) return;
        String type = normalizeType(challenge.getType());
        challenge.setType(type);
        challenge.setCefrLevel(normalizeCefr(cefrLevel));
        challenge.setSkill(skill == null || skill.trim().isEmpty() ? inferSkill(type) : skill.trim());
        challenge.setDifficulty(calculateDifficulty(challenge.getCefrLevel(), type, orderNum));
        challenge.setTargetText(targetText == null ? "" : targetText.trim());
        challenge.setPromptTemplate(type + "_" + challenge.getSkill().toUpperCase(Locale.US).replace(' ', '_'));
        challenge.setReviewTag(buildReviewTag(challenge.getTargetText(), challenge.getSkill()));
    }

    public static SpacedRepetitionCalculator.Rating ratingForAnswer(boolean correct, int streakBeforeAnswer, String challengeType) {
        if (!correct) return SpacedRepetitionCalculator.Rating.HARD;
        String type = normalizeType(challengeType);
        if (streakBeforeAnswer >= 2 && ("TYPE".equals(type) || "SPEAK".equals(type) || "ARRANGE".equals(type))) {
            return SpacedRepetitionCalculator.Rating.EASY;
        }
        return SpacedRepetitionCalculator.Rating.MEDIUM;
    }

    public static String inferSkill(String type) {
        switch (normalizeType(type)) {
            case "INTRO":
                return "Vocabulary";
            case "LISTEN":
                return "Listening";
            case "TYPE":
                return "Recall";
            case "MATCH":
                return "Recognition";
            case "ARRANGE":
                return "Grammar";
            case "SPEAK":
                return "Speaking";
            default:
                return "Meaning";
        }
    }

    public static String normalizeType(String type) {
        String normalized = type == null ? "SELECT" : type.trim().toUpperCase(Locale.US);
        if ("INPUT".equals(normalized) || "FORM".equals(normalized)) return "TYPE";
        if ("REVIEW".equals(normalized)) return "SELECT";
        return normalized;
    }

    private static int calculateDifficulty(String cefrLevel, String type, int orderNum) {
        int base;
        switch (normalizeCefr(cefrLevel)) {
            case "A2":
                base = 3;
                break;
            case "B1":
                base = 5;
                break;
            case "B2":
                base = 7;
                break;
            default:
                base = 2;
                break;
        }
        if ("TYPE".equals(type) || "ARRANGE".equals(type) || "SPEAK".equals(type)) base += 1;
        if (orderNum > 8) base += 1;
        return Math.max(1, Math.min(10, base));
    }

    private static String normalizeCefr(String cefrLevel) {
        if (cefrLevel == null || cefrLevel.trim().isEmpty()) return "A1";
        return cefrLevel.trim().toUpperCase(Locale.US);
    }

    private static String buildReviewTag(String targetText, String skill) {
        String target = targetText == null || targetText.trim().isEmpty()
                ? "general"
                : targetText.trim().toLowerCase(Locale.US);
        String normalizedSkill = skill == null || skill.trim().isEmpty()
                ? "skill"
                : skill.trim().toLowerCase(Locale.US);
        return normalizedSkill + ":" + target;
    }
}
