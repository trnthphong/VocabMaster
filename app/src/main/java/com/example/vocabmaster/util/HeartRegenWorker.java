package com.example.vocabmaster.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.vocabmaster.data.gamification.GamificationCalculator;
import com.example.vocabmaster.data.gamification.GamificationConstants;
import com.example.vocabmaster.data.repository.GamificationRepository;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;

import java.util.concurrent.TimeUnit;

public class HeartRegenWorker extends Worker {
    private static final String KEY_UID = "uid";
    private static final String WORK_PREFIX = "heart_regen_";

    public HeartRegenWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public static void schedule(
            Context context,
            String uid,
            int hearts,
            long lastHeartRegenMillis,
            boolean premium
    ) {
        if (context == null || uid == null || uid.trim().isEmpty()) return;

        WorkManager workManager = WorkManager.getInstance(context.getApplicationContext());
        String workName = WORK_PREFIX + uid;

        if (premium || hearts >= GamificationConstants.MAX_HEARTS) {
            workManager.cancelUniqueWork(workName);
            return;
        }

        long now = System.currentTimeMillis();
        long nextHeartAtMillis = GamificationCalculator.nextHeartAtMillis(hearts, lastHeartRegenMillis, now);
        long delayMillis = nextHeartAtMillis > 0L ? Math.max(0L, nextHeartAtMillis - now) : 0L;

        Data input = new Data.Builder()
                .putString(KEY_UID, uid)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(HeartRegenWorker.class)
                .setInputData(input)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build();

        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        String uid = getInputData().getString(KEY_UID);
        if (uid == null || uid.trim().isEmpty()) {
            uid = FirebaseAuth.getInstance().getUid();
        }
        if (uid == null || uid.trim().isEmpty()) {
            return Result.success();
        }

        try {
            GamificationRepository repository = new GamificationRepository(getApplicationContext());
            GamificationRepository.HeartStateResult result =
                    Tasks.await(repository.recoverHeartsIfDue(uid));
            schedule(getApplicationContext(), uid, result.getHearts(), result.getLastHeartRegenMillis(), result.isPremium());
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
