package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.LearningProfile;
import com.example.vocabmaster.data.repository.AssessmentRepository;
import com.example.vocabmaster.databinding.ActivityPlacementTestBinding;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;
import java.util.Map;

public class PlacementTestActivity extends AppCompatActivity {
    private ActivityPlacementTestBinding binding;
    private AssessmentRepository assessmentRepository;
    private String testId;
    private String currentQuestionId;
    private String selectedAnswer;
    private int questionCount = 0;
    private final int MAX_QUESTIONS = 15;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlacementTestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        assessmentRepository = new AssessmentRepository();
        
        setupListeners();
        startTest();
    }

    private void setupListeners() {
        binding.btnCloseTest.setOnClickListener(v -> finish());
        
        binding.btnSubmitAnswer.setOnClickListener(v -> {
            if (selectedAnswer != null) {
                submitAnswer();
            }
        });
    }

    private void startTest() {
        String uid = FirebaseAuth.getInstance().getUid();
        String language = getIntent().getStringExtra("language");
        
        assessmentRepository.startPlacementTest(uid, language, new AssessmentRepository.ApiCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> result) {
                testId = (String) result.get("test_id");
                displayQuestion((Map<String, Object>) result.get("first_question"));
            }

            @Override
            public void onError(Throwable t) {
                Toast.makeText(PlacementTestActivity.this, "Lỗi khởi tạo test", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayQuestion(Map<String, Object> questionData) {
        if (questionData == null) return;
        
        questionCount++;
        binding.testProgress.setProgress((questionCount * 100) / MAX_QUESTIONS);
        
        currentQuestionId = (String) questionData.get("id");
        binding.textSkillTag.setText((String) questionData.get("skill"));
        binding.textQuestionContent.setText((String) questionData.get("text"));
        
        binding.layoutOptions.removeAllViews();
        List<String> options = (List<String>) questionData.get("options");
        
        RadioGroup radioGroup = new RadioGroup(this);
        radioGroup.setLayoutParams(new RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.MATCH_PARENT, RadioGroup.LayoutParams.WRAP_CONTENT));

        for (String option : options) {
            RadioButton rb = new RadioButton(this);
            rb.setText(option);
            rb.setTextSize(16);
            rb.setPadding(20, 32, 20, 32);
            rb.setButtonDrawable(null);
            rb.setBackgroundResource(R.drawable.bg_selectable_item_3d);
            rb.setTextColor(Color.parseColor("#4046d8"));
            
            RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.MATCH_PARENT, RadioGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, 24);
            rb.setLayoutParams(params);
            
            rb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedAnswer = option;
                    binding.btnSubmitAnswer.setEnabled(true);
                }
            });
            
            radioGroup.addView(rb);
        }
        
        binding.layoutOptions.addView(radioGroup);
        binding.btnSubmitAnswer.setEnabled(false);
        binding.btnSubmitAnswer.setText("Kiểm tra");
    }

    private void submitAnswer() {
        binding.btnSubmitAnswer.setEnabled(false);
        binding.btnSubmitAnswer.setText("Đang xử lý...");

        assessmentRepository.submitPlacementAnswer(testId, currentQuestionId, selectedAnswer, new AssessmentRepository.ApiCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> result) {
                boolean isFinished = (boolean) result.getOrDefault("is_finished", false);
                if (isFinished || questionCount >= MAX_QUESTIONS) {
                    completeTest();
                } else {
                    displayQuestion((Map<String, Object>) result.get("next_question"));
                }
            }

            @Override
            public void onError(Throwable t) {
                Toast.makeText(PlacementTestActivity.this, "Lỗi gửi câu trả lời", Toast.LENGTH_SHORT).show();
                binding.btnSubmitAnswer.setEnabled(true);
            }
        });
    }

    private void completeTest() {
        assessmentRepository.completePlacementTest(testId, new AssessmentRepository.ApiCallback<LearningProfile>() {
            @Override
            public void onSuccess(LearningProfile profile) {
                Intent resultIntent = new Intent();
                resultIntent.putExtra("cefr_level", profile.getCefrLevel());
                resultIntent.putExtra("profile_id", profile.getProfileId());
                setResult(RESULT_OK, resultIntent);
                finish();
            }

            @Override
            public void onError(Throwable t) {
                Toast.makeText(PlacementTestActivity.this, "Lỗi hoàn tất bài test", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
