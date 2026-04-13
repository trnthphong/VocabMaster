package com.example.vocabmaster.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.api.MoMoApiService;
import com.example.vocabmaster.data.api.RetrofitClient;
import com.example.vocabmaster.data.model.MoMoRequest;
import com.example.vocabmaster.data.model.MoMoResponse;
import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.databinding.FragmentPremiumBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PremiumFragment extends Fragment {
    private FragmentPremiumBinding binding;
    private FirebaseFirestore db;
    private User currentUser;
    private boolean isAnnualPlan = true;

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
        
        setupUI();
        loadUserData();
        updatePlanSelection();
    }

    private void setupUI() {
        binding.btnClose.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        
        binding.cardAnnual.setOnClickListener(v -> {
            isAnnualPlan = true;
            updatePlanSelection();
        });
        
        binding.cardMonthly.setOnClickListener(v -> {
            isAnnualPlan = false;
            updatePlanSelection();
        });
        
        binding.btnSubscribe.setOnClickListener(v -> onUpgrade());
    }

    private void updatePlanSelection() {
        binding.radioAnnual.setChecked(isAnnualPlan);
        binding.radioMonthly.setChecked(!isAnnualPlan);

        int activeColor = getResources().getColor(R.color.brand_primary);
        int inactiveColor = getResources().getColor(R.color.card_border);

        binding.cardAnnual.setStrokeColor(isAnnualPlan ? activeColor : inactiveColor);
        binding.cardAnnual.setStrokeWidth(isAnnualPlan ? 6 : 2);

        binding.cardMonthly.setStrokeColor(!isAnnualPlan ? activeColor : inactiveColor);
        binding.cardMonthly.setStrokeWidth(!isAnnualPlan ? 6 : 2);

        updateHowItWorksUI();
    }

    private void updateHowItWorksUI() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd 'thg' M, yyyy", new Locale("vi", "VN"));
        
        if (isAnnualPlan) {
            cal.add(Calendar.YEAR, 1);
            binding.textHowItWorksStep1Desc.setText("Bạn được tính phí cho một năm dịch vụ.");
            binding.textHowItWorksStep2Desc.setText("Gói đăng ký của bạn được gia hạn thêm một năm trừ khi bạn hủy trước ngày này.");
        } else {
            cal.add(Calendar.MONTH, 1);
            binding.textHowItWorksStep1Desc.setText("Bạn được tính phí cho một tháng dịch vụ.");
            binding.textHowItWorksStep2Desc.setText("Gói đăng ký của bạn được gia hạn thêm một tháng trừ khi bạn hủy trước ngày này.");
        }
        
        binding.textRenewalDate.setText(sdf.format(cal.getTime()) + ": Gia hạn");
    }

    private void loadUserData() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        db.collection("users").document(uid).addSnapshotListener((snapshot, e) -> {
            if (snapshot != null && snapshot.exists() && isAdded()) {
                currentUser = snapshot.toObject(User.class);
                if (currentUser != null && currentUser.isActivePremium()) {
                    binding.btnSubscribe.setText("Đã là thành viên Pro");
                    binding.btnSubscribe.setEnabled(false);
                }
            }
        });
    }

    private void onUpgrade() {
        if (currentUser == null) return;
        
        long amount = isAnnualPlan ? 1299000 : 299000;
        String planName = isAnnualPlan ? "Gói năm Premium" : "Gói tháng Premium";
        createMoMoPayment(amount, planName);
    }

    private void createMoMoPayment(long amount, String description) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        binding.btnSubscribe.setEnabled(false);
        binding.btnSubscribe.setText("Đang khởi tạo...");

        MoMoApiService apiService = RetrofitClient.getClient().create(MoMoApiService.class);
        MoMoRequest request = new MoMoRequest(uid, (int) amount, description + " VocabMaster");

        apiService.createPayment(request).enqueue(new Callback<MoMoResponse>() {
            @Override
            public void onResponse(@NonNull Call<MoMoResponse> call, @NonNull Response<MoMoResponse> response) {
                if (isAdded()) {
                    binding.btnSubscribe.setEnabled(true);
                    binding.btnSubscribe.setText("Mua dịch vụ");

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
                    binding.btnSubscribe.setEnabled(true);
                    binding.btnSubscribe.setText("Mua dịch vụ");
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
