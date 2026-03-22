package com.example.vocabmaster.ui.common;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

import com.example.vocabmaster.R;

public final class MotionSystem {

    private MotionSystem() {
    }

    public static void startScreen(Activity activity, Intent intent) {
        activity.startActivity(intent);
        activity.overridePendingTransition(R.anim.screen_enter_right, R.anim.screen_exit_left);
    }

    public static void applyPressState(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }
}
