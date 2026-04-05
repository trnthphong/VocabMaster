package com.example.vocabmaster.ui.study;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.databinding.ActivityStudySummaryBinding;
import com.example.vocabmaster.ui.library.CourseDetailActivity;

import nl.dionsegijn.konfetti.core.Party;
import nl.dionsegijn.konfetti.core.PartyFactory;
import nl.dionsegijn.konfetti.core.emitter.Emitter;
import nl.dionsegijn.konfetti.core.emitter.EmitterConfig;
import nl.dionsegijn.konfetti.core.models.Shape;
import nl.dionsegijn.konfetti.core.models.Size;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class StudySummaryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityStudySummaryBinding binding = ActivityStudySummaryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        int xp = getIntent().getIntExtra("xp", 0);
        String lessonId = getIntent().getStringExtra("lesson_id");

        binding.textXpEarned.setText("+" + xp + " XP");
        binding.textAccuracy.setText("100%"); // In this flow, we complete all challenges
        binding.textCorrect.setText("Completed!");
        binding.textHeartsImpact.setVisibility(View.GONE);

        // Confetti
        EmitterConfig emitterConfig = new Emitter(5L, TimeUnit.SECONDS).perSecond(30);
        Party party = new PartyFactory(emitterConfig)
                .angle(270)
                .spread(360)
                .setSpeedBetween(10f, 30f)
                .position(0.5, 0.3)
                .shapes(Arrays.asList(Shape.Square.INSTANCE, Shape.Circle.INSTANCE))
                .sizes(new Size(12, 5f, 0.2f))
                .build();
        
        // Note: Needs KonfettiView in XML if using XML version, but keeping it simple for now
        // if (binding.konfettiView != null) binding.konfettiView.start(party);

        binding.btnDone.setOnClickListener(v -> {
            Intent intent = new Intent(this, CourseDetailActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }
}
