package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.MainActivity;
import com.example.vocabmaster.R;
import com.example.vocabmaster.databinding.ActivityJourneySetupBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class JourneySetupActivity extends AppCompatActivity {
    private ActivityJourneySetupBinding binding;
    private int currentStep = 0;
    private String selectedTopic = "";
    private String displayTitle = "";
    private String langCode = "en";
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
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnAction.setOnClickListener(v -> {
            if (currentStep == 0) {
                if (validateStep()) {
                    saveLanguagePreference();
                }
            }
        });
    }

    private boolean validateStep() {
        if (binding.rgLanguages.getCheckedRadioButtonId() == -1) {
            Toast.makeText(this, "Vui lòng chọn ngôn ngữ", Toast.LENGTH_SHORT).show();
            return false;
        }
        RadioButton rb = findViewById(binding.rgLanguages.getCheckedRadioButtonId());
        langCode = rb.getId() == R.id.rb_en ? "en" : "ru";
        return true;
    }

    private void saveLanguagePreference() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("language", langCode);
            
            String unitTitle = (displayTitle != null && !displayTitle.isEmpty()) ? 
                              "Chủ đề: " + displayTitle : "Khởi đầu mới";
            updates.put("currentUnitTitle", unitTitle);

            FirebaseFirestore.getInstance().collection("users").document(uid)
                    .update(updates)
                    .addOnCompleteListener(task -> startProcessing());
        } else {
            startProcessing();
        }
    }

    private void updateUI() {
        binding.viewFlipper.setDisplayedChild(currentStep);
        binding.progressSetup.setProgress(50);
        
        if (currentStep == 3) {
            binding.headerJourney.setVisibility(View.GONE);
            binding.layoutBottom.setVisibility(View.GONE);
        } else {
            binding.headerJourney.setVisibility(View.VISIBLE);
            binding.layoutBottom.setVisibility(View.VISIBLE);
            binding.btnAction.setText(isChangeOnly ? "Cập nhật" : "Khám phá ngay");
        }
    }

    private void startProcessing() {
        currentStep = 3;
        updateUI();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isChangeOnly) {
                // Để thay đổi ngôn ngữ có hiệu lực ngay lập tức, 
                // chúng ta cần khởi động lại MainActivity và xóa các màn hình cũ.
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            } else {
                Intent intent = new Intent(this, TopicWordListActivity.class);
                intent.putExtra("topic", selectedTopic);
                intent.putExtra("display_title", displayTitle);
                intent.putExtra("lang_code", langCode);
                startActivity(intent);
            }
            finish();
        }, 1500);
    }
}
