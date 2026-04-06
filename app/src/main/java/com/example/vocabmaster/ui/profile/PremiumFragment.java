package com.example.vocabmaster.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.vocabmaster.data.api.MoMoApiService;
import com.example.vocabmaster.data.api.RetrofitClient;
import com.example.vocabmaster.data.model.MoMoRequest;
import com.example.vocabmaster.data.model.MoMoResponse;
import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.databinding.FragmentPremiumBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PremiumFragment extends Fragment {
    private static final String TAG = "PremiumFragment";
    private FragmentPremiumBinding binding;
    private FirebaseFirestore db;
    private User currentUser;

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
        
        loadUserData();
        
        binding.btnSubscribeNow.setOnClickListener(v -> onUpgrade());
    }

    private void loadUserData() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        db.collection("users").document(uid).addSnapshotListener((snapshot, e) -> {
            if (snapshot != null && snapshot.exists() && isAdded()) {
                currentUser = snapshot.toObject(User.class);
                updateUI();
            }
        });
    }

    private void updateUI() {
        if (currentUser == null) return;

        if (currentUser.isActivePremium()) {
            binding.btnSubscribeNow.setText("Đã là thành viên Pro");
            binding.btnSubscribeNow.setEnabled(false);
            binding.textPremiumStatus.setText("Bạn đang sử dụng gói Premium!");
            binding.textPremiumStatus.setVisibility(View.VISIBLE);
        } else {
            binding.btnSubscribeNow.setText("Nâng cấp ngay");
            binding.btnSubscribeNow.setEnabled(true);
            binding.textPremiumStatus.setVisibility(View.GONE);
        }
    }

    private void onUpgrade() {
        if (currentUser == null) return;
        createMoMoPayment();
    }

    private void createMoMoPayment() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        binding.btnSubscribeNow.setEnabled(false);
        binding.btnSubscribeNow.setText("Đang khởi tạo MoMo...");

        MoMoApiService apiService = RetrofitClient.getClient().create(MoMoApiService.class);
        MoMoRequest request = new MoMoRequest(uid, 50000, "Nâng cấp Premium VocabMaster");

        apiService.createPayment(request).enqueue(new Callback<MoMoResponse>() {
            @Override
            public void onResponse(@NonNull Call<MoMoResponse> call, @NonNull Response<MoMoResponse> response) {
                if (isAdded()) {
                    binding.btnSubscribeNow.setEnabled(true);
                    binding.btnSubscribeNow.setText("Nâng cấp ngay");

                    if (response.isSuccessful() && response.body() != null) {
                        String payUrl = response.body().getPayUrl();
                        if (payUrl != null && !payUrl.isEmpty()) {
                            openWebPayment(payUrl);
                        } else {
                            Toast.makeText(requireContext(), "Không nhận được link thanh toán", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(requireContext(), "Lỗi MoMo: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<MoMoResponse> call, @NonNull Throwable t) {
                if (isAdded()) {
                    binding.btnSubscribeNow.setEnabled(true);
                    binding.btnSubscribeNow.setText("Nâng cấp ngay");
                    Toast.makeText(requireContext(), "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void openWebPayment(String url) {
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Không thể mở MoMo", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
