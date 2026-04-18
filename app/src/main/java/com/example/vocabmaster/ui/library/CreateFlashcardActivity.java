package com.example.vocabmaster.ui.library;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.data.remote.AIService;
import com.example.vocabmaster.databinding.ActivityCreateFlashcardBinding;
import com.example.vocabmaster.ui.common.UiFeedback;

public class CreateFlashcardActivity extends AppCompatActivity {

    private ActivityCreateFlashcardBinding binding;
    private LibraryViewModel viewModel;
    private AIService aiService;
    private Uri selectedImageUri;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    binding.imagePreview.setImageURI(selectedImageUri);
                    binding.imagePreview.setVisibility(View.VISIBLE);
                    binding.layoutAddImagePrompt.setVisibility(View.GONE);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateFlashcardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LibraryViewModel.class);
        aiService = new AIService(getString(R.string.gemini_api_key));

        setupListeners();
    }

    private void setupListeners() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.cardSelectImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            imagePickerLauncher.launch(intent);
        });

        binding.btnAiGenerateImage.setOnClickListener(v -> generateAiImage());

        binding.btnSaveFlashcard.setOnClickListener(v -> saveFlashcard());
    }

    private void generateAiImage() {
        String term = binding.editTerm.getText().toString().trim();
        String definition = binding.editDefinition.getText().toString().trim();

        if (TextUtils.isEmpty(term)) {
            Toast.makeText(this, "Vui lòng nhập từ vựng trước", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressImage.setVisibility(View.VISIBLE);
        binding.btnAiGenerateImage.setEnabled(false);

        aiService.generateImageFromText(term, definition, new AIService.AICallback<String>() {
            @Override
            public void onSuccess(String imageUrl) {
                runOnUiThread(() -> {
                    binding.progressImage.setVisibility(View.GONE);
                    binding.btnAiGenerateImage.setEnabled(true);
                    selectedImageUri = Uri.parse(imageUrl);
                    Glide.with(CreateFlashcardActivity.this)
                            .load(imageUrl)
                            .centerCrop()
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .into(binding.imagePreview);
                    binding.imagePreview.setVisibility(View.VISIBLE);
                    binding.layoutAddImagePrompt.setVisibility(View.GONE);
                });
            }

            @Override
            public void onError(Throwable t) {
                runOnUiThread(() -> {
                    binding.progressImage.setVisibility(View.GONE);
                    binding.btnAiGenerateImage.setEnabled(true);
                    Toast.makeText(CreateFlashcardActivity.this, "Lỗi AI: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void saveFlashcard() {
        String term = binding.editTerm.getText().toString().trim();
        String definition = binding.editDefinition.getText().toString().trim();
        String tag = binding.editTag.getText().toString().trim();

        if (TextUtils.isEmpty(term) || TextUtils.isEmpty(definition)) {
            Toast.makeText(this, "Vui lòng nhập đủ thông tin", Toast.LENGTH_SHORT).show();
            return;
        }

        Flashcard card = new Flashcard(term, definition);
        card.setTag(tag);
        if (selectedImageUri != null) {
            card.setImageUrl(selectedImageUri.toString());
        }

        viewModel.addPersonalFlashcard(card);
        Toast.makeText(this, "Đã lưu Flashcard mới", Toast.LENGTH_SHORT).show();
        finish();
    }
}
