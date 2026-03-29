package com.example.vocabmaster.ui.library;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.vocabmaster.R;
import com.example.vocabmaster.databinding.ActivityCourseDetailBinding;

import java.util.ArrayList;
import java.util.List;

public class CourseDetailActivity extends AppCompatActivity {

    private ActivityCourseDetailBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCourseDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String courseTitle = getIntent().getStringExtra("course_title");
        String courseTheme = getIntent().getStringExtra("course_theme");

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(courseTitle);
        }

        binding.collapsingToolbar.setTitle(courseTitle);
        binding.textCourseTheme.setText("Chủ đề: " + (courseTheme != null ? courseTheme : "Chưa chọn"));

        setupRoadmap();
    }

    private void setupRoadmap() {
        List<RoadmapStep> steps = new ArrayList<>();
        steps.add(new RoadmapStep("Level 1", "Kỹ năng Nghe", "Luyện tập nhận diện âm thanh và từ vựng cơ bản thông qua audio.", R.drawable.vocab)); // Replace with real icons if available
        steps.add(new RoadmapStep("Level 2", "Kỹ năng Viết", "Học cách viết đúng chính tả và cấu trúc câu đơn giản.", R.drawable.vocab));
        steps.add(new RoadmapStep("Level 3", "Kỹ năng Nói", "Luyện phát âm và phản xạ giao tiếp cơ bản.", R.drawable.vocab));
        steps.add(new RoadmapStep("Level 4", "Luyện Từ vựng", "Mở rộng vốn từ chuyên sâu theo chủ đề đã chọn.", R.drawable.vocab));
        steps.add(new RoadmapStep("Level 5", "Tổng kết & Kiểm tra", "Đánh giá năng lực tổng hợp sau khóa học.", R.drawable.vocab));

        RoadmapAdapter adapter = new RoadmapAdapter(steps);
        binding.recyclerRoadmap.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerRoadmap.setAdapter(adapter);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
