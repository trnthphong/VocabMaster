package com.example.vocabmaster.ui.topic;

import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.R;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.local.VocabularyDao;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.data.remote.GeminiTranslator;
import com.example.vocabmaster.databinding.ActivityTopicDetailBinding;
import com.example.vocabmaster.databinding.ItemFlashcardHorizontalBinding;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TopicDetailActivity extends AppCompatActivity {

    private ActivityTopicDetailBinding binding;
    private VocabularyDao vocabularyDao;
    private FirebaseFirestore db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String topicId;
    private String topicTitle;
    private boolean isPersonal;
    private MediaPlayer mediaPlayer;
    private List<Vocabulary> allWords = new ArrayList<>();
    private boolean hasChanges = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTopicDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        topicId = getIntent().getStringExtra("topic_id");
        topicTitle = getIntent().getStringExtra("topic_title");
        isPersonal = getIntent().getBooleanExtra("is_personal_topic", false);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.inflateMenu(R.menu.menu_topic_detail);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_delete_topic) {
                confirmDelete();
                return true;
            }
            return false;
        });

        // Set title viết hoa
        String title = topicTitle != null ? topicTitle : "Topic";
        binding.toolbarTitle.setText(title.toUpperCase());

        vocabularyDao = AppDatabase.getDatabase(this).vocabularyDao();
        db = FirebaseFirestore.getInstance();
        mediaPlayer = new MediaPlayer();

        binding.btnLearnNew.setOnClickListener(v -> startLearning());
        binding.btnFlashcard.setOnClickListener(v -> openFlashcard());

        // Show edit button only for personal topics
        if (isPersonal) {
            binding.btnEditTopic.setVisibility(View.VISIBLE);
            binding.btnEditTopic.setOnClickListener(v -> openEditTopic());
        } else {
            binding.btnEditTopic.setVisibility(View.GONE);
        }

        // Ẩn nội dung, hiện loading khi vào
        binding.contentLayout.setVisibility(View.GONE);
        binding.downloadProgress.setVisibility(View.VISIBLE);
        binding.textDownloadStatus.setVisibility(View.VISIBLE);
        binding.textDownloadStatus.setText("Đang kiểm tra dữ liệu...");

        checkAndDownloadThenLoad();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data when returning from edit or learn
        if (binding.contentLayout.getVisibility() == View.VISIBLE) {
            loadData();
        }
    }

    /**
     * Kiểm tra Room DB — nếu chưa có từ thì tải từ Firestore về trước,
     * sau đó mới hiển thị nội dung.
     */
    private void checkAndDownloadThenLoad() {
        String key = isPersonal ? topicId : topicId.toLowerCase();
        executor.execute(() -> {
            int count = vocabularyDao.getCountByTopic(key);
            // Personal topic: không cần dịch, load thẳng hoặc download nếu chưa có
            // Public topic: cần kiểm tra có tiếng Việt chưa
            boolean needTranslate = !isPersonal && count > 0 && !vocabularyDao.hasVietnamese(key);
            android.util.Log.d("TopicDetail", "count=" + count + " isPersonal=" + isPersonal + " needTranslate=" + needTranslate);
            mainHandler.post(() -> {
                if (count == 0) {
                    // Chưa có dữ liệu → tải về
                    if (isPersonal) downloadPersonalTopic();
                    else downloadPublicTopic();
                } else if (needTranslate) {
                    // Public topic chưa có tiếng Việt → tải lại + dịch
                    downloadPublicTopic();
                } else {
                    // Đã có đủ dữ liệu → load thẳng
                    loadData();
                }
            });
        });
    }

    private void downloadPublicTopic() {
        binding.textDownloadStatus.setText("Đang tải bộ từ về...");
        db.collection("topics").document(topicId)
                .collection("vocabularies")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Vocabulary> vocabs = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Vocabulary v = doc.toObject(Vocabulary.class);
                        if (v != null) {
                            v.setVocabularyId(doc.getId());
                            v.setTopic(topicId.toLowerCase());
                            v.setVietnamese_translation(getVietnameseFromDoc(doc));
                            vocabs.add(v);
                        }
                    }
                    if (vocabs.isEmpty()) {
                        binding.downloadProgress.setVisibility(View.GONE);
                        binding.textDownloadStatus.setText("Bộ từ này chưa có dữ liệu.");
                        return;
                    }
                    // Lưu vào Room trước, rồi dịch tiếng Việt sau
                    executor.execute(() -> {
                        vocabularyDao.insertAll(vocabs);
                        mainHandler.post(() -> {
                            binding.textDownloadStatus.setText("Đang dịch nghĩa tiếng Việt...");
                            translateAndUpdate(vocabs);
                        });
                    });
                })
                .addOnFailureListener(e -> {
                    binding.downloadProgress.setVisibility(View.GONE);
                    binding.textDownloadStatus.setText("Lỗi tải dữ liệu: " + e.getMessage());
                });
    }

    private void downloadPersonalTopic() {
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (uid == null) { loadData(); return; }
        binding.textDownloadStatus.setText("Đang tải bộ từ cá nhân...");
        db.collection("users").document(uid)
                .collection("personal_topics").document(topicId)
                .collection("vocabularies")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Vocabulary> vocabs = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Vocabulary v = doc.toObject(Vocabulary.class);
                        if (v != null) {
                            v.setVocabularyId(doc.getId());
                            v.setTopic(topicId);
                            // Personal topic đã có vietnamese_translation từ lúc tạo
                            v.setVietnamese_translation(getVietnameseFromDoc(doc));
                            vocabs.add(v);
                        }
                    }
                    executor.execute(() -> {
                        if (!vocabs.isEmpty()) vocabularyDao.insertAll(vocabs);
                        mainHandler.post(this::loadData); // Không cần dịch
                    });
                })
                .addOnFailureListener(e -> loadData());
    }

    private void translateAndUpdate(List<Vocabulary> vocabs) {
        List<Vocabulary> needTranslation = new ArrayList<>();
        List<String> words = new ArrayList<>();
        List<String> defs = new ArrayList<>();
        for (Vocabulary v : vocabs) {
            String vi = v.getVietnamese_translation();
            if (vi == null || vi.isEmpty()) {
                needTranslation.add(v);
                words.add(v.getWord() != null ? v.getWord() : "");
                defs.add(v.getDefinition() != null ? v.getDefinition() : "");
            }
        }

        android.util.Log.d("TopicDetail", "translateAndUpdate: need=" + needTranslation.size() + "/" + vocabs.size());

        if (needTranslation.isEmpty()) {
            loadData();
            return;
        }

        // Chia thành batch nhỏ 10 từ để tránh timeout
        translateBatchChunked(needTranslation, words, defs, 0);
    }

    private void translateBatchChunked(List<Vocabulary> vocabs, List<String> words,
                                        List<String> defs, int offset) {
        int chunkSize = 10;
        int end = Math.min(offset + chunkSize, words.size());
        List<String> chunkWords = new ArrayList<>(words.subList(offset, end));
        List<String> chunkDefs = new ArrayList<>(defs.subList(offset, end));
        List<Vocabulary> chunkVocabs = new ArrayList<>(vocabs.subList(offset, end));

        binding.textDownloadStatus.setVisibility(View.VISIBLE);
        binding.downloadProgress.setVisibility(View.VISIBLE);
        mainHandler.post(() -> binding.textDownloadStatus.setText("Đang dịch tiếng Việt... (" + end + "/" + words.size() + ")"));

        GeminiTranslator translator = new GeminiTranslator();
        translator.translateBatch(chunkWords, chunkDefs, new GeminiTranslator.BatchCallback() {
            @Override
            public void onSuccess(List<String> translations) {
                android.util.Log.d("TopicDetail", "Chunk success: " + translations.size());
                executor.execute(() -> {
                    for (int i = 0; i < chunkVocabs.size() && i < translations.size(); i++) {
                        chunkVocabs.get(i).setVietnamese_translation(translations.get(i));
                        vocabularyDao.update(chunkVocabs.get(i));
                    }
                    mainHandler.postDelayed(() -> {
                        if (end < words.size()) {
                            // Delay 2s giữa các chunk để tránh rate limit
                            translateBatchChunked(vocabs, words, defs, end);
                        } else {
                            loadData();
                        }
                    }, 2000);
                });
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("TopicDetail", "Chunk error: " + error);
                if (error != null && (error.contains("QuotaExceeded") || error.contains("quota"))) {
                    mainHandler.post(() -> binding.textDownloadStatus.setText("Đang chờ quota... thử lại sau 20s"));
                    mainHandler.postDelayed(() ->
                        translateBatchChunked(vocabs, words, defs, offset), 20000);
                } else if (error != null && (error.contains("403") || error.contains("Forbidden") || error.contains("API_KEY"))) {
                    android.util.Log.e("TopicDetail", "API key restricted, skipping translation");
                    mainHandler.post(TopicDetailActivity.this::loadData);
                } else {
                    mainHandler.postDelayed(() -> {
                        if (end < words.size()) {
                            translateBatchChunked(vocabs, words, defs, end);
                        } else {
                            loadData();
                        }
                    }, 3000);
                }
            }
        });
    }

    /** Thử nhiều tên field khác nhau để lấy nghĩa tiếng Việt */
    private String getVietnameseFromDoc(DocumentSnapshot doc) {
        String[] candidates = {
            "vietnamese_translation", "vietnamese", "meaning_vi",
            "translation", "vi", "nghia"
        };
        for (String key : candidates) {
            String val = doc.getString(key);
            if (val != null && !val.isEmpty()) return val;
        }
        return null;
    }

    private void loadData() {
        String key = isPersonal ? topicId : topicId.toLowerCase();
        executor.execute(() -> {
            allWords = vocabularyDao.getVocabulariesByTopic(key);
            int total = allWords.size();
            int learned = vocabularyDao.getLearnedCountByTopic(key);
            int learning = vocabularyDao.getLearningCountByTopic(key);
            int newCount = total - learned - learning;

            mainHandler.post(() -> {
                binding.downloadProgress.setVisibility(View.GONE);
                binding.textDownloadStatus.setVisibility(View.GONE);
                binding.contentLayout.setVisibility(View.VISIBLE);

                updateStats(total, learned, learning, newCount);
                setupPieChart(learned, learning, newCount);
                setupFlashcardRecycler(allWords);
                updateLearnButton(newCount, learning);
            });
        });
    }

    private void updateStats(int total, int learned, int learning, int newCount) {
        binding.textTotalWords.setText(String.valueOf(total));
        binding.textLearnedWords.setText(String.valueOf(learned));
        binding.textLearningWords.setText(String.valueOf(learning));
        binding.textNewWords.setText(String.valueOf(newCount));
    }

    private void setupPieChart(int learned, int learning, int newCount) {
        int notLearned = learning + newCount; // Gộp đang học + chưa học = chưa học
        int total = learned + notLearned;
        
        if (total == 0) {
            binding.pieChart.setVisibility(View.GONE);
            return;
        }
        binding.pieChart.setVisibility(View.VISIBLE);

        List<PieEntry> entries = new ArrayList<>();
        if (learned > 0) entries.add(new PieEntry(learned, "Đã học"));
        if (notLearned > 0) entries.add(new PieEntry(notLearned, "Chưa học"));

        PieDataSet dataSet = new PieDataSet(entries, "");
        // Xanh cho đã học, vàng cho chưa học
        dataSet.setColors(
                Color.parseColor("#4CAF50"), // Xanh - đã học
                Color.parseColor("#FFC107")  // Vàng - chưa học
        );
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setSliceSpace(3f);

        PieData data = new PieData(dataSet);
        binding.pieChart.setData(data);
        binding.pieChart.setHoleRadius(50f);
        binding.pieChart.setTransparentCircleRadius(55f);
        binding.pieChart.setDrawHoleEnabled(true);
        binding.pieChart.setHoleColor(Color.WHITE);
        binding.pieChart.getDescription().setEnabled(false);
        binding.pieChart.getLegend().setEnabled(false); // Tắt legend mặc định, dùng legend tùy chỉnh
        binding.pieChart.setCenterText("Tiến độ");
        binding.pieChart.setCenterTextSize(14f);
        binding.pieChart.animateY(800);
        binding.pieChart.invalidate();
    }

    private void setupFlashcardRecycler(List<Vocabulary> words) {
        VocabCardAdapter adapter = new VocabCardAdapter(words, mediaPlayer);
        binding.recyclerFlashcards.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        binding.recyclerFlashcards.setAdapter(adapter);
    }

    private void updateLearnButton(int newCount, int learning) {
        if (newCount > 0 || learning > 0) {
            binding.btnLearnNew.setEnabled(true);
            binding.btnLearnNew.setText(newCount > 0 ? "Học từ mới (" + newCount + ")" : "Tiếp tục học (" + learning + ")");
        } else {
            binding.btnLearnNew.setEnabled(false);
            binding.btnLearnNew.setText("Đã học hết! 🎉");
        }
    }

    private void confirmDelete() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Xóa bộ từ?")
                .setMessage("Toàn bộ từ vựng và tiến độ học sẽ bị xóa. Không thể hoàn tác.")
                .setPositiveButton("Xóa", (d, w) -> deleteTopic())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteTopic() {
        binding.downloadProgress.setVisibility(View.VISIBLE);
        binding.textDownloadStatus.setVisibility(View.VISIBLE);
        binding.textDownloadStatus.setText("Đang xóa...");
        binding.contentLayout.setVisibility(View.GONE);

        String key = isPersonal ? topicId : topicId.toLowerCase();

        // Xóa khỏi Room DB
        executor.execute(() -> {
            vocabularyDao.deleteByTopic(key);
            mainHandler.post(() -> {
                // Nếu là personal topic → xóa luôn trên Firestore
                if (isPersonal) {
                    String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
                    if (uid != null) {
                        db.collection("users").document(uid)
                                .collection("personal_topics").document(topicId)
                                .delete()
                                .addOnCompleteListener(t -> finish());
                    } else {
                        finish();
                    }
                } else {
                    // Public topic → chỉ xóa local, không xóa Firestore
                    finish();
                }
            });
        });
    }

    private void startLearning() {
        Intent intent = new Intent(this, TopicWordPickActivity.class);
        intent.putExtra("topic_id", topicId);
        intent.putExtra("topic_title", topicTitle);
        intent.putExtra("is_personal_topic", isPersonal);
        startActivity(intent);
    }

    private void openFlashcard() {
        Intent intent = new Intent(this, TopicFlashcardActivity.class);
        intent.putExtra("topic_id", topicId);
        intent.putExtra("topic_title", topicTitle);
        intent.putExtra("is_personal_topic", isPersonal);
        startActivity(intent);
    }

    private void openEditTopic() {
        Intent intent = new Intent(this, TopicEditActivity.class);
        intent.putExtra("topic_id", topicId);
        intent.putExtra("topic_title", topicTitle);
        intent.putExtra("is_personal_topic", isPersonal);
        startActivityForResult(intent, REQUEST_EDIT_TOPIC);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT_TOPIC && resultCode == RESULT_OK) {
            // Reload data after editing
            hasChanges = true;
            loadData();
        }
    }

    private static final int REQUEST_EDIT_TOPIC = 1001;

    @Override
    public void finish() {
        if (hasChanges) {
            setResult(RESULT_OK);
        }
        super.finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) { mediaPlayer.release(); mediaPlayer = null; }
    }

    // --- VocabCard Adapter (thay thế FlashcardHorizontalAdapter) ---
    static class VocabCardAdapter extends RecyclerView.Adapter<VocabCardAdapter.VH> {
        private final List<Vocabulary> words;
        private final MediaPlayer player;

        VocabCardAdapter(List<Vocabulary> words, MediaPlayer player) {
            this.words = words;
            this.player = player;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            com.example.vocabmaster.databinding.ItemVocabCardBinding b =
                    com.example.vocabmaster.databinding.ItemVocabCardBinding.inflate(
                            LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(b);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(words.get(position), player);
        }

        @Override
        public int getItemCount() { return words.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final com.example.vocabmaster.databinding.ItemVocabCardBinding b;

            VH(com.example.vocabmaster.databinding.ItemVocabCardBinding b) {
                super(b.getRoot());
                this.b = b;
            }

            void bind(Vocabulary v, MediaPlayer player) {
                b.textWord.setText(v.getWord() != null ? v.getWord() : "");
                b.textPhonetic.setText(v.getPhonetic() != null ? v.getPhonetic() : "");
                b.textDefinition.setText(v.getDefinition() != null ? v.getDefinition() : "");

                // Tiếng Việt
                String vi = v.getVietnamese_translation();
                if (vi != null && !vi.isEmpty()) {
                    b.textVietnamese.setVisibility(View.VISIBLE);
                    b.textVietnamese.setText("🇻🇳 " + vi);
                } else {
                    b.textVietnamese.setVisibility(View.GONE);
                }

                // Ví dụ
                String ex = v.getExample_sentence();
                if (ex != null && !ex.isEmpty()) {
                    b.textExample.setVisibility(View.VISIBLE);
                    b.textExample.setText("\"" + ex + "\"");
                } else {
                    b.textExample.setVisibility(View.GONE);
                }

                // Status
                switch (v.getLearnStatus()) {
                    case 2: b.textStatus.setText("✓ Đã học"); b.textStatus.setTextColor(Color.parseColor("#4CAF50")); break;
                    case 1: b.textStatus.setText("⟳ Đang học"); b.textStatus.setTextColor(Color.parseColor("#FF9800")); break;
                    default: b.textStatus.setText("● Mới"); b.textStatus.setTextColor(Color.parseColor("#9E9E9E")); break;
                }

                // Ảnh
                String imgUrl = v.getImage_url();
                if (imgUrl != null && !imgUrl.isEmpty() && !imgUrl.contains("defaultImage")) {
                    b.imgVocab.setVisibility(View.VISIBLE);
                    Glide.with(b.getRoot()).load(imgUrl)
                            .placeholder(R.drawable.macdinh).error(R.drawable.macdinh)
                            .centerCrop().into(b.imgVocab);
                } else {
                    b.imgVocab.setVisibility(View.GONE);
                }

                // Audio
                String audioUrl = v.getAnyAudioUrl();
                if (audioUrl != null && !audioUrl.isEmpty()) {
                    b.btnAudio.setVisibility(View.VISIBLE);
                    b.btnAudio.setOnClickListener(vw -> {
                        try {
                            player.reset();
                            player.setDataSource(audioUrl);
                            player.prepareAsync();
                            player.setOnPreparedListener(MediaPlayer::start);
                        } catch (IOException e) { e.printStackTrace(); }
                    });
                } else {
                    b.btnAudio.setVisibility(View.GONE);
                }
            }
        }
    }
}
