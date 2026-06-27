package com.example.vocabmaster.util;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class NotificationPermissionHelper {
    private static final String PREFS = "notification_permission_prefs";
    private static final String KEY_STARTUP_DIALOG_SHOWN = "startup_dialog_shown";

    private NotificationPermissionHelper() {}

    public static boolean hasPermission(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean shouldShowStartupDialog(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !hasPermission(context)
                && !prefs(context).getBoolean(KEY_STARTUP_DIALOG_SHOWN, false);
    }

    public static void markStartupDialogShown(Context context) {
        prefs(context).edit().putBoolean(KEY_STARTUP_DIALOG_SHOWN, true).apply();
    }

    public static void showPermissionDialog(Context context, Runnable onConfirm, Runnable onCancel) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("Bật thông báo học tập?")
                .setMessage("VocabMaster sẽ gửi nhắc nhở học mỗi ngày để bạn không bị mất streak. Bạn có thể tắt lại trong Cài đặt.")
                .setNegativeButton("Để sau", (dialog, which) -> {
                    markStartupDialogShown(context);
                    if (onCancel != null) onCancel.run();
                })
                .setPositiveButton("Cho phép", (dialog, which) -> {
                    markStartupDialogShown(context);
                    if (onConfirm != null) onConfirm.run();
                })
                .show();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
