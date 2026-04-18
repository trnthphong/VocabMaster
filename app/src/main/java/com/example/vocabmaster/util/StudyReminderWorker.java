package com.example.vocabmaster.util;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class StudyReminderWorker extends Worker {

    public StudyReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String title = "Đã đến giờ học rồi! 📚";
        String message = "Đừng bỏ lỡ bài học hôm nay để duy trì chuỗi Streak của bạn nhé!";
        
        NotificationHelper.showStudyReminder(getApplicationContext(), title, message);
        
        return Result.success();
    }
}
