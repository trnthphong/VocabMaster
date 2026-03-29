package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.databinding.ActivityCreateCourseFlowBinding;
import com.example.vocabmaster.ui.library.CourseDetailActivity;
import com.example.vocabmaster.ui.library.LibraryViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CreateCourseFlowActivity extends AppCompatActivity {
    private ActivityCreateCourseFlowBinding binding;
    private List<Fragment> fragments = new ArrayList<>();
    private LibraryViewModel viewModel;
    
    private String selectedLanguage = "";
    private String selectedTheme = "";
    private int selectedTime = 10;
    private boolean isPremiumUser = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateCourseFlowBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LibraryViewModel.class);

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

        binding.viewPagerFlow.setUserInputEnabled(false);
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
                if (isPremiumUser) {
                    createCourseAndFinish();
                    return;
                }
            } else if (current == 3) {
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
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) {
            Toast.makeText(this, "Vui lòng đăng nhập lại", Toast.LENGTH_SHORT).show();
            return;
        }

        Course course = new Course();
        course.setTitle(selectedLanguage + " Course");
        course.setDescription("Learning " + selectedLanguage + " with theme: " + selectedTheme);
        course.setTheme(selectedTheme);
        course.setCreatorId(userId);
        course.setPublic(false);
        
        // Map data from schema
        course.setTargetLanguageId(mapLanguageToId(selectedLanguage));
        course.setSourceLanguageId(1); // Mặc định Tiếng Việt
        course.setDailyTimeMinutes(selectedTime);
        course.setProficiencyLevel("beginner");
        course.setLearningGoal("Personal Growth");
        course.setStatus("active");
        course.setCreatedAt(new Date());
        course.setUpdatedAt(new Date());
        course.setStartDate(new Date());
        course.setFlashcardCount(0);
        course.setProgressPercentage(0.0);
        course.setStreakDays(0);

        // Đẩy lên Firestore và đồng bộ Local trong một quy trình
        viewModel.addCourseAndSync(course);

        // Cập nhật ngôn ngữ đang học cho User để hiển thị ở Profile
        FirebaseFirestore.getInstance().collection("users").document(userId)
                .update("language", selectedLanguage);

        Toast.makeText(this, "Đang tạo khóa học...", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, CourseDetailActivity.class);
        intent.putExtra("course_title", course.getTitle());
        intent.putExtra("course_theme", course.getTheme());
        startActivity(intent);
        finish();
    }

    private int mapLanguageToId(String language) {
        if (language == null) return 0;
        switch (language.toLowerCase()) {
            case "english": case "tiếng anh": return 1;
            case "japanese": case "tiếng nhật": return 2;
            case "korean": case "tiếng hàn": return 3;
            case "chinese": case "tiếng trung": return 4;
            case "russia": case "tiếng nga": return 5;
            default: return 0;
        }
    }
}
