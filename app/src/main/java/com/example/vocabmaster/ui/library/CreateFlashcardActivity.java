package com.example.vocabmaster.ui.library;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.data.remote.AIService;
import com.example.vocabmaster.data.repository.CourseRepository;

public class CreateFlashcardActivity extends AppCompatActivity {
    private EditText etTerm, etDefinition, etTag;
    private ImageView imgPreview;
    private View layoutAddImagePrompt;
    private Button btnGenerateAI, btnSave;
    private ProgressBar progressBar;
    private AIService aiService;
    private String generatedImageUrl = "";

    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    generatedImageUrl = uri.toString();
                    showPreview(uri.toString());
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_flashcard);

        initViews();
        aiService = new AIService();

        btnGenerateAI.setOnClickListener(v -> generateImageWithAI());
        btnSave.setOnClickListener(v -> saveFlashcard());
        findViewById(R.id.card_select_image).setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        
        findViewById(R.id.toolbar).setOnClickListener(v -> finish());
    }

    private void initViews() {
        etTerm = findViewById(R.id.edit_term);
        etDefinition = findViewById(R.id.edit_definition);
        etTag = findViewById(R.id.edit_tag);
        imgPreview = findViewById(R.id.image_preview);
        layoutAddImagePrompt = findViewById(R.id.layout_add_image_prompt);
        btnGenerateAI = findViewById(R.id.btn_ai_generate_image);
        btnSave = findViewById(R.id.btn_save_flashcard);
        progressBar = findViewById(R.id.progress_image);
    }

    private void showPreview(String url) {
        layoutAddImagePrompt.setVisibility(View.GONE);
        imgPreview.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(url)
                .placeholder(R.drawable.macdinh)
                .error(R.drawable.macdinh)
                .into(imgPreview);
    }

    private void generateImageWithAI() {
        String term = etTerm.getText().toString().trim();
        String definition = etDefinition.getText().toString().trim();

        if (term.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập từ vựng!", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnGenerateAI.setEnabled(false);

        aiService.generateImageFromText(term, definition, new AIService.AICallback<String>() {
            @Override
            public void onSuccess(String imageUrl) {
                generatedImageUrl = imageUrl;
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnGenerateAI.setEnabled(true);
                    showPreview(imageUrl);
                });
            }

            @Override
            public void onError(Throwable t) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnGenerateAI.setEnabled(true);
                    // Fallback to loremflickr if AI fails
                    generatedImageUrl = "https://loremflickr.com/400/300/" + term;
                    showPreview(generatedImageUrl);
                    Toast.makeText(CreateFlashcardActivity.this, "Sử dụng ảnh minh họa mặc định", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void saveFlashcard() {
        String term = etTerm.getText().toString().trim();
        String definition = etDefinition.getText().toString().trim();
        String tag = etTag.getText().toString().trim();

        if (term.isEmpty() || definition.isEmpty()) {
            Toast.makeText(this, "Vui lòng điền đủ thông tin!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Nếu chưa có ảnh, tự động tạo link ảnh từ keyword
        if (generatedImageUrl.isEmpty()) {
            generatedImageUrl = "https://loremflickr.com/400/300/" + term;
        }

        Flashcard flashcard = new Flashcard(term, definition);
        flashcard.setImageUrl(generatedImageUrl);
        flashcard.setTag(tag);

        new CourseRepository(getApplication()).addPersonalFlashcard(flashcard);
        
        Toast.makeText(this, "Đã lưu flashcard!", Toast.LENGTH_SHORT).show();
        finish();
    }
}
