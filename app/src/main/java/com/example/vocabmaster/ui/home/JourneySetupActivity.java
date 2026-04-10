package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.MainActivity;
import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.databinding.ActivityJourneySetupBinding;
import com.example.vocabmaster.ui.library.CourseDetailActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class JourneySetupActivity extends AppCompatActivity {
    private static final String TAG = "JourneySetup";
    private ActivityJourneySetupBinding binding;
    private int currentStep = 0;
    
    // User selections
    private String selectedTopic = "";
    private String displayTitle = "";
    private String langCode = "en";
    private String selectedLevel = "A1"; 
    private String selectedGoal = "";
    
    private boolean isChangeOnly = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityJourneySetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        selectedTopic = getIntent().getStringExtra("selected_topic");
        displayTitle = getIntent().getStringExtra("display_title");
        isChangeOnly = getIntent().getBooleanExtra("is_change_only", false);

        setupListeners();
        updateUI();
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> {
            if (currentStep > 0 && currentStep < 3) {
                currentStep--;
                updateUI();
            } else {
                finish();
            }
        });

        binding.btnAction.setOnClickListener(v -> {
            if (validateStep()) {
                if (currentStep < 2) {
                    currentStep++;
                    updateUI();
                } else {
                    saveUserPreferences();
                }
            }
        });
    }

    private boolean validateStep() {
        switch (currentStep) {
            case 0: // Ngôn ngữ
                if (binding.rgLanguages.getCheckedRadioButtonId() == -1) {
                    Toast.makeText(this, "Vui lòng chọn ngôn ngữ", Toast.LENGTH_SHORT).show();
                    return false;
                }
                langCode = (binding.rgLanguages.getCheckedRadioButtonId() == R.id.rb_en) ? "en" : "ru";
                return true;

            case 1: // Trình độ (CEFR)
                int levelId = binding.rgLevels.getCheckedRadioButtonId();
                if (levelId == -1) {
                    Toast.makeText(this, "Vui lòng chọn trình độ", Toast.LENGTH_SHORT).show();
                    return false;
                }
                if (levelId == R.id.level_a1) selectedLevel = "A1";
                else if (levelId == R.id.level_a2) selectedLevel = "A2";
                else if (levelId == R.id.level_b1) selectedLevel = "B1";
                else if (levelId == R.id.level_b2) selectedLevel = "B2";
                else if (levelId == R.id.level_c1) selectedLevel = "C1";
                return true;

            case 2: // Mục tiêu
                if (binding.rgGoals.getCheckedRadioButtonId() == -1) {
                    Toast.makeText(this, "Vui lòng chọn mục tiêu", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
        }
        return true;
    }

    private void saveUserPreferences() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("language", langCode);
            updates.put("proficiencyLevel", selectedLevel);
            
            String unitTitle = (displayTitle != null && !displayTitle.isEmpty()) ? 
                              displayTitle : "Khởi đầu mới";
            updates.put("currentUnitTitle", "Chủ đề: " + unitTitle + " (" + selectedLevel + ")");

            FirebaseFirestore.getInstance().collection("users").document(uid)
                    .update(updates)
                    .addOnCompleteListener(task -> startProcessing());
        } else {
            startProcessing();
        }
    }

    private void updateUI() {
        binding.viewFlipper.setDisplayedChild(currentStep);
        
        // Cập nhật Progress Bar
        int progress = (currentStep + 1) * 33;
        binding.progressSetup.setProgress(progress);
        
        if (currentStep == 3) { // Màn hình Loading
            binding.headerJourney.setVisibility(View.GONE);
            binding.layoutBottom.setVisibility(View.GONE);
        } else {
            binding.headerJourney.setVisibility(View.VISIBLE);
            binding.layoutBottom.setVisibility(View.VISIBLE);
            binding.btnAction.setText(currentStep == 2 ? (isChangeOnly ? "Cập nhật" : "Khám phá ngay") : "Tiếp tục");
        }
    }

    private void startProcessing() {
        currentStep = 3; // Chuyển sang View Loading
        updateUI();

        // LOG CÁC TỪ VỰNG PHÙ HỢP YÊU CẦU (Topic + Level)
        logMatchingVocabularies();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isChangeOnly) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            } else {
                // Thay đổi: Sau khi setup xong, mở CourseDetailActivity (Lộ trình) thay vì TopicWordListActivity
                Intent intent = new Intent(this, CourseDetailActivity.class);
                intent.putExtra("course_theme", selectedTopic);
                intent.putExtra("display_title", displayTitle);
                intent.putExtra("lang_code", langCode);
                startActivity(intent);
            }
            finish();
        }, 2000);
    }

    private void logMatchingVocabularies() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String collectionPath = "ru".equals(langCode) ? "russian_vocabularies" : "vocabularies";
        
        Log.d(TAG, "--- ĐANG LỌC TỪ VỰNG ---");
        Log.d(TAG, "Ngôn ngữ: " + langCode);
        Log.d(TAG, "Chủ đề: " + selectedTopic);
        Log.d(TAG, "Trình độ (CEFR): " + selectedLevel);

        db.collection(collectionPath)
                .whereEqualTo("topic", selectedTopic.toLowerCase())
                .whereEqualTo("cefr", selectedLevel)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d(TAG, "Kết quả: Tìm thấy " + queryDocumentSnapshots.size() + " từ phù hợp.");
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Vocabulary v = doc.toObject(Vocabulary.class);
                        if (v != null) {
                            Log.i(TAG, "MATCHED: " + v.getWord() + " [" + selectedLevel + "] - " + v.getDefinition());
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Lỗi khi lọc từ vựng: ", e));
    }
}
