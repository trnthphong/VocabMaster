package com.example.vocabmaster.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StudyReminderBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!StudyReminderScheduler.isReminderEnabled(context)) return;

        StudyReminderScheduler.scheduleDailyReminder(
                context,
                StudyReminderScheduler.getSavedHour(context),
                StudyReminderScheduler.getSavedMinute(context)
        );
    }
}
