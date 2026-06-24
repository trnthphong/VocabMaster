package com.example.vocabmaster.data.repository;

import android.content.Context;
import android.os.Looper;

import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.local.PendingUserProgressDao;
import com.example.vocabmaster.data.model.PendingUserProgress;
import com.example.vocabmaster.data.model.UserProgress;
import com.example.vocabmaster.data.sync.OfflineSyncWorker;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OfflineProgressRepository {
    private static final int SYNC_BATCH_SIZE = 50;
    private final Context appContext;
    private final PendingUserProgressDao pendingDao;
    private final FirebaseFirestore firestore;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public OfflineProgressRepository(Context context) {
        appContext = context.getApplicationContext();
        pendingDao = AppDatabase.getDatabase(appContext).pendingUserProgressDao();
        firestore = FirebaseFirestore.getInstance();
    }

    public void enqueue(UserProgress progress) {
        if (progress == null || progress.getUserId() == null || progress.getCardId() == null) return;
        runDatabaseWrite(() -> pendingDao.upsert(fromUserProgress(progress)));
    }

    public void enqueueTopicProgress(String userId, String setId, String cardId, String status, long reviewedAtMillis) {
        if (userId == null || setId == null || cardId == null) return;
        runDatabaseWrite(() -> {
            PendingUserProgress pending = new PendingUserProgress();
            pending.setProgressId(userId + "_" + cardId);
            pending.setUserId(userId);
            pending.setCardId(cardId);
            pending.setSetId(setId);
            pending.setStatus(status);
            pending.setInterval(0);
            pending.setEaseFactor(0f);
            pending.setNextReviewMillis(reviewedAtMillis);
            pending.setLastReviewedMillis(reviewedAtMillis);
            pending.setCreatedAtMillis(System.currentTimeMillis());
            pendingDao.upsert(pending);
        });
    }

    private void runDatabaseWrite(Runnable write) {
        Runnable wrapped = () -> {
            write.run();
            OfflineSyncWorker.enqueue(appContext);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            executorService.execute(wrapped);
        } else {
            wrapped.run();
        }
    }

    public boolean syncPendingOnce() {
        List<PendingUserProgress> pendingItems = pendingDao.getOldest(SYNC_BATCH_SIZE);
        boolean allSucceeded = true;
        for (PendingUserProgress pending : pendingItems) {
            try {
                Tasks.await(firestore.collection("user_progress")
                        .document(pending.getProgressId())
                        .set(toFirestoreMap(pending)));
                pendingDao.deleteById(pending.getProgressId());
            } catch (Exception e) {
                pendingDao.incrementAttempt(pending.getProgressId());
                allSucceeded = false;
            }
        }
        return allSucceeded;
    }

    public int pendingCount() {
        return pendingDao.count();
    }

    private PendingUserProgress fromUserProgress(UserProgress progress) {
        long now = System.currentTimeMillis();
        String progressId = progress.getProgressId();
        if (progressId == null || progressId.trim().isEmpty()) {
            progressId = progress.getUserId() + "_" + progress.getCardId();
        }
        PendingUserProgress pending = new PendingUserProgress();
        pending.setProgressId(progressId);
        pending.setUserId(progress.getUserId());
        pending.setCardId(progress.getCardId());
        pending.setSetId(progress.getSetId());
        pending.setStatus(progress.getStatus());
        pending.setInterval(progress.getInterval());
        pending.setEaseFactor(progress.getEaseFactor());
        pending.setNextReviewMillis(timestampMillis(progress.getNextReview(), now));
        pending.setLastReviewedMillis(timestampMillis(progress.getLastReviewed(), now));
        pending.setCreatedAtMillis(now);
        return pending;
    }

    private Map<String, Object> toFirestoreMap(PendingUserProgress pending) {
        Map<String, Object> data = new HashMap<>();
        data.put("progressId", pending.getProgressId());
        data.put("userId", pending.getUserId());
        data.put("cardId", pending.getCardId());
        data.put("setId", pending.getSetId());
        data.put("status", pending.getStatus());
        data.put("interval", pending.getInterval());
        data.put("easeFactor", pending.getEaseFactor());
        data.put("nextReview", new Timestamp(new Date(pending.getNextReviewMillis())));
        data.put("lastReviewed", new Timestamp(new Date(pending.getLastReviewedMillis())));
        return data;
    }

    private long timestampMillis(Timestamp timestamp, long fallback) {
        return timestamp != null ? timestamp.toDate().getTime() : fallback;
    }
}
