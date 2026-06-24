package com.example.vocabmaster.ui.home;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.vision.YoloDetection;
import com.example.vocabmaster.data.vision.YoloDetector;
import com.example.vocabmaster.data.api.DictionaryClient;
import com.example.vocabmaster.data.remote.DictionaryResponse;
import com.example.vocabmaster.data.remote.FreeDictionaryApiService;
import com.example.vocabmaster.data.remote.UnsplashHelper;
import com.example.vocabmaster.databinding.ActivityYoloVocabularyBinding;
import com.example.vocabmaster.databinding.DialogYoloDuplicateWordsBinding;
import com.example.vocabmaster.databinding.DialogYoloImageDetailBinding;
import com.example.vocabmaster.databinding.DialogYoloTopicPickerBinding;
import com.example.vocabmaster.databinding.ItemYoloVocabBinding;
import com.example.vocabmaster.databinding.ItemYoloTopicOptionBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class YoloVocabularyActivity extends AppCompatActivity {
    private ActivityYoloVocabularyBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ResultAdapter adapter = new ResultAdapter();
    private FreeDictionaryApiService dictionaryService;
    private Bitmap selectedBitmap;
    private Bitmap previewBitmap;
    private String topicId;
    private boolean isPersonal;

    private final ActivityResultLauncher<Void> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(),
            bitmap -> {
                if (bitmap != null) {
                    selectedBitmap = bitmap;
                    setPreviewImage(selectedBitmap);
                    binding.textStatus.setText("Ảnh đã sẵn sàng để phân tích.");
                    binding.btnAddSelected.setEnabled(false);
                    adapter.submit(new ArrayList<>());
                }
            });

    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) loadSelectedImage(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityYoloVocabularyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        topicId = getIntent().getStringExtra("topic_id");
        isPersonal = getIntent().getBooleanExtra("is_personal", false);
        dictionaryService = DictionaryClient.getService();

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.recyclerResults.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerResults.setAdapter(adapter);

        binding.btnPickImage.setOnClickListener(v -> cameraLauncher.launch(null));
        binding.btnRunYolo.setOnClickListener(v -> runYolo());
        binding.btnAddSelected.setOnClickListener(v -> addSelectedWords());
        binding.imgPreview.setOnClickListener(v -> showImageDetailDialog());

        binding.btnPickImage.setText("Chụp ảnh");

        if (!isPersonal) {
            binding.textStatus.setText("Thu YOLO duoc, nhung chi luu vao bo tu ca nhan.");
        }

        if (getIntent().getBooleanExtra("auto_capture", false)) {
            binding.getRoot().post(() -> cameraLauncher.launch(null));
        }
    }

    private void loadSelectedImage(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            selectedBitmap = BitmapFactory.decodeStream(input);
            setPreviewImage(selectedBitmap);
            binding.textStatus.setText("Ảnh đã sẵn sàng để phân tích.");
            binding.btnAddSelected.setEnabled(false);
            adapter.submit(new ArrayList<>());
        } catch (Exception e) {
            Toast.makeText(this, "Không đọc được ảnh", Toast.LENGTH_SHORT).show();
        }
    }

    private void runYolo() {
        if (selectedBitmap == null) {
            Toast.makeText(this, "Hãy chọn ảnh trước", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        executor.execute(() -> {
            try (YoloDetector detector = new YoloDetector(this)) {
                List<YoloDetection> detections = detector.detect(selectedBitmap);
                runOnUiThread(() -> {
                    adapter.submit(detections);
                    setPreviewImage(detections.isEmpty()
                            ? selectedBitmap
                            : drawDetections(selectedBitmap, detections));
                    binding.textStatus.setText(detections.isEmpty()
                            ? "Phân tích ảnh không thành công."
                            : "Tìm thấy " + detections.size() + " từ gợi ý.");
                    binding.btnAddSelected.setEnabled(isPersonal && !detections.isEmpty());
                    setLoading(false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.textStatus.setText("Thieu model assets/" + YoloDetector.MODEL_FILE
                            + " hoac model khong dung dinh dang.");
                    Toast.makeText(this, "Phân tích ảnh không thành công: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    setLoading(false);
                });
            }
        });
    }

    private Bitmap drawDetections(Bitmap source, List<YoloDetection> detections) {
        Bitmap annotated = source.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(annotated);
        float density = getResources().getDisplayMetrics().density;

        Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(3.5f * density);
        boxPaint.setColor(Color.rgb(84, 90, 232));

        Paint labelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelBgPaint.setStyle(Paint.Style.FILL);
        labelBgPaint.setColor(Color.rgb(84, 90, 232));

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(14f * density);
        textPaint.setFakeBoldText(true);

        float labelPaddingX = 8f * density;
        float labelPaddingY = 5f * density;
        float corner = 6f * density;

        for (YoloDetection detection : detections) {
            RectF box = new RectF(
                    detection.getLeft(),
                    detection.getTop(),
                    detection.getRight(),
                    detection.getBottom());
            canvas.drawRoundRect(box, corner, corner, boxPaint);

            String label = String.format(Locale.US, "%s %.0f%%",
                    detection.getLabel(), detection.getConfidence() * 100f);
            float textWidth = textPaint.measureText(label);
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float labelHeight = (metrics.descent - metrics.ascent) + labelPaddingY * 2f;
            float labelWidth = textWidth + labelPaddingX * 2f;
            float labelTop = Math.max(0, box.top - labelHeight);
            float labelLeft = Math.min(box.left, Math.max(0, annotated.getWidth() - labelWidth));
            RectF labelRect = new RectF(labelLeft, labelTop, labelLeft + labelWidth, labelTop + labelHeight);
            canvas.drawRoundRect(labelRect, corner, corner, labelBgPaint);
            canvas.drawText(label, labelLeft + labelPaddingX,
                    labelTop + labelPaddingY - metrics.ascent, textPaint);
        }
        return annotated;
    }

    private void setPreviewImage(Bitmap bitmap) {
        previewBitmap = bitmap;
        binding.imgPreview.setImageBitmap(bitmap);
    }

    private void showImageDetailDialog() {
        if (previewBitmap == null) return;

        DialogYoloImageDetailBinding dialogBinding = DialogYoloImageDetailBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogBinding.getRoot())
                .create();
        dialogBinding.imgDetail.setImageBitmap(previewBitmap);
        dialogBinding.btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void addSelectedWords() {
        if (!isPersonal) {
            Toast.makeText(this, "Chỉ thêm vào bộ từ cá nhân", Toast.LENGTH_SHORT).show();
            return;
        }

        List<YoloDetection> selected = adapter.getSelected();
        if (selected.isEmpty()) {
            Toast.makeText(this, "Chưa chọn từ nào", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(this, "Cần đăng nhập để lưu từ", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        if (topicId == null) {
            showTopicPicker(uid, selected);
            return;
        }

        saveSelectedWords(uid, topicId, selected);
    }

    private void showTopicPicker(String uid, List<YoloDetection> selected) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(uid)
                .collection("personal_topics")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    setLoading(false);
                    List<TopicOption> options = new ArrayList<>();
                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        String name = doc.getString("name");
                        if (name == null || name.trim().isEmpty()) name = "Bộ từ không tên";
                        options.add(new TopicOption(doc.getId(), name));
                    }
                    options.add(new TopicOption(null, "Your Vocabulary"));

                    showTopicPickerDialog(uid, selected, options);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Không tải được danh sách bộ từ", Toast.LENGTH_SHORT).show();
                });
    }

    private void showTopicPickerDialog(String uid, List<YoloDetection> selected, List<TopicOption> options) {
        DialogYoloTopicPickerBinding dialogBinding = DialogYoloTopicPickerBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogBinding.getRoot())
                .create();

        TopicOptionAdapter topicAdapter = new TopicOptionAdapter(options, selectedTopic -> {
            dialog.dismiss();
            setLoading(true);
            if (selectedTopic.id == null) {
                findOrCreateYoloTopic(uid, selected);
            } else {
                topicId = selectedTopic.id;
                saveSelectedWords(uid, topicId, selected);
            }
        });
        dialogBinding.recyclerTopicOptions.setLayoutManager(new LinearLayoutManager(this));
        dialogBinding.recyclerTopicOptions.setAdapter(topicAdapter);
        dialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void findOrCreateYoloTopic(String uid, List<YoloDetection> selected) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(uid)
                .collection("personal_topics")
                .whereEqualTo("source", "yolo_quick")
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (!qs.isEmpty()) {
                        topicId = qs.getDocuments().get(0).getId();
                        db.collection("users").document(uid)
                                .collection("personal_topics").document(topicId)
                                .update("imageUrl", getDefaultYoloCoverUri(), "updatedAt", Timestamp.now())
                                .addOnCompleteListener(task -> saveSelectedWords(uid, topicId, selected));
                        return;
                    }

                    Map<String, Object> topicData = new HashMap<>();
                    topicData.put("name", "your vocabulary");
                    topicData.put("source", "yolo_quick");
                    topicData.put("isDownloaded", true);
                    topicData.put("imageUrl", getDefaultYoloCoverUri());
                    topicData.put("createdAt", Timestamp.now());
                    topicData.put("updatedAt", Timestamp.now());

                    db.collection("users").document(uid)
                            .collection("personal_topics")
                            .add(topicData)
                            .addOnSuccessListener(doc -> {
                                topicId = doc.getId();
                                saveSelectedWords(uid, topicId, selected);
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                Toast.makeText(this, "Tạo bộ từ không thành công", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Không kiểm tra được bộ từ", Toast.LENGTH_SHORT).show();
                });
    }

    private String getDefaultYoloCoverUri() {
        return "android.resource://" + getPackageName() + "/" + R.drawable.vcdefault;
    }

    private void saveSelectedWords(String uid, String targetTopicId, List<YoloDetection> selected) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(uid)
                .collection("personal_topics").document(targetTopicId)
                .collection("vocabularies")
                .get()
                .addOnSuccessListener(snapshot -> {
                    Set<String> existingWords = new HashSet<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String word = doc.getString("word");
                        if (word != null && !word.trim().isEmpty()) {
                            existingWords.add(word.trim().toLowerCase(Locale.US));
                        }
                    }
                    handleDuplicatesBeforeSave(db, uid, targetTopicId, selected, existingWords);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Không kiểm tra được từ trùng", Toast.LENGTH_SHORT).show();
                    setLoading(false);
                });
    }

    private void handleDuplicatesBeforeSave(FirebaseFirestore db, String uid, String targetTopicId,
                                            List<YoloDetection> selected, Set<String> existingWords) {
        List<String> duplicateWords = new ArrayList<>();
        Set<String> seenInSelection = new HashSet<>();
        for (YoloDetection detection : selected) {
            String normalized = detection.getLabel().trim().toLowerCase(Locale.US);
            if (existingWords.contains(normalized) || seenInSelection.contains(normalized)) {
                duplicateWords.add(detection.getLabel());
            }
            seenInSelection.add(normalized);
        }

        if (duplicateWords.isEmpty()) {
            saveWordsWithDuplicatePolicy(db, uid, targetTopicId, selected, existingWords, true);
            return;
        } else if (System.currentTimeMillis() >= 0) {
            showDuplicateWordsDialog(db, uid, targetTopicId, selected, existingWords, duplicateWords);
            return;
        }

        String message = "Các từ đã có sẵn: " + joinWords(duplicateWords)
                + "\n\nBạn có muốn thêm bản sao không?";
        new AlertDialog.Builder(this)
                .setTitle("Từ vựng bị trùng")
                .setMessage(message)
                .setPositiveButton("Thêm bản sao", (dialog, which) ->
                        saveWordsWithDuplicatePolicy(db, uid, targetTopicId, selected, existingWords, true))
                .setNegativeButton("Bỏ qua từ trùng", (dialog, which) ->
                        saveWordsWithDuplicatePolicy(db, uid, targetTopicId, selected, existingWords, false))
                .setNeutralButton("Hủy", (dialog, which) -> setLoading(false))
                .show();
    }

    private void showDuplicateWordsDialog(FirebaseFirestore db, String uid, String targetTopicId,
                                          List<YoloDetection> selected, Set<String> existingWords,
                                          List<String> duplicateWords) {
        DialogYoloDuplicateWordsBinding dialogBinding = DialogYoloDuplicateWordsBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogBinding.getRoot())
                .create();

        dialogBinding.textDuplicateWords.setText(joinWords(duplicateWords));
        dialogBinding.btnAddCopies.setOnClickListener(v -> {
            dialog.dismiss();
            saveWordsWithDuplicatePolicy(db, uid, targetTopicId, selected, existingWords, true);
        });
        dialogBinding.btnSkipDuplicates.setOnClickListener(v -> {
            dialog.dismiss();
            saveWordsWithDuplicatePolicy(db, uid, targetTopicId, selected, existingWords, false);
        });
        dialogBinding.btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            setLoading(false);
        });
        dialog.show();
    }

    private void saveWordsWithDuplicatePolicy(FirebaseFirestore db, String uid, String targetTopicId,
                                              List<YoloDetection> selected, Set<String> existingWords,
                                              boolean addDuplicates) {
        List<WordToSave> wordsToSave = new ArrayList<>();
        Set<String> reservedWords = new HashSet<>(existingWords);
        for (YoloDetection detection : selected) {
            String baseWord = detection.getLabel().trim();
            String normalized = baseWord.toLowerCase(Locale.US);
            if (reservedWords.contains(normalized)) {
                if (!addDuplicates) continue;
                baseWord = nextAvailableWordName(baseWord, reservedWords);
                normalized = baseWord.toLowerCase(Locale.US);
            }
            reservedWords.add(normalized);
            wordsToSave.add(new WordToSave(detection, baseWord));
        }

        if (wordsToSave.isEmpty()) {
            refreshTopicWordCount(db, uid, targetTopicId, () -> {
                setLoading(false);
                Toast.makeText(this, "Không có từ mới để thêm", Toast.LENGTH_SHORT).show();
            });
            return;
        }

        final int[] done = {0};
        final int[] success = {0};
        for (WordToSave item : wordsToSave) {
            saveEnrichedWord(db, uid, targetTopicId, item, task -> {
                if (task.isSuccessful()) success[0]++;
                done[0]++;
                if (done[0] == wordsToSave.size()) {
                    refreshTopicWordCount(db, uid, targetTopicId, () -> {
                        setLoading(false);
                        setResult(RESULT_OK);
                        Toast.makeText(this, "Đã thêm " + success[0] + " từ", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            });
        }
    }

    private void saveEnrichedWord(FirebaseFirestore db, String uid, String targetTopicId,
                                  WordToSave item,
                                  com.google.android.gms.tasks.OnCompleteListener<com.google.firebase.firestore.DocumentReference> onComplete) {
        Map<String, Object> wordData = buildWordData(item.detection, item.word);
        wordData.put("topic", targetTopicId);

        final boolean[] dictionaryDone = {false};
        final boolean[] imageDone = {false};

        Runnable saveWhenReady = () -> {
            if (!dictionaryDone[0] || !imageDone[0]) return;
            db.collection("users").document(uid)
                    .collection("personal_topics").document(targetTopicId)
                    .collection("vocabularies")
                    .add(wordData)
                    .addOnCompleteListener(onComplete);
        };

        String lookupWord = item.detection.getLabel();
        dictionaryService.getDefinition(lookupWord).enqueue(new Callback<List<DictionaryResponse>>() {
            @Override
            public void onResponse(Call<List<DictionaryResponse>> call, Response<List<DictionaryResponse>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    DictionaryResponse dr = response.body().get(0);
                    if (dr.phonetic != null) wordData.put("phonetic", dr.phonetic);
                    if (dr.phonetics != null) {
                        for (DictionaryResponse.Phonetic p : dr.phonetics) {
                            if (p.audio != null && !p.audio.isEmpty()) {
                                wordData.put("audio_url", p.audio);
                                break;
                            }
                        }
                    }
                    if (dr.meanings != null && !dr.meanings.isEmpty()) {
                        DictionaryResponse.Meaning meaning = dr.meanings.get(0);
                        if (meaning.partOfSpeech != null) wordData.put("part_of_speech", meaning.partOfSpeech);
                        if (meaning.definitions != null && !meaning.definitions.isEmpty()) {
                            DictionaryResponse.Definition definition = meaning.definitions.get(0);
                            if (definition.definition != null) wordData.put("definition", definition.definition);
                            if (definition.example != null) wordData.put("example_sentence", definition.example);
                        }
                    }
                }
                dictionaryDone[0] = true;
                saveWhenReady.run();
            }

            @Override
            public void onFailure(Call<List<DictionaryResponse>> call, Throwable t) {
                dictionaryDone[0] = true;
                saveWhenReady.run();
            }
        });

        new UnsplashHelper().searchImage(lookupWord, new UnsplashHelper.ImageCallback() {
            @Override
            public void onSuccess(String imageUrl) {
                wordData.put("image_url", imageUrl);
                imageDone[0] = true;
                saveWhenReady.run();
            }

            @Override
            public void onError() {
                wordData.put("image_url", "https://loremflickr.com/400/300/" + lookupWord);
                imageDone[0] = true;
                saveWhenReady.run();
            }
        });
    }

    private void refreshTopicWordCount(FirebaseFirestore db, String uid, String targetTopicId, Runnable onComplete) {
        db.collection("users").document(uid)
                .collection("personal_topics").document(targetTopicId)
                .collection("vocabularies")
                .get()
                .addOnSuccessListener(snapshot -> db.collection("users").document(uid)
                        .collection("personal_topics").document(targetTopicId)
                        .update("updatedAt", Timestamp.now(), "word_count", snapshot.size())
                        .addOnCompleteListener(task -> onComplete.run()))
                .addOnFailureListener(e -> db.collection("users").document(uid)
                        .collection("personal_topics").document(targetTopicId)
                        .update("updatedAt", Timestamp.now())
                        .addOnCompleteListener(task -> onComplete.run()));
    }

    private Map<String, Object> buildWordData(YoloDetection detection, String word) {
        String baseWord = detection.getLabel();
        Map<String, Object> wordData = new HashMap<>();
        wordData.put("word", word);
        wordData.put("definition", "An object detected in a real image.");
        wordData.put("example_sentence", "I can see a " + baseWord + " in the picture.");
        wordData.put("part_of_speech", "noun");
        wordData.put("vietnamese_translation", translateObject(baseWord));
        wordData.put("base_word", baseWord);
        wordData.put("topic", topicId);
        wordData.put("source", "yolo");
        wordData.put("confidence", detection.getConfidence());
        wordData.put("createdAt", Timestamp.now());
        return wordData;
    }

    private String nextAvailableWordName(String baseWord, Set<String> reservedWords) {
        int index = 2;
        String candidate;
        do {
            candidate = baseWord + " (" + index + ")";
            index++;
        } while (reservedWords.contains(candidate.toLowerCase(Locale.US)));
        return candidate;
    }

    private String joinWords(List<String> words) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) builder.append(", ");
            builder.append(words.get(i));
        }
        return builder.toString();
    }

    private String translateObject(String word) {
        Map<String, String> vi = new HashMap<>();
        vi.put("person", "nguoi");
        vi.put("bicycle", "xe dap");
        vi.put("car", "xe hoi");
        vi.put("motorcycle", "xe may");
        vi.put("bus", "xe buyt");
        vi.put("train", "tau hoa");
        vi.put("truck", "xe tai");
        vi.put("boat", "thuyen");
        vi.put("cat", "meo");
        vi.put("dog", "cho");
        vi.put("backpack", "ba lo");
        vi.put("umbrella", "du");
        vi.put("handbag", "tui xach");
        vi.put("suitcase", "vali");
        vi.put("bottle", "chai");
        vi.put("cup", "coc");
        vi.put("fork", "nia");
        vi.put("knife", "dao");
        vi.put("spoon", "muong");
        vi.put("bowl", "bat");
        vi.put("banana", "chuoi");
        vi.put("apple", "tao");
        vi.put("orange", "cam");
        vi.put("carrot", "ca rot");
        vi.put("chair", "ghe");
        vi.put("couch", "ghe sofa");
        vi.put("bed", "giuong");
        vi.put("dining table", "ban an");
        vi.put("tv", "tivi");
        vi.put("laptop", "may tinh xach tay");
        vi.put("mouse", "chuot may tinh");
        vi.put("remote", "dieu khien");
        vi.put("keyboard", "ban phim");
        vi.put("cell phone", "dien thoai");
        vi.put("microwave", "lo vi song");
        vi.put("oven", "lo nuong");
        vi.put("sink", "bon rua");
        vi.put("refrigerator", "tu lanh");
        vi.put("book", "sach");
        vi.put("clock", "dong ho");
        vi.put("vase", "binh hoa");
        vi.put("scissors", "keo");
        vi.put("toothbrush", "ban chai danh rang");
        return vi.getOrDefault(word, word);
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnPickImage.setEnabled(!loading);
        binding.btnRunYolo.setEnabled(!loading);
        binding.btnAddSelected.setEnabled(!loading && isPersonal && !adapter.getSelected().isEmpty());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private static class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.VH> {
        private final List<YoloDetection> items = new ArrayList<>();
        private final Set<String> selectedLabels = new HashSet<>();

        void submit(List<YoloDetection> detections) {
            items.clear();
            items.addAll(detections);
            selectedLabels.clear();
            for (YoloDetection detection : detections) {
                selectedLabels.add(detection.getLabel());
            }
            notifyDataSetChanged();
        }

        List<YoloDetection> getSelected() {
            List<YoloDetection> selected = new ArrayList<>();
            for (YoloDetection detection : items) {
                if (selectedLabels.contains(detection.getLabel())) selected.add(detection);
            }
            return selected;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemYoloVocabBinding binding = ItemYoloVocabBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            YoloDetection detection = items.get(position);
            holder.binding.textWord.setText(detection.getLabel());
            holder.binding.textDetail.setText(String.format(Locale.US, "%.0f%% confidence", detection.getConfidence() * 100f));
            holder.binding.checkWord.setOnCheckedChangeListener(null);
            holder.binding.checkWord.setChecked(selectedLabels.contains(detection.getLabel()));
            holder.binding.checkWord.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) selectedLabels.add(detection.getLabel());
                else selectedLabels.remove(detection.getLabel());
            });
            holder.itemView.setOnClickListener(v -> holder.binding.checkWord.toggle());
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final ItemYoloVocabBinding binding;

            VH(ItemYoloVocabBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private static class TopicOptionAdapter extends RecyclerView.Adapter<TopicOptionAdapter.VH> {
        private final List<TopicOption> items;
        private final OnTopicSelected listener;

        TopicOptionAdapter(List<TopicOption> items, OnTopicSelected listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemYoloTopicOptionBinding binding = ItemYoloTopicOptionBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            TopicOption option = items.get(position);
            holder.binding.textTopicName.setText(option.name);
            holder.itemView.setOnClickListener(v -> listener.onSelected(option));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        interface OnTopicSelected {
            void onSelected(TopicOption option);
        }

        static class VH extends RecyclerView.ViewHolder {
            final ItemYoloTopicOptionBinding binding;

            VH(ItemYoloTopicOptionBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private static class TopicOption {
        final String id;
        final String name;

        TopicOption(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static class WordToSave {
        final YoloDetection detection;
        final String word;

        WordToSave(YoloDetection detection, String word) {
            this.detection = detection;
            this.word = word;
        }
    }
}
