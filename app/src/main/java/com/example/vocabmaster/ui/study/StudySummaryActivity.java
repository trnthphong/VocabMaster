package com.example.vocabmaster.ui.study;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.databinding.ActivityStudySummaryBinding;

public class StudySummaryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityStudySummaryBinding binding = ActivityStudySummaryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        int total = getIntent().getIntExtra("total", 0);
        int correct = getIntent().getIntExtra("correct", 0);
        int xp = getIntent().getIntExtra("xp", 0);
        int heartsLost = getIntent().getIntExtra("hearts_lost", 0);
        int accuracy = total == 0 ? 0 : (correct * 100 / total);

        binding.textAccuracy.setText(accuracy + "%");
        binding.textCorrect.setText(correct + "/" + total + " correct");
        binding.textXpEarned.setText("XP earned: +" + xp);
        binding.textHeartsImpact.setText("Hearts lost: -" + heartsLost);
        binding.btnDone.setOnClickListener(v -> finish());
    }
}
