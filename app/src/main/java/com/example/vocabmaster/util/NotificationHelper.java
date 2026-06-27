package com.example.vocabmaster.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.vocabmaster.R;
import com.example.vocabmaster.MainActivity;

import java.util.Map;

public class NotificationHelper {
    public static final String STUDY_REMINDER_CHANNEL_ID = "study_reminder_channel";
    public static final String GENERAL_CHANNEL_ID = "general_notification_channel";

    public static void createNotificationChannel(Context context) {
        createNotificationChannels(context);
    }

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel reminderChannel = new NotificationChannel(
                    STUDY_REMINDER_CHANNEL_ID,
                    "Nhắc học mỗi ngày",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            reminderChannel.setDescription("Nhắc bạn học từ vựng để giữ streak");

            NotificationChannel generalChannel = new NotificationChannel(
                    GENERAL_CHANNEL_ID,
                    "Thông báo VocabMaster",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            generalChannel.setDescription("Thông báo từ hệ thống và bạn bè");

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(reminderChannel);
                notificationManager.createNotificationChannel(generalChannel);
            }
        }
    }

    public static void showStudyReminder(Context context, String title, String message) {
        showNotification(context, STUDY_REMINDER_CHANNEL_ID, title, message, null);
    }

    public static void showRemoteMessage(Context context, String title, String message, Map<String, String> data) {
        showNotification(context, GENERAL_CHANNEL_ID, title, message, data);
    }

    private static void showNotification(Context context, String channelId, String title, String message, Map<String, String> data) {
        if (!canShowNotifications(context)) return;

        createNotificationChannels(context);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (data != null && "library".equals(data.get("destination"))) {
            intent.putExtra("navigate_to_library", true);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context).notify((int) System.currentTimeMillis(), builder.build());
    }

    public static boolean canShowNotifications(Context context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false;
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }
}
