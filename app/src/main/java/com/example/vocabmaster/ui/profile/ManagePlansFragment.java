package com.example.vocabmaster.ui.profile;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.vocabmaster.R;
import com.example.vocabmaster.databinding.FragmentManagePlansBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ManagePlansFragment extends Fragment {
    private FragmentManagePlansBinding binding;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentManagePlansBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        loadUserData();
    }

    private void loadUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        binding.textUserUid.setText("UID: " + user.getUid());

        db.collection("users").document(user.getUid()).addSnapshotListener((snapshot, e) -> {
            if (snapshot != null && snapshot.exists() && isAdded()) {
                String name = snapshot.getString("name");
                binding.textUserName.setText("Tên: " + (name != null ? name : "Người dùng"));

                // Kiểm tra cả 2 trường để đảm bảo không bị sót
                Boolean isPremium = snapshot.getBoolean("premium");
                if (isPremium == null) isPremium = snapshot.getBoolean("isPremium");
                
                binding.containerActivePlans.removeAllViews();
                
                if (Boolean.TRUE.equals(isPremium)) {
                    binding.textNoPlans.setVisibility(View.GONE);
                    
                    String planType = snapshot.getString("premiumPlanType");
                    if (planType == null) planType = "Premium";
                    
                    int days = snapshot.getLong("premiumDays") != null ? snapshot.getLong("premiumDays").intValue() : 365;
                    long price = snapshot.getLong("premiumPrice") != null ? snapshot.getLong("premiumPrice") : 0;
                    Date regDate = snapshot.getDate("premiumRegDate");
                    Date expiryDate = snapshot.getDate("premiumUntil");
                    String method = snapshot.getString("paymentMethod");
                    String status = snapshot.getString("premiumStatus");
                    
                    if (status == null) {
                        status = (expiryDate != null && expiryDate.before(new Date())) ? "EXPIRED" : "ACTIVE";
                    }
                    
                    addDetailedPlanCard(planType, days, price, regDate, expiryDate, method, status);
                } else {
                    binding.textNoPlans.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void addDetailedPlanCard(String type, int days, long price, Date regDate, Date expiry, String method, String status) {
        View cardView = getLayoutInflater().inflate(R.layout.item_active_plan, binding.containerActivePlans, false);
        
        TextView textTitle = cardView.findViewById(R.id.text_plan_title);
        TextView badgeStatus = cardView.findViewById(R.id.badge_status);
        TextView textFeatures = cardView.findViewById(R.id.text_plan_features);
        TextView textPrice = cardView.findViewById(R.id.text_plan_price);
        TextView textMethod = cardView.findViewById(R.id.text_payment_method);
        TextView textReg = cardView.findViewById(R.id.text_reg_date);
        TextView textExp = cardView.findViewById(R.id.text_plan_expiry);
        TextView textAuto = cardView.findViewById(R.id.text_auto_renewal);
        
        textTitle.setText("Gói " + type + " (" + days + " ngày)");
        
        int colorRes = R.color.light_green;
        String statusText = "Đang sử dụng";

        if ("EXPIRED".equalsIgnoreCase(status)) {
            statusText = "Hết hạn";
            colorRes = R.color.light_yellow;
        } else if ("CANCELLED".equalsIgnoreCase(status)) {
            statusText = "Đã hủy";
            colorRes = R.color.light_red;
        }

        badgeStatus.setText(statusText);
        badgeStatus.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(colorRes)));

        textFeatures.setText("• Toàn bộ tính năng và khóa học\n• Không quảng cáo\n• Hỗ trợ ưu tiên");

        NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));
        textPrice.setText(formatter.format(price) + " ₫");
        textMethod.setText(method != null ? method : "Ví MoMo");
        
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN"));
        textReg.setText(regDate != null ? sdf.format(regDate) : "--/--/----");
        textExp.setText(expiry != null ? sdf.format(expiry) : "--/--/----");
        
        textAuto.setText("Có");

        cardView.findViewById(R.id.btn_cancel_subscription).setOnClickListener(v -> 
            Toast.makeText(requireContext(), "Yêu cầu hủy gói đã được gửi", Toast.LENGTH_SHORT).show());
            
        cardView.findViewById(R.id.btn_cancel_renewal).setOnClickListener(v -> 
            Toast.makeText(requireContext(), "Đã tắt gia hạn tự động", Toast.LENGTH_SHORT).show());

        binding.containerActivePlans.addView(cardView);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
