package com.example.vocabmaster.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.vocabmaster.R;
import com.example.vocabmaster.databinding.FragmentPremiumSuccessBinding;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import nl.dionsegijn.konfetti.core.Party;
import nl.dionsegijn.konfetti.core.PartyFactory;
import nl.dionsegijn.konfetti.core.emitter.Emitter;
import nl.dionsegijn.konfetti.core.emitter.EmitterConfig;
import nl.dionsegijn.konfetti.core.models.Shape;
import nl.dionsegijn.konfetti.core.models.Size;

public class PremiumSuccessFragment extends Fragment {
    private FragmentPremiumSuccessBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPremiumSuccessBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String planName = getArguments() != null ? getArguments().getString("plan_name", "Premium") : "Premium";
        int days = getArguments() != null ? getArguments().getInt("days", 30) : 30;

        binding.textPlanName.setText(planName);
        binding.textSuccessMessage.setText("Bạn đã đăng ký thành công " + planName + "\nHành trình chinh phục từ vựng bắt đầu ngay bây giờ!");

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, days);
        SimpleDateFormat sdf = new SimpleDateFormat("dd 'thg' M, yyyy", new Locale("vi", "VN"));
        binding.textExpiryDate.setText("Hết hạn vào: " + sdf.format(cal.getTime()));

        binding.btnBackHome.setOnClickListener(v -> {
            NavHostFragment.findNavController(this).navigate(R.id.navigation_home);
        });

        showConfetti();
    }

    private void showConfetti() {
        EmitterConfig emitterConfig = new Emitter(300L, TimeUnit.MILLISECONDS).max(300);
        Party party = new PartyFactory(emitterConfig)
                .shapes(Shape.Circle.INSTANCE, Shape.Square.INSTANCE)
                .spread(360)
                .position(0.5, 0.3)
                .sizes(new Size(8, 50, 10))
                .colors(java.util.Arrays.asList(0xfce18a, 0xff726d, 0xb48def, 0xf4306d))
                .build();
        binding.konfettiView.start(party);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
