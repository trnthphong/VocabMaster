package com.example.vocabmaster.data.model;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import com.example.vocabmaster.R;
import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.ui.onboarding.OnboardingActivity;

public class IntroActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);

        // Đợi 2 giây rồi chuyển sang OnboardingActivity
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(IntroActivity.this, OnboardingActivity.class);
            startActivity(intent);
            finish(); // Quan trọng: Kết thúc Intro để user không quay lại được bằng nút Back
        }, 2000);
    }
}
