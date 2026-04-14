package com.example.vocabmaster.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.MainActivity;
import com.example.vocabmaster.databinding.ActivityVerifyEmailBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class VerifyEmailActivity extends AppCompatActivity {

    private ActivityVerifyEmailBinding binding;
    private FirebaseAuth mAuth;
    private Handler handler;
    private Runnable checkVerificationRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVerifyEmailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        handler = new Handler();

        binding.btnBack.setOnClickListener(v -> {
            mAuth.signOut();
            finish();
        });

        binding.btnResendEmail.setOnClickListener(v -> resendVerificationEmail());

        checkEmailVerificationStatus();
    }

    private void resendVerificationEmail() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.sendEmailVerification().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    UiFeedback.showSnack(binding.getRoot(), "Verification email resent.");
                } else {
                    UiFeedback.showErrorDialog(this, "Error", "Could not resend email.");
                }
            });
        }
    }

    private void checkEmailVerificationStatus() {
        checkVerificationRunnable = new Runnable() {
            @Override
            public void run() {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    user.reload().addOnCompleteListener(task -> {
                        if (user.isEmailVerified()) {
                            // Chuyển sang màn hình chính khi đã xác thực
                            startActivity(new Intent(VerifyEmailActivity.this, MainActivity.class));
                            finish();
                        } else {
                            // Tiếp tục kiểm tra sau mỗi 3 giây
                            handler.postDelayed(checkVerificationRunnable, 3000);
                        }
                    });
                }
            }
        };
        handler.postDelayed(checkVerificationRunnable, 3000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && checkVerificationRunnable != null) {
            handler.removeCallbacks(checkVerificationRunnable);
        }
    }
}
