package com.example.vocabmaster.ui.library;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.vocabmaster.databinding.ActivityPersonalCardsBinding;

public class PersonalCardsActivity extends AppCompatActivity {
    private ActivityPersonalCardsBinding binding;
    private LibraryViewModel viewModel;
    private FlashcardListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPersonalCardsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LibraryViewModel.class);
        
        setupViewPager();
        
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        viewModel.getPersonalFlashcards().observe(this, flashcards -> {
            if (flashcards != null && !flashcards.isEmpty()) {
                adapter.submitList(flashcards);
                binding.textEmpty.setVisibility(View.GONE);
                binding.viewPagerFlashcards.setVisibility(View.VISIBLE);
            } else {
                binding.textEmpty.setVisibility(View.VISIBLE);
                binding.viewPagerFlashcards.setVisibility(View.GONE);
                Toast.makeText(this, "Không còn thẻ nào, quay lại Thư viện", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void setupViewPager() {
        adapter = new FlashcardListAdapter(flashcard -> viewModel.deleteFlashcard(flashcard));
        adapter.setViewPagerMode(true); // Kích hoạt chế độ match_parent cho ViewPager2
        binding.viewPagerFlashcards.setAdapter(adapter);
        
        binding.viewPagerFlashcards.setOffscreenPageLimit(3);
        binding.viewPagerFlashcards.setClipToPadding(false);
        binding.viewPagerFlashcards.setClipChildren(false);
        
        // Tạo khoảng trống giữa các thẻ (tùy chọn)
        float margin = 40 * getResources().getDisplayMetrics().density;
        binding.viewPagerFlashcards.setPageTransformer((page, position) -> {
            float absPos = Math.abs(position);
            page.setScaleY(0.85f + (1 - absPos) * 0.15f);
            page.setAlpha(0.5f + (1 - absPos) * 0.5f);
            page.setTranslationX(-position * margin);
        });
    }
}
