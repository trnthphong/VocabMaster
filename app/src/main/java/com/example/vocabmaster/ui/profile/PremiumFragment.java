package com.example.vocabmaster.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.vocabmaster.databinding.FragmentPremiumBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class PremiumFragment extends Fragment {
    private FragmentPremiumBinding binding;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPremiumBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        binding.btnSubscribeNow.setOnClickListener(v -> subscribePremium());
    }

    private void subscribePremium() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        UiFeedback.showConfirmDialog(requireContext(), "Xác nhận đăng ký", "Bạn có muốn nâng cấp lên Premium với giá 99k/tháng?", () -> {
            db.collection("users").document(uid).update("premium", true).addOnSuccessListener(unused -> {
                Map<String, Object> sub = new HashMap<>();
                sub.put("userId", uid);
                sub.put("plan", "monthly");
                sub.put("status", "active");
                sub.put("createdAt", System.currentTimeMillis());
                db.collection("subscriptions").add(sub);
                
                UiFeedback.showSnack(binding.getRoot(), "Chúc mừng! Bạn đã là thành viên Premium");
                requireActivity().onBackPressed();
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
