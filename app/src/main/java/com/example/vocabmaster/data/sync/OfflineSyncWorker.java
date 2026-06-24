package com.example.vocabmaster.data.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.vocabmaster.data.repository.OfflineProgressRepository;

public class OfflineSyncWorker extends Worker {
    private static final String WORK_NAME = "offline_progress_sync";

    public OfflineSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        OfflineProgressRepository repository = new OfflineProgressRepository(getApplicationContext());
        boolean synced = repository.syncPendingOnce();
        if (!synced) return Result.retry();
        return repository.pendingCount() > 0 ? Result.retry() : Result.success();
    }

    public static void enqueue(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(OfflineSyncWorker.class)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request);
    }
}
