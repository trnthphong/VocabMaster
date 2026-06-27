package com.example.vocabmaster.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StudyReminderReceiver extends BroadcastReceiver {
    public static final String ACTION_SHOW_STUDY_REMINDER = "com.example.vocabmaster.SHOW_STUDY_REMINDER";
    public static final String EXTRA_HOUR = "hour";
    public static final String EXTRA_MINUTE = "minute";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_SHOW_STUDY_REMINDER.equals(intent.getAction())) return;

        int hour = intent.getIntExtra(EXTRA_HOUR, StudyReminderScheduler.getSavedHour(context));
        int minute = intent.getIntExtra(EXTRA_MINUTE, StudyReminderScheduler.getSavedMinute(context));

        NotificationHelper.showStudyReminder(
                context,
                "Đến giờ học rồi!",
                "Đừng để mất streak hôm nay nha. Vào học vài phút thôi là đủ giữ nhịp rồi."
        );

        if (StudyReminderScheduler.isReminderEnabled(context)) {
            StudyReminderScheduler.scheduleDailyReminder(context, hour, minute);
        }
    }
}
