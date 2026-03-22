package com.example.vocabmaster.ui.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.databinding.ActivityForgotPasswordBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private ActivityForgotPasswordBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnSendResetLink.setOnClickListener(v -> sendResetEmail());
        binding.btnBackLogin.setOnClickListener(v -> finish());
    }

    private void sendResetEmail() {
        String email = binding.editEmail.getText() == null ? "" : binding.editEmail.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            binding.editEmail.setError("Enter your account email");
            return;
        }
        binding.progressBar.setVisibility(View.VISIBLE);
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    binding.progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        UiFeedback.showSnack(binding.getRoot(), "Password reset email sent");
                        finish();
                    } else {
                        UiFeedback.showErrorDialog(this, "Reset failed", task.getException() != null ? task.getException().getMessage() : "Unknown error");
                    }
                });
    }
}
