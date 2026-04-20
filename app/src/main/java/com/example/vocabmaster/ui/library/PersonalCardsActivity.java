package com.example.vocabmaster.ui.library;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Flashcard;
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
        adapter.setEditListener(flashcard -> showEditDialog(flashcard));
        adapter.setViewPagerMode(true);
        binding.viewPagerFlashcards.setAdapter(adapter);
        
        binding.viewPagerFlashcards.setOffscreenPageLimit(3);
        binding.viewPagerFlashcards.setClipToPadding(false);
        binding.viewPagerFlashcards.setClipChildren(false);
        
        float margin = 40 * getResources().getDisplayMetrics().density;
        binding.viewPagerFlashcards.setPageTransformer((page, position) -> {
            float absPos = Math.abs(position);
            page.setScaleY(0.85f + (1 - absPos) * 0.15f);
            page.setAlpha(0.5f + (1 - absPos) * 0.5f);
            page.setTranslationX(-position * margin);
        });
    }

    private void showEditDialog(Flashcard flashcard) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_flashcard, null);
        EditText etTerm = dialogView.findViewById(R.id.edit_term);
        EditText etDefinition = dialogView.findViewById(R.id.edit_definition);
        EditText etExample = dialogView.findViewById(R.id.edit_example);

        etTerm.setText(flashcard.getTerm());
        etDefinition.setText(flashcard.getDefinition());
        etExample.setText(flashcard.getExample() != null ? flashcard.getExample() : "");

        new AlertDialog.Builder(this)
                .setTitle("Chỉnh sửa flashcard")
                .setView(dialogView)
                .setPositiveButton("Lưu", (d, w) -> {
                    String term = etTerm.getText().toString().trim();
                    String def = etDefinition.getText().toString().trim();
                    if (term.isEmpty() || def.isEmpty()) {
                        Toast.makeText(this, "Vui lòng điền đủ thông tin", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    flashcard.setTerm(term);
                    flashcard.setDefinition(def);
                    flashcard.setExample(etExample.getText().toString().trim());
                    viewModel.updateFlashcard(flashcard);
                    Toast.makeText(this, "Đã cập nhật", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}
