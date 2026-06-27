package com.example.vocabmaster.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.MainActivity;
import com.example.vocabmaster.databinding.ActivityLoginBinding;
import com.example.vocabmaster.ui.admin.AdminActivity;
import com.example.vocabmaster.ui.common.MotionSystem;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private static final String SUPER_ADMIN_EMAIL = "23521406@gm.uit.edu.vn";
    private ActivityLoginBinding binding;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        MotionSystem.applyPressState(binding.btnLogin);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            if (currentUser.isEmailVerified()) {
                routeVerifiedUser(currentUser);
            } else {
                // Nếu user chưa verify mà lỡ kẹt ở đây thì yêu cầu verify
                UiFeedback.showSnack(binding.getRoot(), "Please verify your email to continue.");
            }
        }

        // Chuyển sang trang Register khi nhấn tab Sign Up hoặc dòng text bên dưới
        View.OnClickListener goToRegister = v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        };
        binding.textRegister.setOnClickListener(goToRegister);

        binding.btnLogin.setOnClickListener(v -> loginUser());
        binding.textForgotPassword.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class)));
    }

    private void loginUser() {
        String email = binding.editEmail.getText().toString().trim();
        String password = binding.editPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            binding.editEmail.setError("Email is required");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            binding.editPassword.setError("Password is required");
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    binding.progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null && user.isEmailVerified()) {
                            routeVerifiedUser(user);
                        } else {
                            UiFeedback.showErrorDialog(this, "Email not verified",
                                    "Please check your inbox and verify your email address before logging in.");
                            mAuth.signOut();
                        }
                    } else {
                        UiFeedback.showErrorDialog(this, "Sign in failed",
                                task.getException() != null ? task.getException().getMessage() : "Unknown error");
                    }
                });
    }

    private void routeVerifiedUser(FirebaseUser user) {
        binding.progressBar.setVisibility(View.VISIBLE);
        FirebaseFirestore.getInstance().collection("users").document(user.getUid()).get()
                .addOnSuccessListener(snapshot -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Boolean banned = snapshot.getBoolean("banned");
                    Boolean disabled = snapshot.getBoolean("disabled");
                    String accountStatus = snapshot.getString("accountStatus");
                    if (Boolean.TRUE.equals(banned)
                            || Boolean.TRUE.equals(disabled)
                            || "banned".equalsIgnoreCase(accountStatus)) {
                        mAuth.signOut();
                        UiFeedback.showErrorDialog(this, "Account locked",
                                "Your account has been locked by the administrator.");
                        return;
                    }

                    String role = snapshot.getString("role");
                    Intent intent = (isAdminRole(role) || isSeedSuperAdmin(user))
                            ? new Intent(LoginActivity.this, AdminActivity.class)
                            : new Intent(LoginActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    MotionSystem.startScreen(this, intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    UiFeedback.showErrorDialog(this, "Sign in failed",
                            e.getMessage() != null ? e.getMessage() : "Could not verify account status");
                });
    }

    private boolean isAdminRole(String role) {
        return "admin".equalsIgnoreCase(role) || "super_admin".equalsIgnoreCase(role);
    }

    private boolean isSeedSuperAdmin(FirebaseUser user) {
        return user.getEmail() != null && SUPER_ADMIN_EMAIL.equalsIgnoreCase(user.getEmail().trim());
    }
}
