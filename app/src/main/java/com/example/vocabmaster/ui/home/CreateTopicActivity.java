package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.vocabmaster.data.api.DictionaryClient;
import com.example.vocabmaster.data.remote.DictionaryResponse;
import com.example.vocabmaster.data.remote.FreeDictionaryApiService;
import com.example.vocabmaster.data.remote.UnsplashHelper;
import com.example.vocabmaster.databinding.ActivityCreateTopicBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CreateTopicActivity extends AppCompatActivity {
    private ActivityCreateTopicBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FreeDictionaryApiService dictionaryService;
    private String currentTopicId = null;
    private Uri selectedImageUri = null;
    private String existingImageUrl = null;

    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    binding.imgTopicCover.setPadding(0, 0, 0, 0);
                    binding.imgTopicCover.setImageURI(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateTopicBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        dictionaryService = DictionaryClient.getService();

        currentTopicId = getIntent().getStringExtra("topic_id");
        if (currentTopicId != null) {
            loadTopicData();
            binding.btnSaveTopic.setText("Cập nhật bộ từ");
        }

        setupListeners();
    }

    private void loadTopicData() {
        String userId = auth.getUid();
        if (userId == null) return;

        db.collection("users").document(userId)
                .collection("personal_topics").document(currentTopicId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        binding.editTopicName.setText(doc.getString("name"));
                        existingImageUrl = doc.getString("imageUrl");
                        if (existingImageUrl != null && !existingImageUrl.isEmpty()) {
                            binding.imgTopicCover.setPadding(0, 0, 0, 0);
                            Glide.with(this).load(existingImageUrl).into(binding.imgTopicCover);
                        }
                    }
                });
    }

    private void setupListeners() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        
        binding.imgTopicCover.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        
        binding.btnSaveTopic.setOnClickListener(v -> validateAndSave());
        
        if (currentTopicId != null) {
            binding.toolbar.inflateMenu(com.example.vocabmaster.R.menu.menu_course_detail);
            binding.toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == com.example.vocabmaster.R.id.action_delete_course) {
                    confirmDelete();
                    return true;
                }
                return false;
            });
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Xóa bộ từ")
                .setMessage("Bạn có chắc chắn muốn xóa bộ từ này không?")
                .setPositiveButton("Xóa", (dialog, which) -> deleteTopic())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteTopic() {
        String userId = auth.getUid();
        if (userId == null || currentTopicId == null) return;

        db.collection("users").document(userId)
                .collection("personal_topics").document(currentTopicId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Đã xóa bộ từ", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void validateAndSave() {
        String name = binding.editTopicName.getText().toString().trim();
        String wordListRaw = binding.editWordList.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            binding.editTopicName.setError("Vui lòng nhập tên");
            return;
        }

        if (currentTopicId == null && TextUtils.isEmpty(wordListRaw)) {
            binding.editWordList.setError("Vui lòng nhập danh sách từ");
            return;
        }

        List<String> validWords = new ArrayList<>();
        if (!TextUtils.isEmpty(wordListRaw)) {
            String[] words = wordListRaw.split("\n");
            for (String w : words) {
                if (!w.trim().isEmpty()) validWords.add(w.trim());
            }
        }

        saveToFirebase(name, validWords);
    }

    private void saveToFirebase(String name, List<String> words) {
        String userId = auth.getUid();
        if (userId == null) return;

        binding.btnSaveTopic.setEnabled(false);
        binding.btnSaveTopic.setText("Đang lưu...");

        // Thay đổi source.unsplash.com (đã ngừng hoạt động) sang loremflickr.com
        String imageUrl = (selectedImageUri != null) ? selectedImageUri.toString() : 
                         (existingImageUrl != null ? existingImageUrl : "https://loremflickr.com/600/400/education," + name);

        Map<String, Object> topicData = new HashMap<>();
        topicData.put("name", name);
        if (!words.isEmpty()) topicData.put("word_count", words.size());
        topicData.put("isDownloaded", true);
        topicData.put("imageUrl", imageUrl);
        topicData.put("updatedAt", com.google.firebase.Timestamp.now());

        if (currentTopicId == null) {
            topicData.put("createdAt", com.google.firebase.Timestamp.now());
            db.collection("users").document(userId)
                    .collection("personal_topics")
                    .add(topicData)
                    .addOnSuccessListener(doc -> {
                        if (!words.isEmpty()) fetchAndSaveWords(doc.getId(), words);
                        else finish();
                    })
                    .addOnFailureListener(e -> resetBtn());
        } else {
            db.collection("users").document(userId)
                    .collection("personal_topics").document(currentTopicId)
                    .update(topicData)
                    .addOnSuccessListener(aVoid -> {
                        if (!words.isEmpty()) fetchAndSaveWords(currentTopicId, words);
                        else finish();
                    })
                    .addOnFailureListener(e -> resetBtn());
        }
    }

    private void fetchAndSaveWords(String topicId, List<String> words) {
        String userId = auth.getUid();
        final int[] count = {0};
        UnsplashHelper unsplash = new UnsplashHelper();

        for (String wordStr : words) {
            dictionaryService.getDefinition(wordStr).enqueue(new Callback<List<DictionaryResponse>>() {
                @Override
                public void onResponse(Call<List<DictionaryResponse>> call, Response<List<DictionaryResponse>> response) {
                    Map<String, Object> wordData = new HashMap<>();
                    wordData.put("word", wordStr);

                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        DictionaryResponse dr = response.body().get(0);
                        wordData.put("phonetic", dr.phonetic != null ? dr.phonetic : "");

                        if (dr.phonetics != null && !dr.phonetics.isEmpty()) {
                            for (DictionaryResponse.Phonetic p : dr.phonetics) {
                                if (p.audio != null && !p.audio.isEmpty()) {
                                    wordData.put("audio_url", p.audio);
                                    break;
                                }
                            }
                        }

                        if (dr.meanings != null && !dr.meanings.isEmpty()) {
                            DictionaryResponse.Meaning m = dr.meanings.get(0);
                            wordData.put("part_of_speech", m.partOfSpeech);
                            if (m.definitions != null && !m.definitions.isEmpty()) {
                                wordData.put("definition", m.definitions.get(0).definition);
                                wordData.put("example_sentence", m.definitions.get(0).example);
                            }
                        }
                    } else {
                        wordData.put("definition", "Click để nhập nghĩa...");
                    }

                    // Lấy ảnh từ Unsplash rồi mới lưu
                    unsplash.searchImage(wordStr, new UnsplashHelper.ImageCallback() {
                        @Override
                        public void onSuccess(String imageUrl) {
                            wordData.put("image_url", imageUrl);
                            saveWordToFirestore(userId, topicId, wordData, count, words.size());
                        }

                        @Override
                        public void onError() {
                            // Fallback nếu Unsplash fail
                            wordData.put("image_url", "https://loremflickr.com/400/300/" + wordStr);
                            saveWordToFirestore(userId, topicId, wordData, count, words.size());
                        }
                    });
                }

                @Override
                public void onFailure(Call<List<DictionaryResponse>> call, Throwable t) {
                    count[0]++;
                    if (count[0] == words.size()) finish();
                }
            });
        }
    }

    private void saveWordToFirestore(String userId, String topicId,
                                      Map<String, Object> wordData,
                                      int[] count, int total) {
        db.collection("users").document(userId)
                .collection("personal_topics").document(topicId)
                .collection("vocabularies").add(wordData)
                .addOnCompleteListener(t -> {
                    if (++count[0] == total) {
                        UiFeedback.performHaptic(CreateTopicActivity.this, 50);
                        finish();
                    }
                });
    }

    private void resetBtn() {
        binding.btnSaveTopic.setEnabled(true);
        binding.btnSaveTopic.setText("Lưu lại");
    }
}
