package com.example.vocabmaster.ui.social;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.vocabmaster.databinding.FragmentSocialBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class SocialFragment extends Fragment {
    private FragmentSocialBinding binding;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSocialBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.btnFollowUser.setOnClickListener(v -> followUser());
        binding.btnReportContent.setOnClickListener(v -> reportContent());
        binding.btnLoadLeaderboard.setOnClickListener(v -> loadLeaderboard());
    }

    private void followUser() {
        String targetEmail = binding.editFriendEmail.getText() == null ? "" : binding.editFriendEmail.getText().toString().trim();
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || targetEmail.isEmpty()) {
            UiFeedback.showSnack(binding.getRoot(), "Enter a valid email to follow");
            return;
        }
        db.collection("users").whereEqualTo("email", targetEmail).limit(1).get().addOnSuccessListener(qs -> {
            if (qs.isEmpty()) {
                UiFeedback.showSnack(binding.getRoot(), "No user found with this email");
                return;
            }
            String friendId = qs.getDocuments().get(0).getId();
            db.collection("users").document(uid).collection("following").document(friendId)
                    .set(new java.util.HashMap<>()).addOnSuccessListener(unused ->
                            UiFeedback.showSnack(binding.getRoot(), "Now following " + targetEmail));
        });
    }

    private void reportContent() {
        String content = binding.editReportContent.getText() == null ? "" : binding.editReportContent.getText().toString().trim();
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || content.isEmpty()) {
            UiFeedback.showSnack(binding.getRoot(), "Please describe the issue before submitting");
            return;
        }
        java.util.Map<String, Object> report = new java.util.HashMap<>();
        report.put("reporterId", uid);
        report.put("content", content);
        report.put("createdAt", System.currentTimeMillis());
        db.collection("reports").add(report).addOnSuccessListener(unused ->
                UiFeedback.showSnack(binding.getRoot(), "Report submitted successfully"));
    }

    private void loadLeaderboard() {
        binding.progressLeaderboard.setVisibility(View.VISIBLE);
        db.collection("users").orderBy("xp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(qs -> {
                    binding.progressLeaderboard.setVisibility(View.GONE);
                    StringBuilder sb = new StringBuilder();
                    int rank = 1;
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : qs) {
                        String name = doc.getString("name");
                        Long xp = doc.getLong("xp");
                        sb.append(rank).append(". ").append(name == null ? "User" : name)
                                .append(" - ").append(xp == null ? 0 : xp).append(" XP\n");
                        rank++;
                    }
                    boolean isEmpty = sb.toString().isEmpty();
                    binding.textLeaderboardEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                    binding.textLeaderboard.setText(isEmpty ? "" : sb.toString());
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}