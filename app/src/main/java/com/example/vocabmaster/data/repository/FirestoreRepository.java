package com.example.vocabmaster.data.repository;

import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.data.model.StudySet;
import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.data.model.UserProgress;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirestoreRepository {
    private final FirebaseFirestore db;
    private final CollectionReference usersRef;
    private final CollectionReference studySetsRef;
    private final CollectionReference progressRef;

    public FirestoreRepository() {
        db = FirebaseFirestore.getInstance();
        usersRef = db.collection("users");
        studySetsRef = db.collection("study_sets");
        progressRef = db.collection("user_progress");
    }

    // --- User Profile Operations ---

    public Task<Void> createNewUser(User user) {
        user.setCreatedAt(Timestamp.now());
        user.setLastActive(Timestamp.now());
        user.setHearts(5);
        user.setXp(0);
        user.setStreak(0);
        user.setRole("user");
        user.setPremium(false);
        return usersRef.document(user.getUid()).set(user);
    }

    public Task<DocumentSnapshot> getUserProfile(String uid) {
        return usersRef.document(uid).get();
    }

    public Task<Void> updateUserXP(String uid, long additionalXp) {
        return usersRef.document(uid).update("xp", com.google.firebase.firestore.FieldValue.increment(additionalXp));
    }

    // --- Study Set Operations ---

    public Task<DocumentReference> createStudySet(StudySet set) {
        set.setCreatedAt(Timestamp.now());
        set.setUpdatedAt(Timestamp.now());
        return studySetsRef.add(set).continueWithTask(task -> {
            String id = task.getResult().getId();
            set.setSetId(id);
            return studySetsRef.document(id).set(set).continueWith(t -> task.getResult());
        });
    }

    public Task<QuerySnapshot> getPublicStudySets() {
        return studySetsRef.whereEqualTo("public", true).orderBy("createdAt", Query.Direction.DESCENDING).get();
    }

    public Task<QuerySnapshot> getMyStudySets(String userId) {
        return studySetsRef.whereEqualTo("creatorId", userId).get();
    }

    // --- Flashcard Operations ---

    public Task<Void> addFlashcardsToSet(String setId, List<Flashcard> cards) {
        WriteBatch batch = db.batch();
        CollectionReference cardsRef = studySetsRef.document(setId).collection("flashcards");
        
        for (Flashcard card : cards) {
            DocumentReference newCard = cardsRef.document();
            card.setFirestoreId(newCard.getId());
            batch.set(newCard, card);
        }
        
        // Update card count in the study set
        batch.update(studySetsRef.document(setId), "cardCount", com.google.firebase.firestore.FieldValue.increment(cards.size()));
        
        return batch.commit();
    }

    public Task<QuerySnapshot> getFlashcards(String setId) {
        return studySetsRef.document(setId).collection("flashcards").orderBy("orderIndex").get();
    }

    // --- SRS & Progress Operations ---

    public Task<Void> saveUserProgress(UserProgress progress) {
        String docId = progress.getUserId() + "_" + progress.getCardId();
        progress.setProgressId(docId);
        progress.setLastReviewed(Timestamp.now());
        return progressRef.document(docId).set(progress);
    }

    public Task<QuerySnapshot> getUserProgressForSet(String userId, String setId) {
        return progressRef.whereEqualTo("userId", userId).whereEqualTo("setId", setId).get();
    }

    // --- Gamification Logic ---

    public Task<Void> updateHearts(String uid, int newHearts) {
        return usersRef.document(uid).update("hearts", newHearts, "lastHeartRegen", Timestamp.now());
    }

    public Task<Void> updateStreak(String uid, int newStreak) {
        return usersRef.document(uid).update("streak", newStreak, "lastActive", Timestamp.now());
    }
}
