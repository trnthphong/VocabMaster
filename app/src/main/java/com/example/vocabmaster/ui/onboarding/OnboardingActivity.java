package com.example.vocabmaster.ui.onboarding;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.ui.auth.LoginActivity;
import com.example.vocabmaster.databinding.ActivityOnboardingBinding;

public class OnboardingActivity extends AppCompatActivity {

    private ActivityOnboardingBinding binding;
    private int page = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences("vocabmaster_prefs", MODE_PRIVATE);
        if (prefs.getBoolean("onboarding_done", false)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        renderPage();
        binding.btnNext.setOnClickListener(v -> {
            if (page < 2) {
                page++;
                renderPage();
            } else {
                completeOnboarding();
            }
        });
        binding.btnSkip.setOnClickListener(v -> completeOnboarding());
    }

    private void renderPage() {
        if (page == 0) {
            binding.textTitle.setText("Master vocabulary faster");
            binding.textBody.setText("Learn with adaptive flashcards and spaced repetition.");
            binding.indicator.setText("1/3");
            binding.btnNext.setText("Next");
        } else if (page == 1) {
            binding.textTitle.setText("Play while you learn");
            binding.textBody.setText("Train with Match, MCQ, and Speed Challenge mini-games.");
            binding.indicator.setText("2/3");
            binding.btnNext.setText("Next");
        } else {
            binding.textTitle.setText("Track your real progress");
            binding.textBody.setText("Earn XP, protect hearts, and keep your daily streak alive.");
            binding.indicator.setText("3/3");
            binding.btnNext.setText("Get started");
        }
    }

    private void completeOnboarding() {
        SharedPreferences prefs = getSharedPreferences("vocabmaster_prefs", MODE_PRIVATE);
        prefs.edit().putBoolean("onboarding_done", true).apply();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
