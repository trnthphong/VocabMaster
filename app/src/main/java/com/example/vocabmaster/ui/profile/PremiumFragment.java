package com.example.vocabmaster.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;
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
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PremiumFragment extends Fragment {
    private FragmentPremiumBinding binding;
    private FirebaseFirestore db;
    private User currentUser;

    private enum PlanType { STUDENT, VIP, VVIP }

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
    }

    private void setupUI() {
        binding.btnClose.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

        binding.btnRegisterStudent.setOnClickListener(v -> showPlanDurationDialog(PlanType.STUDENT));
        binding.btnRegisterVip.setOnClickListener(v -> showPlanDurationDialog(PlanType.VIP));
        binding.btnRegisterVvip.setOnClickListener(v -> showPlanDurationDialog(PlanType.VVIP));
    }

    private void loadUserData() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        db.collection("users").document(uid).addSnapshotListener((snapshot, e) -> {
            if (snapshot != null && snapshot.exists() && isAdded()) {
                currentUser = snapshot.toObject(User.class);
            }
        });
    }

    private void showPlanDurationDialog(PlanType planType) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.dialog_plan_selection, null);
        dialog.setContentView(view);

        TextView subtitle = view.findViewById(R.id.text_dialog_subtitle);
        TextView totalPriceText = view.findViewById(R.id.text_total_price);
        RadioGroup radioGroup = view.findViewById(R.id.radio_group_duration);
        MaterialButton btnConfirm = view.findViewById(R.id.btn_confirm_payment);

        String planName = "";
        switch (planType) {
            case STUDENT: planName = "Gói Student"; break;
            case VIP: planName = "Gói VIP"; break;
            case VVIP: planName = "Gói VVIP"; break;
        }
        subtitle.setText(planName);

        updateTotalPrice(totalPriceText, planType, 30);

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int days = 30;
            if (checkedId == R.id.radio_7_days) days = 7;
            else if (checkedId == R.id.radio_30_days) days = 30;
            else if (checkedId == R.id.radio_90_days) days = 90;
            else if (checkedId == R.id.radio_180_days) days = 180;
            else if (checkedId == R.id.radio_360_days) days = 360;
            updateTotalPrice(totalPriceText, planType, days);
        });

        String finalPlanName = planName;
        btnConfirm.setOnClickListener(v -> {
            int checkedId = radioGroup.getCheckedRadioButtonId();
            int days = 30;
            if (checkedId == R.id.radio_7_days) days = 7;
            else if (checkedId == R.id.radio_30_days) days = 30;
            else if (checkedId == R.id.radio_90_days) days = 90;
            else if (checkedId == R.id.radio_180_days) days = 180;
            else if (checkedId == R.id.radio_360_days) days = 360;

            long price = calculatePrice(planType, days);
            dialog.dismiss();
            startMoMoPayment(price, finalPlanName + " (" + days + " ngày)");
        });

        dialog.show();
    }

    private void updateTotalPrice(TextView textView, PlanType type, int days) {
        long price = calculatePrice(type, days);
        NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));
        textView.setText(formatter.format(price) + " ₫");
    }

    private long calculatePrice(PlanType type, int days) {
        int basePrice;
        switch (type) {
            case STUDENT: basePrice = 2500; break;
            case VIP: basePrice = 3500; break;
            case VVIP: basePrice = 4500; break;
            default: basePrice = 3500;
        }
        
        long total = (long) basePrice * days;
        if (days == 30) total = (long) (total * 0.9);
        else if (days == 90) total = (long) (total * 0.85);
        else if (days == 180) total = (long) (total * 0.8);
        else if (days == 360) total = (long) (total * 0.7);
        
        return (total / 1000) * 1000;
    }

    private void startMoMoPayment(long amount, String description) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        MoMoApiService apiService = RetrofitClient.getClient().create(MoMoApiService.class);
        MoMoRequest request = new MoMoRequest(uid, (int) amount, "Thanh toán " + description);

        apiService.createPayment(request).enqueue(new Callback<MoMoResponse>() {
            @Override
            public void onResponse(@NonNull Call<MoMoResponse> call, @NonNull Response<MoMoResponse> response) {
                if (isAdded() && response.isSuccessful() && response.body() != null) {
                    String payUrl = response.body().getPayUrl();
                    if (payUrl != null) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(payUrl)));
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<MoMoResponse> call, @NonNull Throwable t) {
                if (isAdded()) Toast.makeText(requireContext(), "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
