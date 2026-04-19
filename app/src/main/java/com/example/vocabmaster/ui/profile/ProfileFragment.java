package com.example.vocabmaster.ui.profile;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.vocabmaster.MainActivity;
import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Post;
import com.example.vocabmaster.databinding.FragmentProfileBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ProfileFragment extends Fragment {
    private static final String TAG = "ProfileFragment";
    private FragmentProfileBinding binding;
    private FirebaseFirestore db;
    private PostAdapter postAdapter;

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
        
        setupFeedRecyclerView();
        setupTabLayout();

        binding.btnMenu.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openDrawer();
            }
        });
        
        binding.btnSettings.setOnClickListener(v -> 
                NavHostFragment.findNavController(this).navigate(R.id.action_profile_to_settings));
        
        binding.cardAvatar.setOnClickListener(v -> showAvatarSelectionDialog());
        binding.btnQrCode.setOnClickListener(v -> showQRCodeDialog());
        binding.btnCopyId.setOnClickListener(v -> copyIdToClipboard());
        
        binding.btnCreatePost.setOnClickListener(v -> {
            BottomNavigationView navView = requireActivity().findViewById(R.id.nav_view);
            if (navView != null) navView.setSelectedItemId(R.id.navigation_library);
        });
    }

    private void setupFeedRecyclerView() {
        postAdapter = new PostAdapter();
        binding.recyclerFeed.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerFeed.setAdapter(postAdapter);
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
                    loadAllPosts(); // Đổi từ loadUserPosts sang loadAllPosts
                }
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void loadAllPosts() {
        // Lấy tất cả bài đăng để mọi người đều thấy nhau
        db.collection("posts")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (binding == null) return;
                    List<Post> posts = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Post post = doc.toObject(Post.class);
                        if (post != null) {
                            post.setId(doc.getId());
                            posts.add(post);
                        }
                    }
                    // Sắp xếp thủ công: Mới nhất lên đầu
                    posts.sort((p1, p2) -> {
                        if (p1.getCreatedAt() == null || p2.getCreatedAt() == null) return 0;
                        return p2.getCreatedAt().compareTo(p1.getCreatedAt());
                    });
                    updateFeedUI(posts);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Lỗi tải bản tin: " + e.getMessage());
                    Toast.makeText(getContext(), "Không thể tải bản tin", Toast.LENGTH_SHORT).show();
                });
    }

    private void updateFeedUI(List<Post> posts) {
        if (posts.isEmpty()) {
            binding.layoutEmptyFeed.setVisibility(View.VISIBLE);
            binding.recyclerFeed.setVisibility(View.GONE);
        } else {
            binding.layoutEmptyFeed.setVisibility(View.GONE);
            binding.recyclerFeed.setVisibility(View.VISIBLE);
            postAdapter.submitList(posts);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProfile();
        if (binding.tabLayoutProfile.getSelectedTabPosition() == 1) {
            loadAllPosts();
        }
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
            Long friendsCount = snapshot.getLong("friendsCount");
            
            userShortId = snapshot.getString("shortId");
            if (userShortId != null) {
                binding.textShortId.setText("ID: " + userShortId);
            }

            binding.textDisplayName.setText(name == null || name.isEmpty() ? "Người dùng" : name);
            binding.textXpValue.setText(String.valueOf(xp == null ? 0 : xp));
            binding.textStreakValue.setText(String.valueOf(streak == null ? 0 : streak));
            binding.textHeartsValue.setText(String.valueOf(hearts == null ? 5 : hearts));
            binding.textFriendsCount.setText((friendsCount != null ? friendsCount : 0) + " Bạn bè");
            
            updateAvatarUI(avatar);
        });
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
        } catch (WriterException e) { e.printStackTrace(); }
        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.VocabMaster_Dialog_Transparent)
                .setView(dialogView).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
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

    private void copyIdToClipboard() {
        if (userShortId == null || userShortId.equals("N/A")) return;
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("User ID", userShortId);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(requireContext(), "Đã sao chép ID: " + userShortId, Toast.LENGTH_SHORT).show();
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
        if (uid != null) db.collection("users").document(uid).update(key, value);
    }
}
