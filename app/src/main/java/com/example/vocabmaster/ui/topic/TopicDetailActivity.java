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
import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.R;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.local.VocabularyDao;
import com.example.vocabmaster.data.model.Vocabulary;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTopicDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        topicId = getIntent().getStringExtra("topic_id");
        topicTitle = getIntent().getStringExtra("topic_title");
        isPersonal = getIntent().getBooleanExtra("is_personal_topic", false);

        binding.toolbar.setTitle(topicTitle != null ? topicTitle : "Topic");
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.inflateMenu(R.menu.menu_topic_detail);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_delete_topic) {
                confirmDelete();
                return true;
            }
            return false;
        });

        vocabularyDao = AppDatabase.getDatabase(this).vocabularyDao();
        db = FirebaseFirestore.getInstance();
        mediaPlayer = new MediaPlayer();

        binding.btnLearnNew.setOnClickListener(v -> startLearning());

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
        // Refresh stats sau khi học xong
        String key = isPersonal ? topicId : topicId.toLowerCase();
        executor.execute(() -> {
            if (allWords.isEmpty()) return;
            int total = allWords.size();
            int learned = vocabularyDao.getLearnedCountByTopic(key);
            int learning = vocabularyDao.getLearningCountByTopic(key);
            int newCount = total - learned - learning;
            mainHandler.post(() -> {
                updateStats(total, learned, learning, newCount);
                setupPieChart(learned, learning, newCount);
                updateLearnButton(newCount, learning);
            });
        });
    }

    /**
     * Kiểm tra Room DB — nếu chưa có từ thì tải từ Firestore về trước,
     * sau đó mới hiển thị nội dung.
     */
    private void checkAndDownloadThenLoad() {
        String key = isPersonal ? topicId : topicId.toLowerCase();
        executor.execute(() -> {
            int count = vocabularyDao.getCountByTopic(key);
            mainHandler.post(() -> {
                if (count > 0) {
                    // Đã có dữ liệu → load thẳng
                    loadData();
                } else if (isPersonal) {
                    // Personal topic → load từ personal_topics collection
                    downloadPersonalTopic();
                } else {
                    // Public topic → tải từ Firestore topics collection
                    downloadPublicTopic();
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
                            vocabs.add(v);
                        }
                    }
                    if (vocabs.isEmpty()) {
                        binding.downloadProgress.setVisibility(View.GONE);
                        binding.textDownloadStatus.setText("Bộ từ này chưa có dữ liệu.");
                        return;
                    }
                    executor.execute(() -> {
                        vocabularyDao.insertAll(vocabs);
                        mainHandler.post(this::loadData);
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
                            vocabs.add(v);
                        }
                    }
                    executor.execute(() -> {
                        if (!vocabs.isEmpty()) vocabularyDao.insertAll(vocabs);
                        mainHandler.post(this::loadData);
                    });
                })
                .addOnFailureListener(e -> loadData());
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
        if (learned == 0 && learning == 0 && newCount == 0) {
            binding.pieChart.setVisibility(View.GONE);
            return;
        }
        binding.pieChart.setVisibility(View.VISIBLE);

        List<PieEntry> entries = new ArrayList<>();
        if (learned > 0) entries.add(new PieEntry(learned, "Đã học"));
        if (learning > 0) entries.add(new PieEntry(learning, "Đang học"));
        if (newCount > 0) entries.add(new PieEntry(newCount, "Chưa học"));

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(
                Color.parseColor("#4CAF50"),
                Color.parseColor("#FF9800"),
                Color.parseColor("#E0E0E0")
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
        binding.pieChart.getLegend().setEnabled(true);
        binding.pieChart.setCenterText("Tiến độ");
        binding.pieChart.setCenterTextSize(14f);
        binding.pieChart.animateY(800);
        binding.pieChart.invalidate();
    }

    private void setupFlashcardRecycler(List<Vocabulary> words) {
        FlashcardHorizontalAdapter adapter = new FlashcardHorizontalAdapter(words, mediaPlayer);
        binding.recyclerFlashcards.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) { mediaPlayer.release(); mediaPlayer = null; }
    }

    // --- Inner Adapter ---
    static class FlashcardHorizontalAdapter extends RecyclerView.Adapter<FlashcardHorizontalAdapter.VH> {
        private final List<Vocabulary> words;
        private final MediaPlayer player;

        FlashcardHorizontalAdapter(List<Vocabulary> words, MediaPlayer player) {
            this.words = words;
            this.player = player;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemFlashcardHorizontalBinding b = ItemFlashcardHorizontalBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(b);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Vocabulary v = words.get(position);
            holder.bind(v, player);
        }

        @Override
        public int getItemCount() { return words.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final ItemFlashcardHorizontalBinding b;
            boolean isFlipped = false;

            VH(ItemFlashcardHorizontalBinding b) {
                super(b.getRoot());
                this.b = b;
            }

            void bind(Vocabulary v, MediaPlayer player) {
                isFlipped = false;
                b.textWord.setText(v.getWord());
                b.textPhonetic.setText(v.getPhonetic() != null ? v.getPhonetic() : "");
                b.textDefinition.setText(v.getDefinition() != null ? v.getDefinition() : "");
                b.textExample.setText(v.getExample_sentence() != null ? v.getExample_sentence() : "");

                // Status badge
                switch (v.getLearnStatus()) {
                    case 2: b.textStatus.setText("✓ Đã học"); b.textStatus.setTextColor(Color.parseColor("#4CAF50")); break;
                    case 1: b.textStatus.setText("⟳ Đang học"); b.textStatus.setTextColor(Color.parseColor("#FF9800")); break;
                    default: b.textStatus.setText("● Mới"); b.textStatus.setTextColor(Color.parseColor("#9E9E9E")); break;
                }

                // Show front by default
                b.cardFront.setVisibility(View.VISIBLE);
                b.cardBack.setVisibility(View.GONE);

                // Flip on click
                b.getRoot().setOnClickListener(vw -> {
                    isFlipped = !isFlipped;
                    b.cardFront.setVisibility(isFlipped ? View.GONE : View.VISIBLE);
                    b.cardBack.setVisibility(isFlipped ? View.VISIBLE : View.GONE);
                });

                // Audio
                String audioUrl = v.getAnyAudioUrl();
                b.btnAudio.setVisibility(audioUrl != null && !audioUrl.isEmpty() ? View.VISIBLE : View.GONE);
                b.btnAudio.setOnClickListener(vw -> {
                    if (audioUrl == null || audioUrl.isEmpty()) return;
                    try {
                        player.reset();
                        player.setDataSource(audioUrl);
                        player.prepareAsync();
                        player.setOnPreparedListener(MediaPlayer::start);
                    } catch (IOException e) { e.printStackTrace(); }
                });

                // Image
                if (v.getImage_url() != null && !v.getImage_url().isEmpty()) {
                    b.imgWord.setVisibility(View.VISIBLE);
                    Glide.with(b.getRoot()).load(v.getImage_url()).into(b.imgWord);
                } else {
                    b.imgWord.setVisibility(View.GONE);
                }
            }
        }
    }
}
