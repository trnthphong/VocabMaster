package com.example.vocabmaster.data.repository;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.vocabmaster.data.gamification.GamificationCalculator;
import com.example.vocabmaster.data.gamification.GamificationConstants;
import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.util.HeartRegenWorker;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class GamificationRepository {
    private final Context appContext;
    private final FirebaseFirestore db;

    public GamificationRepository(@Nullable Context context) {
        this.appContext = context != null ? context.getApplicationContext() : null;
        this.db = FirebaseFirestore.getInstance();
    }

    public ListenerRegistration observeUser(String uid, EventListener<DocumentSnapshot> listener) {
        return userRef(uid).addSnapshotListener(listener);
    }

    public Task<HeartStateResult> recoverHeartsIfDue(String uid) {
        long now = System.currentTimeMillis();
        return db.runTransaction(transaction -> {
            DocumentReference ref = userRef(uid);
            DocumentSnapshot snapshot = transaction.get(ref);
            User user = snapshot.toObject(User.class);
            boolean premium = user != null && user.isActivePremium();
            int hearts = getInt(snapshot, "hearts", GamificationConstants.MAX_HEARTS);
            Timestamp lastHeartRegen = snapshot.getTimestamp("lastHeartRegen");
            Long lastHeartRegenMillis = lastHeartRegen != null ? lastHeartRegen.toDate().getTime() : null;

            GamificationCalculator.HeartRecoveryResult recovery =
                    GamificationCalculator.calculateHeartRecovery(hearts, lastHeartRegenMillis, now);

            if (!premium && recovery.isChanged()) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("hearts", recovery.getHearts());
                updates.put("lastHeartRegen", new Timestamp(new Date(recovery.getLastHeartRegenMillis())));
                transaction.update(ref, updates);
            }

            return new HeartStateResult(
                    premium,
                    premium ? GamificationConstants.MAX_HEARTS : recovery.getHearts(),
                    recovery.getLastHeartRegenMillis(),
                    recovery.getNextHeartAtMillis()
            );
        }).addOnSuccessListener(result -> scheduleHeartRegen(uid, result));
    }

    public Task<HeartSpendResult> spendHeart(String uid) {
        long now = System.currentTimeMillis();
        return db.runTransaction(transaction -> {
            DocumentReference ref = userRef(uid);
            DocumentSnapshot snapshot = transaction.get(ref);
            User user = snapshot.toObject(User.class);
            boolean premium = user != null && user.isActivePremium();
            if (premium) {
                return new HeartSpendResult(true, true, GamificationConstants.MAX_HEARTS, -1L, -1L);
            }

            int hearts = getInt(snapshot, "hearts", GamificationConstants.MAX_HEARTS);
            Timestamp lastHeartRegen = snapshot.getTimestamp("lastHeartRegen");
            Long lastHeartRegenMillis = lastHeartRegen != null ? lastHeartRegen.toDate().getTime() : null;

            GamificationCalculator.HeartRecoveryResult recovery =
                    GamificationCalculator.calculateHeartRecovery(hearts, lastHeartRegenMillis, now);

            if (recovery.getHearts() <= 0) {
                if (recovery.isChanged()) {
                    transaction.update(ref,
                            "hearts", recovery.getHearts(),
                            "lastHeartRegen", new Timestamp(new Date(recovery.getLastHeartRegenMillis())));
                }
                return new HeartSpendResult(
                        false,
                        false,
                        recovery.getHearts(),
                        recovery.getLastHeartRegenMillis(),
                        recovery.getNextHeartAtMillis()
                );
            }

            int nextHearts = Math.max(0, recovery.getHearts() - GamificationConstants.HEART_COST_WRONG);
            long nextLastRegen = recovery.getHearts() >= GamificationConstants.MAX_HEARTS
                    ? now
                    : recovery.getLastHeartRegenMillis();
            long nextHeartAt = GamificationCalculator.nextHeartAtMillis(nextHearts, nextLastRegen, now);

            transaction.update(ref,
                    "hearts", nextHearts,
                    "lastHeartRegen", new Timestamp(new Date(nextLastRegen)));

            return new HeartSpendResult(true, false, nextHearts, nextLastRegen, nextHeartAt);
        }).addOnSuccessListener(result -> scheduleHeartRegen(uid, result));
    }

    public Task<StudyAwardResult> awardStudyCompletion(String uid, int xpEarned) {
        int safeXp = Math.max(0, xpEarned);
        long now = System.currentTimeMillis();
        String timezone = TimeZone.getDefault().getID();

        return db.runTransaction(transaction -> {
            DocumentReference ref = userRef(uid);
            DocumentSnapshot snapshot = transaction.get(ref);

            long currentXp = getLong(snapshot, "xp", 0L);
            int currentStreak = getInt(snapshot, "streak", 0);
            int longestStreak = getInt(snapshot, "longestStreak", 0);
            Timestamp lastActive = snapshot.getTimestamp("lastActive");
            String savedTimezone = snapshot.getString("timezone");
            String resolvedTimezone = savedTimezone != null && !savedTimezone.trim().isEmpty()
                    ? savedTimezone
                    : timezone;
            Long lastActiveMillis = lastActive != null ? lastActive.toDate().getTime() : null;

            GamificationCalculator.StreakResult streakResult =
                    GamificationCalculator.calculateStreak(
                            currentStreak,
                            longestStreak,
                            lastActiveMillis,
                            now,
                            resolvedTimezone
                    );

            long nextXp = currentXp + safeXp;
            Map<String, Object> updates = new HashMap<>();
            updates.put("xp", nextXp);
            updates.put("streak", streakResult.getStreak());
            updates.put("longestStreak", streakResult.getLongestStreak());
            updates.put("lastActive", new Timestamp(new Date(now)));
            updates.put("timezone", resolvedTimezone);
            transaction.update(ref, updates);

            return new StudyAwardResult(
                    safeXp,
                    nextXp,
                    streakResult.getStreak(),
                    streakResult.getLongestStreak()
            );
        });
    }

    private void scheduleHeartRegen(String uid, HeartState state) {
        if (appContext == null) return;
        HeartRegenWorker.schedule(appContext, uid, state.getHearts(), state.getLastHeartRegenMillis(), state.isPremium());
    }

    private DocumentReference userRef(String uid) {
        return db.collection("users").document(uid);
    }

    private int getInt(DocumentSnapshot snapshot, String field, int fallback) {
        Long value = snapshot.getLong(field);
        return value != null ? value.intValue() : fallback;
    }

    private long getLong(DocumentSnapshot snapshot, String field, long fallback) {
        Long value = snapshot.getLong(field);
        return value != null ? value : fallback;
    }

    public interface HeartState {
        boolean isPremium();
        int getHearts();
        long getLastHeartRegenMillis();
        long getNextHeartAtMillis();
    }

    public static class HeartStateResult implements HeartState {
        private final boolean premium;
        private final int hearts;
        private final long lastHeartRegenMillis;
        private final long nextHeartAtMillis;

        public HeartStateResult(boolean premium, int hearts, long lastHeartRegenMillis, long nextHeartAtMillis) {
            this.premium = premium;
            this.hearts = hearts;
            this.lastHeartRegenMillis = lastHeartRegenMillis;
            this.nextHeartAtMillis = nextHeartAtMillis;
        }

        @Override
        public boolean isPremium() { return premium; }
        @Override
        public int getHearts() { return hearts; }
        @Override
        public long getLastHeartRegenMillis() { return lastHeartRegenMillis; }
        @Override
        public long getNextHeartAtMillis() { return nextHeartAtMillis; }
    }

    public static class HeartSpendResult extends HeartStateResult {
        private final boolean allowed;

        public HeartSpendResult(
                boolean allowed,
                boolean premium,
                int hearts,
                long lastHeartRegenMillis,
                long nextHeartAtMillis
        ) {
            super(premium, hearts, lastHeartRegenMillis, nextHeartAtMillis);
            this.allowed = allowed;
        }

        public boolean isAllowed() { return allowed; }
    }

    public static class StudyAwardResult {
        private final int xpEarned;
        private final long totalXp;
        private final int streak;
        private final int longestStreak;

        public StudyAwardResult(int xpEarned, long totalXp, int streak, int longestStreak) {
            this.xpEarned = xpEarned;
            this.totalXp = totalXp;
            this.streak = streak;
            this.longestStreak = longestStreak;
        }

        public int getXpEarned() { return xpEarned; }
        public long getTotalXp() { return totalXp; }
        public int getStreak() { return streak; }
        public int getLongestStreak() { return longestStreak; }
    }
}
