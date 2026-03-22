package com.example.vocabmaster.ui.analytics;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.databinding.ActivityAnalyticsBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class AnalyticsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityAnalyticsBinding binding = ActivityAnalyticsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnClose.setOnClickListener(v -> finish());
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseFirestore.getInstance().collection("users").document(uid).get().addOnSuccessListener(snapshot -> {
            int xp = snapshot.getLong("xp") == null ? 0 : snapshot.getLong("xp").intValue();
            int streak = snapshot.getLong("streak") == null ? 0 : snapshot.getLong("streak").intValue();
            int hearts = snapshot.getLong("hearts") == null ? 0 : snapshot.getLong("hearts").intValue();
            int weeklyGoal = 140;
            int weeklyProgress = Math.min(weeklyGoal, xp % (weeklyGoal + 1));
            int retention = Math.max(20, Math.min(95, 60 + streak));

            binding.textWeeklyXp.setText(weeklyProgress + " / " + weeklyGoal + " XP");
            binding.progressWeeklyXp.setMax(weeklyGoal);
            binding.progressWeeklyXp.setProgress(weeklyProgress);
            binding.textRetention.setText(retention + "%");
            binding.progressRetention.setProgress(retention);
            binding.textHeartsTrend.setText("Hearts left: " + hearts);
        });
    }
}
