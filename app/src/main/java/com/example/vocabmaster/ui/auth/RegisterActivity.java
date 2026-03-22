package com.example.vocabmaster.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.MainActivity;
import com.example.vocabmaster.databinding.ActivityRegisterBinding;
import com.example.vocabmaster.ui.common.MotionSystem;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        MotionSystem.applyPressState(binding.btnRegister);

        binding.btnRegister.setOnClickListener(v -> registerUser());
        binding.textLogin.setOnClickListener(v -> finish());
    }

    private void registerUser() {
        String name = binding.editName.getText().toString().trim();
        String email = binding.editEmail.getText().toString().trim();
        String password = binding.editPassword.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            binding.editName.setError("Name is required");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            binding.editEmail.setError("Email is required");
            return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            binding.editPassword.setError("Password must be >= 6 characters");
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        String userId = mAuth.getCurrentUser().getUid();
                        Map<String, Object> user = new HashMap<>();
                        user.put("name", name);
                        user.put("email", email);
                        user.put("premium", false);
                        user.put("xp", 0);
                        user.put("streak", 0);
                        user.put("hearts", 5);
                        user.put("role", "user");
                        user.put("darkMode", false);
                        user.put("dailyGoal", 20);

                        db.collection("users").document(userId)
                                .set(user)
                                .addOnSuccessListener(aVoid -> {
                                    binding.progressBar.setVisibility(View.GONE);
                                    UiFeedback.showSnack(binding.getRoot(), "Account created successfully");
                                    MotionSystem.startScreen(this, new Intent(RegisterActivity.this, MainActivity.class));
                                    finishAffinity();
                                })
                                .addOnFailureListener(e -> {
                                    binding.progressBar.setVisibility(View.GONE);
                                    UiFeedback.showErrorDialog(this, "Registration error", e.getMessage());
                                });
                    } else {
                        binding.progressBar.setVisibility(View.GONE);
                        UiFeedback.showErrorDialog(this, "Registration failed",
                                task.getException() != null ? task.getException().getMessage() : "Unknown error");
                    }
                });
    }
}