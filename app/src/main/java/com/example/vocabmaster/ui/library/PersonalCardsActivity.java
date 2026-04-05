package com.example.vocabmaster.ui.library;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

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
        
        setupRecyclerView();
        
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        viewModel.getPersonalFlashcards().observe(this, flashcards -> {
            if (flashcards != null && !flashcards.isEmpty()) {
                adapter.submitList(flashcards);
                binding.textEmpty.setVisibility(View.GONE);
            } else {
                binding.textEmpty.setVisibility(View.VISIBLE);
                // Nếu không còn flashcard nào, tự động quay lại Library
                Toast.makeText(this, "Không còn thẻ nào, quay lại Thư viện", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new FlashcardListAdapter(flashcard -> viewModel.deleteFlashcard(flashcard));
        binding.recyclerFlashcards.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerFlashcards.setAdapter(adapter);
    }
}
