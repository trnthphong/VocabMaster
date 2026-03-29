package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.vocabmaster.databinding.ActivityCreateCourseFlowBinding;
import com.example.vocabmaster.ui.common.MotionSystem;
import com.example.vocabmaster.ui.library.CourseDetailActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class CreateCourseFlowActivity extends AppCompatActivity {
    private ActivityCreateCourseFlowBinding binding;
    private List<Fragment> fragments = new ArrayList<>();
    private String selectedLanguage = "";
    private String selectedTheme = "";
    private int selectedTime = 10;
    private boolean isPremiumUser = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateCourseFlowBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        checkPremiumStatus();
        setupViewPager();
        setupListeners();
    }

    private void checkPremiumStatus() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                    .addOnSuccessListener(snapshot -> {
                        isPremiumUser = Boolean.TRUE.equals(snapshot.getBoolean("premium"));
                    });
        }
    }

    private void setupViewPager() {
        fragments.add(new Step1LanguageFragment());
        fragments.add(new Step2ThemeFragment());
        fragments.add(new Step3TimeFragment());
        fragments.add(new Step4PremiumFragment());

        binding.viewPagerFlow.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return fragments.get(position);
            }

            @Override
            public int getItemCount() {
                return fragments.size();
            }
        });

        binding.viewPagerFlow.setUserInputEnabled(false); // Không cho vuốt tay
        binding.progressFlow.setMax(fragments.size());
        binding.progressFlow.setProgress(1);
    }

    private void setupListeners() {
        binding.btnBackFlow.setOnClickListener(v -> {
            int current = binding.viewPagerFlow.getCurrentItem();
            if (current > 0) {
                binding.viewPagerFlow.setCurrentItem(current - 1);
                binding.progressFlow.setProgress(current);
            } else {
                finish();
            }
        });

        binding.btnNextFlow.setOnClickListener(v -> {
            int current = binding.viewPagerFlow.getCurrentItem();
            
            // Xử lý logic từng bước trước khi qua trang mới
            if (current == 0) {
                selectedLanguage = ((Step1LanguageFragment) fragments.get(0)).getSelectedLanguage();
                if (selectedLanguage.isEmpty()) {
                    Toast.makeText(this, "Vui lòng chọn ngôn ngữ", Toast.LENGTH_SHORT).show();
                    return;
                }
            } else if (current == 1) {
                selectedTheme = ((Step2ThemeFragment) fragments.get(1)).getSelectedTheme();
                if (selectedTheme.isEmpty()) {
                    Toast.makeText(this, "Vui lòng chọn chủ đề", Toast.LENGTH_SHORT).show();
                    return;
                }
            } else if (current == 2) {
                selectedTime = ((Step3TimeFragment) fragments.get(2)).getSelectedTime();
                // Nếu đã là Premium thì bỏ qua bước 3 (Premium offer) và tạo luôn
                if (isPremiumUser) {
                    createCourseAndFinish();
                    return;
                }
            } else if (current == 3) {
                // Bước Premium, nhấn "Tiếp tục" ở đây có thể coi là đồng ý hoặc bỏ qua tùy UI
                createCourseAndFinish();
                return;
            }

            if (current < fragments.size() - 1) {
                binding.viewPagerFlow.setCurrentItem(current + 1);
                binding.progressFlow.setProgress(current + 2);
                
                if (current + 1 == fragments.size() - 1) {
                    binding.btnNextFlow.setText("Hoàn tất");
                }
            }
        });
    }

    private void createCourseAndFinish() {
        // Giả lập tạo course thành công
        Intent intent = new Intent(this, CourseDetailActivity.class);
        intent.putExtra("course_title", selectedLanguage + " - " + selectedTheme);
        intent.putExtra("course_theme", selectedTheme);
        startActivity(intent);
        finish();
    }
}