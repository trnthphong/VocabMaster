package com.example.vocabmaster.data.repository;

import androidx.annotation.NonNull;

import com.example.vocabmaster.data.model.Flashcard;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class StudyScheduler {

    public interface FlashcardsCallback {
        void onLoaded(List<Flashcard> cards);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void loadDueFlashcards(@NonNull String uid, FlashcardsCallback callback) {
        long now = System.currentTimeMillis();
        db.collection("users").document(uid).collection("flashcards")
                .whereLessThanOrEqualTo("nextReviewAt", now)
                .limit(20)
                .get()
                .addOnSuccessListener(qs -> {
                    List<Flashcard> cards = new ArrayList<>();
                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        Flashcard card = doc.toObject(Flashcard.class);
                        if (card != null) {
                            card.setFirestoreId(doc.getId());
                            cards.add(card);
                        }
                    }
                    callback.onLoaded(cards);
                });
    }

    public void seedIfEmpty(@NonNull String uid, Runnable done) {
        db.collection("users").document(uid).collection("flashcards").limit(1).get().addOnSuccessListener(qs -> {
            if (!qs.isEmpty()) {
                done.run();
                return;
            }
            List<Flashcard> defaults = new ArrayList<>();
            defaults.add(new Flashcard("abundant", "existing in large quantities", 1));
            defaults.add(new Flashcard("brief", "lasting a short time", 1));
            defaults.add(new Flashcard("accurate", "correct in all details", 1));
            for (Flashcard f : defaults) {
                db.collection("users").document(uid).collection("flashcards").add(f);
            }
            done.run();
        });
    }

    public void persistReview(@NonNull String uid, @NonNull Flashcard card, boolean success) {
        int nextInterval = success ? Math.max(1, card.getInterval() * 2) : 1;
        long now = System.currentTimeMillis();
        long nextReview = now + (nextInterval * 24L * 60L * 60L * 1000L);
        card.setInterval(nextInterval);
        card.setLastReviewTime(now);
        card.setNextReviewAt(nextReview);
        if (card.getFirestoreId() == null) return;
        db.collection("users").document(uid).collection("flashcards").document(card.getFirestoreId())
                .set(card);
    }
}
