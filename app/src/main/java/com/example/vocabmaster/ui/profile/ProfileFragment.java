package com.example.vocabmaster.ui.profile;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.vocabmaster.MainActivity;
import com.example.vocabmaster.R;
import com.example.vocabmaster.databinding.FragmentProfileBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.Date;
import java.util.Random;

public class ProfileFragment extends Fragment {
    private FragmentProfileBinding binding;
    private FirebaseFirestore db;

    private final String[] avatarValues = {"bear", "cat", "dog", "bird", "snake", "tiger", "rabbit"};
    private final int[] avatarResIds = {
            R.drawable.bear, R.drawable.cat, R.drawable.dog,
            R.drawable.bird, R.drawable.snake, R.drawable.tiger,
            R.drawable.rabbit
    };

    private String userShortId = "N/A";
    private final Random random = new Random();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        setupTabLayout();

        binding.btnMenu.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openDrawer();
            }
        });
        
        binding.btnEditProfile.setOnClickListener(this::showEditProfilePopup);
        binding.cardAvatar.setOnClickListener(v -> showAvatarSelectionDialog());

        View.OnClickListener toSocial = v -> {
            BottomNavigationView navView = requireActivity().findViewById(R.id.nav_view);
            if (navView != null) {
                navView.setSelectedItemId(R.id.navigation_social);
            } else {
                NavHostFragment.findNavController(this).navigate(R.id.navigation_social);
            }
        };
        binding.btnAddFriendAction.setOnClickListener(toSocial);
        binding.btnQrCode.setOnClickListener(v -> showQRCodeDialog());
        
        binding.btnCopyId.setOnClickListener(v -> copyIdToClipboard());
    }

    private void copyIdToClipboard() {
        if (userShortId == null || userShortId.equals("N/A")) return;
        
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("User ID", userShortId);
        clipboard.setPrimaryClip(clip);
        
        Toast.makeText(requireContext(), "Đã sao chép ID: " + userShortId, Toast.LENGTH_SHORT).show();
    }

    private void showQRCodeDialog() {
        if (userShortId == null || userShortId.equals("N/A")) return;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_qr_code, null);
        ImageView qrImg = dialogView.findViewById(R.id.img_qr_code);
        TextView idText = dialogView.findViewById(R.id.text_qr_short_id);
        View closeBtn = dialogView.findViewById(R.id.btn_close_qr);

        idText.setText("ID: " + userShortId);
        
        String qrData = "vocabmaster://user/" + userShortId;
        try {
            Bitmap bitmap = generateQRCode(qrData);
            qrImg.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.VocabMaster_Dialog_Transparent)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        closeBtn.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private Bitmap generateQRCode(String data) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, 512, 512);
        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bitmap;
    }

    private void setupTabLayout() {
        binding.tabLayoutProfile.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    binding.layoutMyProcess.setVisibility(View.VISIBLE);
                    binding.layoutFeed.setVisibility(View.GONE);
                } else {
                    binding.layoutMyProcess.setVisibility(View.GONE);
                    binding.layoutFeed.setVisibility(View.VISIBLE);
                }
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void showEditProfilePopup(View v) {
        PopupMenu popup = new PopupMenu(requireContext(), v);
        popup.getMenuInflater().inflate(R.menu.profile_right_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_change_avatar) {
                showAvatarSelectionDialog();
                return true;
            } else if (id == R.id.menu_change_name) {
                showEditNameDialog();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showEditNameDialog() {
        android.widget.EditText editText = new android.widget.EditText(requireContext());
        editText.setText(binding.textDisplayName.getText());
        new AlertDialog.Builder(requireContext())
                .setTitle("Đổi tên hiển thị")
                .setView(editText)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String newName = editText.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        saveSetting("name", newName);
                        binding.textDisplayName.setText(newName);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProfile();
    }

    private void loadProfile() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        db.collection("users").document(uid).get().addOnSuccessListener(snapshot -> {
            if (!isAdded() || binding == null) return;
            
            String name = snapshot.getString("name");
            Long streak = snapshot.getLong("streak");
            Long xp = snapshot.getLong("xp");
            Long hearts = snapshot.getLong("hearts");
            String avatar = snapshot.getString("avatar");
            String language = snapshot.getString("language");
            Long friendsCount = snapshot.getLong("friendsCount");
            
            userShortId = snapshot.getString("shortId");
            if (userShortId == null) {
                generateAndSaveShortId(uid);
            } else {
                binding.textShortId.setText("ID: " + userShortId);
            }

            binding.textDisplayName.setText(name == null || name.isEmpty() ? "Người dùng" : name);
            
            binding.textXpValue.setText(String.valueOf(xp == null ? 0 : xp));
            binding.textStreakValue.setText(String.valueOf(streak == null ? 0 : streak));
            binding.textHeartsValue.setText(String.valueOf(hearts == null ? 5 : hearts));
            binding.textFriendsCount.setText((friendsCount != null ? friendsCount : 0) + " Bạn bè");
            
            updateAvatarUI(avatar);
            loadLatestCourse(uid, language);
        });
    }

    private void generateAndSaveShortId(String uid) {
        String candidateId = String.valueOf(100000 + random.nextInt(900000));
        db.collection("users").whereEqualTo("shortId", candidateId).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        db.collection("users").document(uid).update("shortId", candidateId)
                                .addOnSuccessListener(aVoid -> {
                                    userShortId = candidateId;
                                    if (isAdded() && binding != null) {
                                        binding.textShortId.setText("ID: " + userShortId);
                                    }
                                });
                    } else {
                        generateAndSaveShortId(uid);
                    }
                });
    }

    private void loadLatestCourse(String uid, String userLanguage) {
        db.collection("courses")
                .whereEqualTo("creatorId", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded() || binding == null) return;
                    if (querySnapshot.isEmpty()) {
                        updateCourseUI();
                        return;
                    }
                    DocumentSnapshot latestDoc = null;
                    Date latestDate = null;
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Date updatedAt = doc.getDate("updatedAt");
                        if (updatedAt == null) updatedAt = doc.getDate("createdAt");
                        if (updatedAt != null && (latestDate == null || updatedAt.after(latestDate))) {
                            latestDate = updatedAt;
                            latestDoc = doc;
                        }
                    }
                    if (latestDoc != null) {
                        String title = latestDoc.getString("title");
                        Long langId = latestDoc.getLong("targetLanguageId");
                        int flagRes = (langId != null) ? getFlagForLanguageId(langId.intValue()) : R.drawable.vietnam;
                        binding.imageCourseFlag.setImageResource(flagRes);
                        binding.textCourseName.setText(title);
                        binding.imageStatCourse.setImageResource(flagRes);
                        binding.textStatsCourseTitle.setText(title);
                    }
                });
    }

    private int getFlagForLanguageId(int langId) {
        switch (langId) {
            case 1: return R.drawable.eng;
            case 2: return R.drawable.japan;
            case 4: return R.drawable.china;
            case 5: return R.drawable.russia;
            default: return R.drawable.vietnam;
        }
    }

    private void updateAvatarUI(String avatarValue) {
        int resId = R.drawable.bear;
        if (avatarValue != null) {
            for (int i = 0; i < avatarValues.length; i++) {
                if (avatarValues[i].equals(avatarValue)) {
                    resId = avatarResIds[i];
                    break;
                }
            }
        }
        binding.imageAvatar.setImageResource(resId);
    }

    private void updateCourseUI() {
        if (binding == null) return;
        binding.imageCourseFlag.setImageResource(R.drawable.vietnam);
        binding.textCourseName.setText("Chưa có khóa học");
        binding.imageStatCourse.setImageResource(R.drawable.vietnam);
        binding.textStatsCourseTitle.setText("Chưa có khóa học");
    }

    private void showAvatarSelectionDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_avatar_selection, null);
        GridView gridView = dialogView.findViewById(R.id.grid_avatars);
        AvatarAdapter adapter = new AvatarAdapter(getLayoutInflater(), avatarResIds);
        gridView.setAdapter(adapter);
        
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(dialogView).create();
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            saveSetting("avatar", avatarValues[position]);
            updateAvatarUI(avatarValues[position]);
            dialog.dismiss();
            Toast.makeText(getContext(), "Đã cập nhật ảnh đại diện", Toast.LENGTH_SHORT).show();
        });
        dialog.show();
    }

    private void saveSetting(String key, Object value) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            db.collection("users").document(uid).update(key, value);
        }
    }
}
