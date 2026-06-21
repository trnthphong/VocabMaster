package com.example.vocabmaster.ui.study;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.MainActivity;
import com.example.vocabmaster.databinding.ActivityStudySummaryBinding;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import nl.dionsegijn.konfetti.core.Party;
import nl.dionsegijn.konfetti.core.PartyFactory;
import nl.dionsegijn.konfetti.core.emitter.Emitter;
import nl.dionsegijn.konfetti.core.emitter.EmitterConfig;
import nl.dionsegijn.konfetti.core.models.Shape;
import nl.dionsegijn.konfetti.core.models.Size;

public class StudySummaryActivity extends AppCompatActivity {
    private ActivityStudySummaryBinding binding;
    private String courseId;
    private String lessonId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudySummaryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        int xp = getIntent().getIntExtra("xp", 0);
        int total = Math.max(1, getIntent().getIntExtra("total_challenges", 1));
        int correct = getIntent().getIntExtra("correct_challenges", total);
        lessonId = getIntent().getStringExtra("lesson_id");
        courseId = getIntent().getStringExtra("course_id");

        int accuracy = Math.round((correct * 100f) / total);
        int stars = getIntent().getIntExtra("stars", calculateStars(accuracy));
        binding.textXpEarned.setText("+" + xp + " XP");
        binding.textAccuracy.setText(accuracy + "%");
        binding.textCorrect.setText(correct + "/" + total);
        binding.textStarsEarned.setText(buildStars(stars));
        binding.textHeartsImpact.setText("Lesson complete. Keep the streak alive.");

        String nextLessonId = getIntent().getStringExtra("next_lesson_id");
        String nextLessonTitle = getIntent().getStringExtra("next_lesson_title");
        if (nextLessonId != null) {
            configureNextLessonButton(nextLessonId, nextLessonTitle);
        } else {
            binding.btnNextLesson.setVisibility(View.GONE);
            findNextLesson();
        }

        binding.btnDone.setOnClickListener(v -> goHome());
        showConfetti();
    }

    private int calculateStars(int accuracy) {
        if (accuracy >= 100) return 3;
        if (accuracy >= 75) return 2;
        return 1;
    }

    private String buildStars(int stars) {
        StringBuilder builder = new StringBuilder();
        int clampedStars = Math.max(1, Math.min(3, stars));
        for (int i = 0; i < clampedStars; i++) {
            builder.append("★");
        }
        for (int i = clampedStars; i < 3; i++) {
            builder.append("☆");
        }
        return builder.toString();
    }

    private void showConfetti() {
        EmitterConfig emitterConfig = new Emitter(300L, TimeUnit.MILLISECONDS).max(300);
        Party party = new PartyFactory(emitterConfig)
                .shapes(Shape.Circle.INSTANCE, Shape.Square.INSTANCE)
                .spread(360)
                .position(0.5, 0.3)
                .sizes(new Size(8, 50, 10))
                .colors(Arrays.asList(0xfce18a, 0xff726d, 0xb48def, 0xf4306d))
                .build();
        binding.konfettiView.start(party);
    }

    private void configureNextLessonButton(String nextLessonId, String nextLessonTitle) {
        if (nextLessonId == null) {
            binding.btnNextLesson.setVisibility(View.GONE);
            return;
        }
        String title = nextLessonTitle == null || nextLessonTitle.trim().isEmpty()
                ? "Next lesson"
                : nextLessonTitle;
        binding.btnNextLesson.setVisibility(View.VISIBLE);
        binding.btnNextLesson.setText("Học tiếp: " + title);
        binding.btnNextLesson.setOnClickListener(v -> {
            Intent intent = new Intent(this, StudyActivity.class);
            intent.putExtra("lesson_id", nextLessonId);
            intent.putExtra("lesson_title", title);
            intent.putExtra("course_id", courseId);
            startActivity(intent);
            finish();
        });
    }

    private void findNextLesson() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || courseId == null || lessonId == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(uid)
                .collection("personal_courses").document(courseId)
                .collection("units")
                .orderBy("orderNum", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(unitSnapshot -> {
                    List<Task<QuerySnapshot>> lessonTasks = new ArrayList<>();
                    for (DocumentSnapshot unitDoc : unitSnapshot.getDocuments()) {
                        lessonTasks.add(unitDoc.getReference()
                                .collection("lessons")
                                .orderBy("orderNum", Query.Direction.ASCENDING)
                                .get());
                    }

                    Tasks.whenAllSuccess(lessonTasks).addOnSuccessListener(results -> {
                        boolean foundCurrent = false;
                        for (Object result : results) {
                            QuerySnapshot lessons = (QuerySnapshot) result;
                            for (DocumentSnapshot lessonDoc : lessons.getDocuments()) {
                                if (foundCurrent) {
                                    configureNextLessonButton(lessonDoc.getId(), lessonDoc.getString("title"));
                                    return;
                                }
                                if (lessonId.equals(lessonDoc.getId())) {
                                    foundCurrent = true;
                                }
                            }
                        }
                        binding.btnNextLesson.setVisibility(View.GONE);
                    });
                });
    }

    private void goHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
