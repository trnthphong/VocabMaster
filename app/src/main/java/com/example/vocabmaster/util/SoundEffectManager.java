package com.example.vocabmaster.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;

public final class SoundEffectManager {
    private static final String PREFS_NAME = "vocabmaster_settings";
    private static final String KEY_SOUND_EFFECTS_ENABLED = "sound_effects_enabled";
    private static final int VOLUME = 55;

    private SoundEffectManager() {
    }

    public static boolean isEnabled(Context context) {
        if (context == null) return true;
        return prefs(context).getBoolean(KEY_SOUND_EFFECTS_ENABLED, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        if (context == null) return;
        prefs(context).edit().putBoolean(KEY_SOUND_EFFECTS_ENABLED, enabled).apply();
    }

    public static void playFlip(Context context) {
        playTone(context, ToneGenerator.TONE_PROP_ACK, 70);
    }

    public static void playCorrect(Context context) {
        playTone(context, ToneGenerator.TONE_PROP_BEEP2, 120);
    }

    public static void playWrong(Context context) {
        playTone(context, ToneGenerator.TONE_PROP_NACK, 180);
    }

    public static void playTap(Context context) {
        playTone(context, ToneGenerator.TONE_PROP_BEEP, 60);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static void playTone(Context context, int toneType, int durationMs) {
        if (context == null || !isEnabled(context)) return;

        ToneGenerator toneGenerator;
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME);
            toneGenerator.startTone(toneType, durationMs);
        } catch (RuntimeException ignored) {
            return;
        }

        new Handler(Looper.getMainLooper()).postDelayed(toneGenerator::release, durationMs + 100L);
    }
}
