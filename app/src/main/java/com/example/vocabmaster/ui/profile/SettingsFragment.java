package com.example.vocabmaster.ui.profile;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.databinding.FragmentSettingsBinding;
import com.example.vocabmaster.ui.auth.LoginActivity;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.example.vocabmaster.util.FcmTokenManager;
import com.example.vocabmaster.util.NotificationPermissionHelper;
import com.example.vocabmaster.util.SoundEffectManager;
import com.example.vocabmaster.util.StudyReminderScheduler;
import com.example.vocabmaster.util.ThemeUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;
    private FirebaseFirestore db;
    private User currentUser;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private boolean applyingNotificationSetting;
    private boolean applyingSoundEffectsSetting;
    private int reminderHour = StudyReminderScheduler.DEFAULT_REMINDER_HOUR;
    private int reminderMinute = StudyReminderScheduler.DEFAULT_REMINDER_MINUTE;

    private final String[] avatarValues = {"bear", "cat", "dog", "bird", "snake", "tiger", "rabbit"};
    private final int[] avatarResIds = {
            R.drawable.bear, R.drawable.cat, R.drawable.dog,
            R.drawable.bird, R.drawable.snake, R.drawable.tiger,
            R.drawable.rabbit
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();
        return binding.getRoot();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (binding == null || !isAdded()) return;
                    applyingNotificationSetting = true;
                    binding.switchNotifications.setChecked(granted);
                    applyingNotificationSetting = false;
                    applyNotificationSetting(granted);
                }
        );
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        // --- Account Section ---
        binding.layoutEditProfile.setOnClickListener(v -> showEditProfileDialog());
        binding.layoutChangePassword.setOnClickListener(v -> resetPassword());

        // --- App Preferences ---
        binding.switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ThemeUtils.applyTheme(isChecked);
            saveSetting("darkMode", isChecked);
        });
        binding.switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (applyingNotificationSetting) return;
            handleNotificationToggle(isChecked);
        });
        binding.layoutReminderTime.setOnClickListener(v -> showReminderTimePicker());
        binding.switchSoundEffects.setChecked(SoundEffectManager.isEnabled(requireContext()));
        binding.switchSoundEffects.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (applyingSoundEffectsSetting) return;
            SoundEffectManager.setEnabled(requireContext(), isChecked);
            saveSetting("soundEffectsEnabled", isChecked);
            if (isChecked) {
                SoundEffectManager.playTap(requireContext());
            }
        });

        // --- Subscription Section ---
        binding.layoutBuyPlan.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.navigation_premium));

        binding.layoutManagePlans.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.navigation_manage_plans));

        // --- About Section ---
        binding.layoutHelp.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.navigation_support_center));
        binding.layoutTerms.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.navigation_terms_policy));

        binding.btnLogout.setOnClickListener(v -> showLogoutConfirmation());

        loadSettings();
    }

    private void showEditProfileDialog() {
        String[] options = {"Đổi ảnh đại diện", "Đổi tên hiển thị"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Chỉnh sửa hồ sơ")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showAvatarSelectionDialog();
                    else showEditNameDialog();
                })
                .show();
    }

    private void showAvatarSelectionDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_avatar_selection, null);
        GridView gridView = dialogView.findViewById(R.id.grid_avatars);
        AvatarAdapter adapter = new AvatarAdapter(getLayoutInflater(), avatarResIds);
        gridView.setAdapter(adapter);
        
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(dialogView).create();
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            saveSetting("avatar", avatarValues[position]);
            dialog.dismiss();
            Toast.makeText(getContext(), "Đã cập nhật ảnh đại diện", Toast.LENGTH_SHORT).show();
        });
        dialog.show();
    }

    private void showEditNameDialog() {
        android.widget.EditText editText = new android.widget.EditText(requireContext());
        if (currentUser != null) {
            editText.setText(currentUser.getName());
        }
        
        new AlertDialog.Builder(requireContext())
                .setTitle("Đổi tên hiển thị")
                .setView(editText)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String newName = editText.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        saveSetting("name", newName);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void resetPassword() {
        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        if (email != null) {
            FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                    .addOnSuccessListener(unused -> 
                            Toast.makeText(getContext(), "Email đặt lại mật khẩu đã được gửi!", Toast.LENGTH_LONG).show())
                    .addOnFailureListener(e -> 
                            Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private void loadSettings() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        db.collection("users").document(uid).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists() && isAdded()) {
                currentUser = snapshot.toObject(User.class);
                if (currentUser != null) {
                    binding.switchDarkMode.setChecked(currentUser.isDarkMode());
                    applyingNotificationSetting = true;
                    binding.switchNotifications.setChecked(currentUser.isNotificationsEnabled());
                    applyingNotificationSetting = false;
                    reminderHour = normalizeHour(currentUser.getReminderHour());
                    reminderMinute = normalizeMinute(currentUser.getReminderMinute());
                    updateReminderTimeText();
                    applyingSoundEffectsSetting = true;
                    binding.switchSoundEffects.setChecked(currentUser.isSoundEffectsEnabled());
                    applyingSoundEffectsSetting = false;
                    SoundEffectManager.setEnabled(requireContext(), currentUser.isSoundEffectsEnabled());
                }
            }
        });
    }

    private void saveSetting(String key, Object value) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        db.collection("users").document(uid).update(key, value)
                .addOnSuccessListener(unused -> {
                    if (isAdded()) UiFeedback.showSnack(binding.getRoot(), "Đã cập nhật cài đặt");
                });
    }

    private void handleNotificationToggle(boolean enabled) {
        if (!enabled) {
            applyNotificationSetting(false);
            return;
        }

        if (NotificationPermissionHelper.hasPermission(requireContext())) {
            applyNotificationSetting(true);
            return;
        }

        NotificationPermissionHelper.showPermissionDialog(
                requireContext(),
                () -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS),
                () -> {
                    applyingNotificationSetting = true;
                    binding.switchNotifications.setChecked(false);
                    applyingNotificationSetting = false;
                    applyNotificationSetting(false);
                }
        );
    }

    private void applyNotificationSetting(boolean enabled) {
        saveSetting("notificationsEnabled", enabled);
        if (enabled) {
            StudyReminderScheduler.scheduleDailyReminder(requireContext(), reminderHour, reminderMinute);
            FcmTokenManager.syncCurrentUserToken();
        } else {
            StudyReminderScheduler.cancelDailyReminder(requireContext());
        }
    }

    private void showReminderTimePicker() {
        new TimePickerDialog(
                requireContext(),
                (view, hourOfDay, minute) -> {
                    reminderHour = normalizeHour(hourOfDay);
                    reminderMinute = normalizeMinute(minute);
                    updateReminderTimeText();
                    saveReminderTime();
                    if (binding.switchNotifications.isChecked()) {
                        StudyReminderScheduler.scheduleDailyReminder(requireContext(), reminderHour, reminderMinute);
                    }
                },
                reminderHour,
                reminderMinute,
                true
        ).show();
    }

    private void saveReminderTime() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        Map<String, Object> update = new HashMap<>();
        update.put("reminderHour", reminderHour);
        update.put("reminderMinute", reminderMinute);
        db.collection("users").document(uid).update(update)
                .addOnSuccessListener(unused -> {
                    if (isAdded()) UiFeedback.showSnack(binding.getRoot(), "Đã cập nhật giờ nhắc học");
                });
    }

    private void updateReminderTimeText() {
        if (binding == null) return;
        binding.textReminderTime.setText(String.format(Locale.US, "%02d:%02d", reminderHour, reminderMinute));
    }

    private int normalizeHour(int hour) {
        return Math.max(0, Math.min(23, hour));
    }

    private int normalizeMinute(int minute) {
        return Math.max(0, Math.min(59, minute));
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Đăng xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất không?")
                .setPositiveButton("Đăng xuất", (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    Intent intent = new Intent(requireContext(), LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    requireActivity().finish();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
