package com.example.vocabmaster.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.work.WorkManager;

import java.util.Calendar;

public class StudyReminderScheduler {
    private static final String TAG = "StudyReminderScheduler";
    private static final String WORK_NAME = "daily_study_reminder";
    private static final String PREFS = "study_reminder_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_HOUR = "hour";
    private static final String KEY_MINUTE = "minute";
    private static final int REQUEST_CODE = 20260628;
    public static final int DEFAULT_REMINDER_HOUR = 20;
    public static final int DEFAULT_REMINDER_MINUTE = 0;

    private StudyReminderScheduler() {}

    public static void scheduleDailyReminder(Context context) {
        scheduleDailyReminder(context, DEFAULT_REMINDER_HOUR, DEFAULT_REMINDER_MINUTE);
    }

    public static void scheduleDailyReminder(Context context, int hour, int minute) {
        int safeHour = Math.max(0, Math.min(23, hour));
        int safeMinute = Math.max(0, Math.min(59, minute));
        Context appContext = context.getApplicationContext();
        savePreference(appContext, true, safeHour, safeMinute);

        WorkManager.getInstance(appContext).cancelUniqueWork(WORK_NAME);

        long triggerAtMillis = calculateNextTriggerMillis(safeHour, safeMinute);
        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = reminderPendingIntent(appContext, safeHour, safeMinute);
        if (alarmManager == null) return;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
            Log.d(TAG, "Study reminder scheduled at " + safeHour + ":" + safeMinute);
        } catch (SecurityException e) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            Log.w(TAG, "Exact alarm not allowed, scheduled inexact reminder.", e);
        }
    }

    public static void cancelDailyReminder(Context context) {
        Context appContext = context.getApplicationContext();
        savePreference(appContext, false, DEFAULT_REMINDER_HOUR, DEFAULT_REMINDER_MINUTE);
        WorkManager.getInstance(appContext).cancelUniqueWork(WORK_NAME);

        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(reminderPendingIntent(appContext, DEFAULT_REMINDER_HOUR, DEFAULT_REMINDER_MINUTE));
        }
    }

    public static boolean isReminderEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static int getSavedHour(Context context) {
        return prefs(context).getInt(KEY_HOUR, DEFAULT_REMINDER_HOUR);
    }

    public static int getSavedMinute(Context context) {
        return prefs(context).getInt(KEY_MINUTE, DEFAULT_REMINDER_MINUTE);
    }

    private static long calculateNextTriggerMillis(int hour, int minute) {
        Calendar now = Calendar.getInstance();
        Calendar reminderTime = Calendar.getInstance();
        reminderTime.set(Calendar.HOUR_OF_DAY, hour);
        reminderTime.set(Calendar.MINUTE, minute);
        reminderTime.set(Calendar.SECOND, 0);
        reminderTime.set(Calendar.MILLISECOND, 0);

        if (!reminderTime.after(now)) {
            reminderTime.add(Calendar.DAY_OF_YEAR, 1);
        }

        return reminderTime.getTimeInMillis();
    }

    private static PendingIntent reminderPendingIntent(Context context, int hour, int minute) {
        Intent intent = new Intent(context, StudyReminderReceiver.class);
        intent.setAction(StudyReminderReceiver.ACTION_SHOW_STUDY_REMINDER);
        intent.putExtra(StudyReminderReceiver.EXTRA_HOUR, hour);
        intent.putExtra(StudyReminderReceiver.EXTRA_MINUTE, minute);
        return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static void savePreference(Context context, boolean enabled, int hour, int minute) {
        prefs(context).edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
