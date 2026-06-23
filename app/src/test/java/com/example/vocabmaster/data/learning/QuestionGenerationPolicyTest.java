package com.example.vocabmaster.data.learning;

import static org.junit.Assert.assertEquals;

import com.example.vocabmaster.data.model.Challenge;
import com.example.vocabmaster.data.srs.SpacedRepetitionCalculator;

import org.junit.Test;

public class QuestionGenerationPolicyTest {
    @Test
    public void metadataIncludesCefrSkillDifficultyAndReviewTag() {
        Challenge challenge = new Challenge();
        challenge.setType("TYPE");

        QuestionGenerationPolicy.applyMetadata(challenge, "B1", "Recall", "deadline", 9);

        assertEquals("TYPE", challenge.getType());
        assertEquals("B1", challenge.getCefrLevel());
        assertEquals("Recall", challenge.getSkill());
        assertEquals("deadline", challenge.getTargetText());
        assertEquals("recall:deadline", challenge.getReviewTag());
        assertEquals(7, challenge.getDifficulty());
    }

    @Test
    public void wrongAnswerSchedulesHardReview() {
        assertEquals(
                SpacedRepetitionCalculator.Rating.HARD,
                QuestionGenerationPolicy.ratingForAnswer(false, 0, "SELECT")
        );
    }

    @Test
    public void strongRecallAnswerSchedulesEasyReview() {
        assertEquals(
                SpacedRepetitionCalculator.Rating.EASY,
                QuestionGenerationPolicy.ratingForAnswer(true, 2, "TYPE")
        );
    }
}
