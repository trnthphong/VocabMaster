package com.example.vocabmaster.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.databinding.ActivityRegisterBinding;
import com.example.vocabmaster.ui.common.MotionSystem;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

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

        View.OnClickListener goToLogin = v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        };

        binding.textLogin.setOnClickListener(goToLogin);
        binding.btnRegister.setOnClickListener(v -> registerUser());
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
                        generateUniqueShortId(shortId -> {
                            Map<String, Object> user = new HashMap<>();
                            user.put("name", name);
                            user.put("email", email);
                            user.put("shortId", shortId);
                            user.put("premium", false);
                            user.put("xp", 0);
                            user.put("streak", 0);
                            user.put("hearts", 5);
                            user.put("role", "user");
                            user.put("darkMode", false);
                            user.put("dailyGoal", 20);
                            user.put("createdAt", System.currentTimeMillis());

                            db.collection("users").document(userId)
                                    .set(user)
                                    .addOnSuccessListener(aVoid -> sendEmailVerification())
                                    .addOnFailureListener(e -> {
                                        binding.progressBar.setVisibility(View.GONE);
                                        UiFeedback.showErrorDialog(this, "Registration error", e.getMessage());
                                    });
                        });
                    } else {
                        binding.progressBar.setVisibility(View.GONE);
                        UiFeedback.showErrorDialog(this, "Registration failed",
                                task.getException() != null ? task.getException().getMessage() : "Unknown error");
                    }
                });
    }

    private interface ShortIdCallback {
        void onGenerated(String shortId);
    }

    private void generateUniqueShortId(ShortIdCallback callback) {
        Random random = new Random();
        String candidateId = String.valueOf(100000 + random.nextInt(900000)); // Tạo số có 6 chữ số

        db.collection("users").whereEqualTo("shortId", candidateId).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        callback.onGenerated(candidateId);
                    } else {
                        generateUniqueShortId(callback); // Thử lại nếu trùng
                    }
                });
    }

    private void sendEmailVerification() {
        if (mAuth.getCurrentUser() != null) {
            mAuth.getCurrentUser().sendEmailVerification()
                    .addOnCompleteListener(task -> {
                        binding.progressBar.setVisibility(View.GONE);
                        if (task.isSuccessful()) {
                            UiFeedback.showSnack(binding.getRoot(), "Verification email sent. Please check your inbox.");
                            startActivity(new Intent(RegisterActivity.this, VerifyEmailActivity.class));
                            finish();
                        } else {
                            UiFeedback.showErrorDialog(this, "Verification failed",
                                    task.getException() != null ? task.getException().getMessage() : "Could not send verification email");
                        }
                    });
        }
    }
}
