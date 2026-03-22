package com.example.vocabmaster.ui.common;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

public final class UiFeedback {

    private UiFeedback() {
    }

    public static void showSnack(View anchor, String message) {
        if (anchor == null) return;
        Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT).show();
    }

    public static void showSnackAction(View anchor, String message, String actionLabel, View.OnClickListener action) {
        if (anchor == null) return;
        Snackbar.make(anchor, message, Snackbar.LENGTH_LONG)
                .setAction(actionLabel, action)
                .show();
    }

    public static void showErrorDialog(Context context, String title, String message) {
        if (context == null) return;
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    public static void showConfirmDialog(Context context, String title, String message, Runnable onConfirm) {
        if (context == null) return;
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    if (onConfirm != null) onConfirm.run();
                })
                .show();
    }

    public static void performHaptic(Context context, int millis) {
        if (context == null) return;
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(millis);
        }
    }
}
