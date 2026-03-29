package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.Lesson;
import com.example.vocabmaster.data.model.Unit;
import com.example.vocabmaster.databinding.ActivityCreateCourseFlowBinding;
import com.example.vocabmaster.ui.library.CourseDetailActivity;
import com.example.vocabmaster.ui.library.LibraryViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class CreateCourseFlowActivity extends AppCompatActivity {
    private static final String TAG = "CreateCourseFlow";
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
        if (userId == null) return;

        // Hiển thị loading
        binding.btnNextFlow.setEnabled(false);
        binding.btnNextFlow.setText("Đang khởi tạo...");

        Course course = new Course();
        course.setTitle(selectedLanguage + " - " + selectedTheme);
        course.setDescription("Lộ trình học tập cá nhân hóa " + selectedLanguage);
        course.setTheme(selectedTheme);
        course.setCreatorId(userId);
        course.setPublic(false);
        course.setCreatedAt(new Date());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("courses").add(course).addOnSuccessListener(docRef -> {
            String courseId = docRef.getId();
            fetchVocabAndGenerateDuolingoPath(db, courseId, selectedTheme, course);
        }).addOnFailureListener(e -> {
            binding.btnNextFlow.setEnabled(true);
            binding.btnNextFlow.setText("Hoàn tất");
            Toast.makeText(this, "Lỗi khi tạo khóa học", Toast.LENGTH_SHORT).show();
        });
    }

    private void fetchVocabAndGenerateDuolingoPath(FirebaseFirestore db, String courseId, String theme, Course course) {
        String topicKey = mapThemeToTopic(theme);
        
        // Lấy từ vựng theo chủ đề hoặc ngẫu nhiên
        db.collection("vocabularies")
                .limit(100)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> allWords = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String word = doc.getString("word");
                        if (word != null) allWords.add(word);
                    }
                    
                    if (allWords.isEmpty()) {
                        allWords.add("Hello"); allWords.add("Goodbye"); allWords.add("Thank you");
                        allWords.add("Apple"); allWords.add("Bread"); allWords.add("Water");
                        allWords.add("Book"); allWords.add("Pen"); allWords.add("School");
                        allWords.add("Teacher");
                    }

                    generateDuolingoUnits(db, courseId, theme, allWords, course);
                });
    }

    private void generateDuolingoUnits(FirebaseFirestore db, String courseId, String theme, List<String> words, Course course) {
        WriteBatch batch = db.batch();
        Collections.shuffle(words);

        String[] unitThemes = {
            "Làm quen & Chào hỏi",
            "Giao tiếp Cơ bản",
            "Mở rộng vốn từ " + theme,
            "Tình huống thực tế",
            "Ôn tập tổng hợp"
        };

        for (int i = 0; i < unitThemes.length; i++) {
            String unitId = db.collection("units").document().getId();
            Unit unit = new Unit("Chương " + (i + 1) + ": " + unitThemes[i], i + 1, (i == 0));
            unit.setCourseId(courseId);
            unit.setUnitId(unitId);
            batch.set(db.collection("units").document(unitId), unit);

            createDuolingoLessons(db, batch, unitId, words, i);
        }

        batch.commit().addOnSuccessListener(aVoid -> {
            Log.d(TAG, "Duolingo path created!");
            
            // Chuyển màn hình SAU KHI đã lưu thành công vào Firestore
            Intent intent = new Intent(this, CourseDetailActivity.class);
            intent.putExtra("course_id", courseId);
            intent.putExtra("course_title", course.getTitle());
            intent.putExtra("course_theme", course.getTheme());
            startActivity(intent);
            finish();
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error committing batch", e);
            binding.btnNextFlow.setEnabled(true);
            binding.btnNextFlow.setText("Hoàn tất");
        });
    }

    private void createDuolingoLessons(FirebaseFirestore db, WriteBatch batch, String unitId, List<String> allWords, int unitIndex) {
        String[] lessonTitles = {"Từ vựng 1", "Luyện nghe", "Từ vựng 2", "Hội thoại", "Kiểm tra cuối chương"};
        String[] types = {"vocabulary", "listening", "vocabulary", "speaking", "quiz"};
        
        // Lấy 10 từ vựng cho mỗi Unit
        int wordsPerUnit = 10;
        int startIdx = (unitIndex * wordsPerUnit) % allWords.size();
        int endIdx = Math.min(startIdx + wordsPerUnit, allWords.size());
        
        List<String> unitWords;
        if (startIdx < endIdx) {
            unitWords = new ArrayList<>(allWords.subList(startIdx, endIdx));
        } else {
            unitWords = new ArrayList<>(allWords.subList(0, Math.min(wordsPerUnit, allWords.size())));
        }

        for (int j = 0; j < lessonTitles.length; j++) {
            Lesson lesson = new Lesson(lessonTitles[j], types[j], 10, 15);
            lesson.setUnitId(unitId);
            lesson.setVocabWords(unitWords);
            lesson.setOrderNum(j + 1);
            
            String lessonId = db.collection("lessons").document().getId();
            lesson.setLessonId(lessonId);
            batch.set(db.collection("lessons").document(lessonId), lesson);
        }
    }

    private String mapThemeToTopic(String theme) {
        if (theme == null) return "daily_life";
        switch (theme) {
            case "Nghề nghiệp": return "career";
            case "Trường học": return "school";
            case "Du lịch": return "travel";
            case "Thức ăn": return "food";
            case "Văn hóa": return "culture";
            default: return "daily_life";
        }
    }
}
