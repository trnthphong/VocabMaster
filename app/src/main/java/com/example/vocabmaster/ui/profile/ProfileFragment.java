package com.example.vocabmaster.ui.profile;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
import com.example.vocabmaster.data.model.Comment;
import com.example.vocabmaster.data.model.Post;
import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.data.repository.CourseRepository;
import com.example.vocabmaster.data.repository.SocialRepository;
import com.example.vocabmaster.databinding.DialogCommentsBinding;
import com.example.vocabmaster.databinding.FragmentProfileBinding;
import com.example.vocabmaster.ui.library.CourseDetailActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.ArrayList;
import java.util.List;

public class ProfileFragment extends Fragment {
    private static final String TAG = "ProfileFragment";
    private FragmentProfileBinding binding;
    private FirebaseFirestore db;
    private PostAdapter postAdapter;
    private CourseRepository courseRepository;
    private SocialRepository socialRepository;
    private User currentUser;

    private final String[] avatarValues = {"bear", "cat", "dog", "bird", "snake", "tiger", "rabbit"};
    private final int[] avatarResIds = {
            R.drawable.bear, R.drawable.cat, R.drawable.dog,
            R.drawable.bird, R.drawable.snake, R.drawable.tiger,
            R.drawable.rabbit
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();
        courseRepository = new CourseRepository(requireActivity().getApplication());
        socialRepository = new SocialRepository();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        loadCurrentUser();
        setupFeedRecyclerView();
        setupTabLayout();

        binding.btnMenu.setOnClickListener(v -> { if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openDrawer(); });
        binding.btnSettings.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.action_profile_to_settings));
        binding.cardAvatar.setOnClickListener(v -> showAvatarSelectionDialog());
        binding.btnQrCode.setOnClickListener(v -> showQRCodeDialog());
        binding.btnCopyId.setOnClickListener(v -> copyIdToClipboard());
        binding.btnCreatePost.setOnClickListener(v -> {
            BottomNavigationView navView = requireActivity().findViewById(R.id.nav_view);
            if (navView != null) navView.setSelectedItemId(R.id.navigation_library);
        });
    }

    private void loadCurrentUser() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener(doc -> currentUser = doc.toObject(User.class));
        }
    }

    private void setupFeedRecyclerView() {
        postAdapter = new PostAdapter();
        binding.recyclerFeed.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerFeed.setAdapter(postAdapter);

        postAdapter.setOnPostActionListener(new PostAdapter.OnPostActionListener() {
            @Override
            public void onLikeClick(Post post) {
                String uid = FirebaseAuth.getInstance().getUid();
                boolean isLiked = post.getLikes().contains(uid);
                socialRepository.toggleLike(post.getId(), !isLiked).addOnSuccessListener(v -> loadAllPosts());
            }

            @Override
            public void onCommentClick(Post post) {
                showCommentsDialog(post);
            }

            @Override
            public void onCopyCourseClick(Post post) {
                courseRepository.copyCourseById(post.getCourseId()).addOnSuccessListener(v -> 
                    Toast.makeText(getContext(), "Đã sao chép học phần!", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showCommentsDialog(Post post) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        DialogCommentsBinding dialogBinding = DialogCommentsBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());

        CommentAdapter commentAdapter = new CommentAdapter();
        dialogBinding.recyclerComments.setLayoutManager(new LinearLayoutManager(getContext()));
        dialogBinding.recyclerComments.setAdapter(commentAdapter);

        // Lắng nghe bình luận thời gian thực
        socialRepository.getCommentsQuery(post.getId()).addSnapshotListener((value, error) -> {
            if (value != null) {
                List<Comment> comments = new ArrayList<>();
                for (DocumentSnapshot doc : value.getDocuments()) {
                    Comment c = doc.toObject(Comment.class);
                    if (c != null) {
                        c.setId(doc.getId());
                        comments.add(c);
                    }
                }
                commentAdapter.submitList(comments);
                dialogBinding.recyclerComments.scrollToPosition(comments.size() - 1);
            }
        });

        dialogBinding.btnSendComment.setOnClickListener(v -> {
            String content = dialogBinding.editComment.getText().toString().trim();
            if (!TextUtils.isEmpty(content)) {
                String name = currentUser != null ? currentUser.getName() : "User";
                String avatar = currentUser != null ? currentUser.getAvatar() : "bear";
                socialRepository.addComment(post.getId(), content, name, avatar).addOnSuccessListener(unused -> {
                    dialogBinding.editComment.setText("");
                    loadAllPosts(); // Cập nhật số lượng comment trên bài đăng
                });
            }
        });

        dialog.show();
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
                    loadAllPosts();
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void loadAllPosts() {
        db.collection("posts").get().addOnSuccessListener(querySnapshot -> {
            if (binding == null) return;
            List<Post> posts = new ArrayList<>();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Post post = doc.toObject(Post.class);
                if (post != null) {
                    post.setId(doc.getId());
                    posts.add(post);
                }
            }
            posts.sort((p1, p2) -> {
                if (p1.getCreatedAt() == null || p2.getCreatedAt() == null) return 0;
                return p2.getCreatedAt().compareTo(p1.getCreatedAt());
            });
            updateFeedUI(posts);
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
        if (binding.tabLayoutProfile.getSelectedTabPosition() == 1) loadAllPosts();
    }

    private void loadProfile() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener(snapshot -> {
                if (binding == null) return;
                String name = snapshot.getString("name");
                Long streak = snapshot.getLong("streak"), xp = snapshot.getLong("xp"), hearts = snapshot.getLong("hearts"), friends = snapshot.getLong("friendsCount");
                String avatar = snapshot.getString("avatar"), shortId = snapshot.getString("shortId");
                if (shortId != null) binding.textShortId.setText("ID: " + shortId);
                binding.textDisplayName.setText(name == null || name.isEmpty() ? "Người dùng" : name);
                binding.textXpValue.setText(String.valueOf(xp == null ? 0 : xp));
                binding.textStreakValue.setText(String.valueOf(streak == null ? 0 : streak));
                binding.textHeartsValue.setText(String.valueOf(hearts == null ? 5 : hearts));
                binding.textFriendsCount.setText((friends != null ? friends : 0) + " Bạn bè");
                updateAvatarUI(avatar);
            });
        }
    }

    private void updateAvatarUI(String avatarValue) {
        int resId = R.drawable.bear;
        if (avatarValue != null) {
            for (int i = 0; i < avatarValues.length; i++) {
                if (avatarValues[i].equals(avatarValue)) { resId = avatarResIds[i]; break; }
            }
        }
        binding.imageAvatar.setImageResource(resId);
    }

    private void showQRCodeDialog() { /* QR Logic ... */ }
    private void copyIdToClipboard() { /* Copy Logic ... */ }
    private void showAvatarSelectionDialog() { /* Avatar Logic ... */ }
    private Bitmap generateQRCode(String data) throws WriterException { return null; }
}
