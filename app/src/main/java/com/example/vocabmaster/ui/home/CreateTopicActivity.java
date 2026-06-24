package com.example.vocabmaster.ui.home;

import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.vocabmaster.data.api.DictionaryClient;
import com.example.vocabmaster.data.remote.DictionaryResponse;
import com.example.vocabmaster.data.remote.FreeDictionaryApiService;
import com.example.vocabmaster.data.remote.GeneratedFlashcard;
import com.example.vocabmaster.data.remote.GeminiFlashcardGenerator;
import com.example.vocabmaster.data.remote.UnsplashHelper;
import com.example.vocabmaster.databinding.ActivityCreateTopicBinding;
import com.example.vocabmaster.databinding.DialogGenerateAiCardsBinding;
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
    private GeminiFlashcardGenerator flashcardGenerator;
    private String currentTopicId = null;
    private Uri selectedImageUri = null;
    private String existingImageUrl = null;
    private final List<GeneratedFlashcard> pendingGeneratedCards = new ArrayList<>();
    private boolean applyingGeneratedWords = false;
    private String generatedWordList = "";

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
        flashcardGenerator = new GeminiFlashcardGenerator();

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

        binding.btnGenerateAiCards.setVisibility(currentTopicId == null ? View.VISIBLE : View.GONE);
        binding.btnGenerateAiCards.setOnClickListener(v -> showGenerateAiDialog());

        binding.editWordList.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!applyingGeneratedWords
                        && !pendingGeneratedCards.isEmpty()
                        && !s.toString().equals(generatedWordList)) {
                    pendingGeneratedCards.clear();
                    generatedWordList = "";
                }
            }
        });

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

    private void showGenerateAiDialog() {
        DialogGenerateAiCardsBinding dialogBinding =
                DialogGenerateAiCardsBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogBinding.getRoot())
                .create();
        boolean[] running = {false};

        String currentName = textOf(binding.editTopicName);
        if (!TextUtils.isEmpty(currentName)) {
            dialogBinding.editAiTopic.setText(currentName);
        }

        dialogBinding.btnCancelAi.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnStartAi.setOnClickListener(v -> {
            String topic = textOf(dialogBinding.editAiTopic);
            if (TextUtils.isEmpty(topic)) {
                dialogBinding.inputAiTopic.setError("Vui lòng nhập chủ đề");
                return;
            }

            dialogBinding.inputAiTopic.setError(null);
            running[0] = true;
            setAiDialogLoading(dialogBinding, dialog, true, "Đang tạo flashcard...");
            binding.btnSaveTopic.setEnabled(false);
            binding.btnGenerateAiCards.setEnabled(false);

            flashcardGenerator.generateCards(topic, GeminiFlashcardGenerator.DEFAULT_CARD_COUNT,
                    new GeminiFlashcardGenerator.Callback() {
                        @Override
                        public void onSuccess(List<GeneratedFlashcard> cards) {
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) return;
                                dialogBinding.textAiStatus.setText("Đang tải ảnh minh họa...");
                                attachImagesToGeneratedCards(cards, cardsWithImages ->
                                        runOnUiThread(() -> {
                                            if (isFinishing() || isDestroyed()) return;
                                            running[0] = false;
                                            setAiDialogLoading(dialogBinding, dialog, false, "");
                                            resetGenerationControls();
                                            applyGeneratedCards(topic, cardsWithImages);
                                            dialog.dismiss();
                                            Toast.makeText(
                                                    CreateTopicActivity.this,
                                                    "Đã tạo " + cardsWithImages.size()
                                                            + " flashcard bằng AI",
                                                    Toast.LENGTH_SHORT
                                            ).show();
                                        }));
                            });
                        }

                        @Override
                        public void onError(Throwable t) {
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) return;
                                running[0] = false;
                                setAiDialogLoading(dialogBinding, dialog, false,
                                        "Không thể tạo flashcard. Vui lòng thử lại.");
                                resetGenerationControls();
                                Toast.makeText(
                                        CreateTopicActivity.this,
                                        "Lỗi AI: " + (t != null ? t.getMessage() : "Không rõ"),
                                        Toast.LENGTH_SHORT
                                ).show();
                            });
                        }
                    });
        });

        dialog.setOnDismissListener(d -> {
            if (!running[0]) {
                resetGenerationControls();
            }
        });
        dialog.show();
    }

    private void setAiDialogLoading(DialogGenerateAiCardsBinding dialogBinding, AlertDialog dialog,
                                    boolean loading, String message) {
        dialog.setCancelable(!loading);
        dialogBinding.editAiTopic.setEnabled(!loading);
        dialogBinding.btnCancelAi.setEnabled(!loading);
        dialogBinding.btnStartAi.setEnabled(!loading);
        dialogBinding.progressAiGenerate.setVisibility(loading ? View.VISIBLE : View.GONE);

        if (!TextUtils.isEmpty(message)) {
            dialogBinding.textAiStatus.setText(message);
            dialogBinding.textAiStatus.setVisibility(View.VISIBLE);
        } else if (!loading) {
            dialogBinding.textAiStatus.setVisibility(View.GONE);
        }
    }

    private void applyGeneratedCards(String topic, List<GeneratedFlashcard> cards) {
        pendingGeneratedCards.clear();
        pendingGeneratedCards.addAll(cards);

        if (TextUtils.isEmpty(textOf(binding.editTopicName))) {
            binding.editTopicName.setText(topic);
        }

        generatedWordList = buildGeneratedWordList(cards);
        applyingGeneratedWords = true;
        binding.editWordList.setText(generatedWordList);
        binding.editWordList.setError(null);
        applyingGeneratedWords = false;
    }

    private String buildGeneratedWordList(List<GeneratedFlashcard> cards) {
        StringBuilder builder = new StringBuilder();
        for (GeneratedFlashcard card : cards) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(card.getWord());
        }
        return builder.toString();
    }

    private void attachImagesToGeneratedCards(List<GeneratedFlashcard> cards,
                                              GeneratedCardsCallback callback) {
        if (cards == null || cards.isEmpty()) {
            callback.onComplete(new ArrayList<>());
            return;
        }

        UnsplashHelper unsplash = new UnsplashHelper();
        final int total = cards.size();
        final int[] completed = {0};

        for (GeneratedFlashcard card : cards) {
            if (!TextUtils.isEmpty(card.getImage_url())) {
                if (++completed[0] == total) callback.onComplete(cards);
                continue;
            }

            String keyword = card.getWord();
            unsplash.searchImage(keyword, new UnsplashHelper.ImageCallback() {
                @Override
                public void onSuccess(String imageUrl) {
                    card.setImage_url(imageUrl);
                    if (++completed[0] == total) callback.onComplete(cards);
                }

                @Override
                public void onError() {
                    card.setImage_url(fallbackImageUrl(keyword));
                    if (++completed[0] == total) callback.onComplete(cards);
                }
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
        String name = textOf(binding.editTopicName);
        String wordListRaw = textOf(binding.editWordList);

        if (TextUtils.isEmpty(name)) {
            binding.editTopicName.setError("Vui lòng nhập tên");
            return;
        }

        if (currentTopicId == null && !pendingGeneratedCards.isEmpty()) {
            saveGeneratedTopicToFirebase(name, new ArrayList<>(pendingGeneratedCards));
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

    private void saveGeneratedTopicToFirebase(String name, List<GeneratedFlashcard> cards) {
        String userId = auth.getUid();
        if (userId == null) return;
        if (cards.isEmpty()) {
            Toast.makeText(this, "Danh sách AI đang trống", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnSaveTopic.setEnabled(false);
        binding.btnGenerateAiCards.setEnabled(false);
        binding.btnSaveTopic.setText("Đang lưu...");

        Map<String, Object> topicData = new HashMap<>();
        topicData.put("name", name);
        topicData.put("word_count", cards.size());
        topicData.put("isDownloaded", true);
        topicData.put("imageUrl", topicImageUrl(name));
        topicData.put("createdAt", com.google.firebase.Timestamp.now());
        topicData.put("updatedAt", com.google.firebase.Timestamp.now());

        db.collection("users").document(userId)
                .collection("personal_topics")
                .add(topicData)
                .addOnSuccessListener(doc -> saveGeneratedWordsToFirestore(userId, doc.getId(), cards))
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi lưu bộ từ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    resetBtn();
                });
    }

    private void saveGeneratedWordsToFirestore(String userId, String topicId,
                                               List<GeneratedFlashcard> cards) {
        final int[] count = {0};
        for (GeneratedFlashcard card : cards) {
            saveWordToFirestore(userId, topicId, generatedCardToMap(card), count, cards.size());
        }
    }

    private Map<String, Object> generatedCardToMap(GeneratedFlashcard card) {
        Map<String, Object> wordData = new HashMap<>();
        String word = nonNull(card.getWord());
        wordData.put("word", word);
        wordData.put("definition", nonNull(card.getDefinition()));
        wordData.put("vietnamese_translation", nonNull(card.getVietnamese_translation()));
        wordData.put("part_of_speech", nonNull(card.getPart_of_speech()));
        wordData.put("phonetic", nonNull(card.getPhonetic()));
        wordData.put("example_sentence", nonNull(card.getExample_sentence()));
        wordData.put("audio_url", "");
        wordData.put("image_url", TextUtils.isEmpty(card.getImage_url())
                ? fallbackImageUrl(word)
                : card.getImage_url());
        return wordData;
    }

    private void saveToFirebase(String name, List<String> words) {
        String userId = auth.getUid();
        if (userId == null) return;

        binding.btnSaveTopic.setEnabled(false);
        binding.btnGenerateAiCards.setEnabled(false);
        binding.btnSaveTopic.setText("Đang lưu...");

        Map<String, Object> topicData = new HashMap<>();
        topicData.put("name", name);
        if (!words.isEmpty()) topicData.put("word_count", words.size());
        topicData.put("isDownloaded", true);
        topicData.put("imageUrl", topicImageUrl(name));
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
                public void onResponse(Call<List<DictionaryResponse>> call,
                                       Response<List<DictionaryResponse>> response) {
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

                    unsplash.searchImage(wordStr, new UnsplashHelper.ImageCallback() {
                        @Override
                        public void onSuccess(String imageUrl) {
                            wordData.put("image_url", imageUrl);
                            saveWordToFirestore(userId, topicId, wordData, count, words.size());
                        }

                        @Override
                        public void onError() {
                            wordData.put("image_url", fallbackImageUrl(wordStr));
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

    private String topicImageUrl(String name) {
        if (selectedImageUri != null) return selectedImageUri.toString();
        if (!TextUtils.isEmpty(existingImageUrl)) return existingImageUrl;
        return "https://loremflickr.com/600/400/education," + Uri.encode(name);
    }

    private String fallbackImageUrl(String keyword) {
        String safeKeyword = TextUtils.isEmpty(keyword) ? "vocabulary" : keyword;
        return "https://loremflickr.com/400/300/" + Uri.encode(safeKeyword);
    }

    private String textOf(TextView view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private String nonNull(String value) {
        return value == null ? "" : value;
    }

    private void resetGenerationControls() {
        binding.btnSaveTopic.setEnabled(true);
        binding.btnGenerateAiCards.setEnabled(currentTopicId == null);
    }

    private void resetBtn() {
        resetGenerationControls();
        binding.btnSaveTopic.setText(currentTopicId == null ? "Tạo bộ từ ngay" : "Cập nhật bộ từ");
    }

    private interface GeneratedCardsCallback {
        void onComplete(List<GeneratedFlashcard> cards);
    }
}
