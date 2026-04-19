package com.example.vocabmaster.ui.topic;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.local.VocabularyDao;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.databinding.ActivityTopicWordPickBinding;
import com.example.vocabmaster.databinding.ItemWordPickBinding;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TopicWordPickActivity extends AppCompatActivity {

    private static final int MIN_PICK = 5;
    private static final int MAX_PICK = 10;
    private static final int SUGGEST_COUNT = 10; // random 10 từ để user chọn

    private ActivityTopicWordPickBinding binding;
    private VocabularyDao vocabularyDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String topicId;
    private String topicTitle;
    private boolean isPersonal;

    private WordPickAdapter adapter;
    private final Set<String> selectedIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTopicWordPickBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        topicId = getIntent().getStringExtra("topic_id");
        topicTitle = getIntent().getStringExtra("topic_title");
        isPersonal = getIntent().getBooleanExtra("is_personal_topic", false);

        vocabularyDao = AppDatabase.getDatabase(this).vocabularyDao();

        binding.toolbar.setTitle("Chọn từ để học");
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.btnStartLearn.setEnabled(false);
        binding.btnStartLearn.setText("Chọn " + MIN_PICK + "-" + MAX_PICK + " từ để bắt đầu");
        binding.btnStartLearn.setOnClickListener(v -> startLearning());

        binding.btnSelectAll.setOnClickListener(v -> adapter.selectAll());
        binding.btnShuffle.setOnClickListener(v -> loadWords()); // reload random batch

        setupRecycler();
        loadWords();
    }

    private void setupRecycler() {
        adapter = new WordPickAdapter(selectedIds, count -> {
            updateStartButton(count);
        });
        binding.recyclerWords.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerWords.setAdapter(adapter);
    }

    private void loadWords() {
        String key = isPersonal ? topicId : topicId.toLowerCase();
        binding.progressBar.setVisibility(View.VISIBLE);
        selectedIds.clear();

        executor.execute(() -> {
            // Lấy TẤT CẢ từ chưa học (status=0)
            List<Vocabulary> newWords = vocabularyDao.getNewWordsByTopic(key);
            // + đang học (status=1)
            List<Vocabulary> learningWords = vocabularyDao.getLearnedWordsByTopic(key);
            List<Vocabulary> candidates = new ArrayList<>(newWords);
            for (Vocabulary v : learningWords) {
                if (v.getLearnStatus() == 1) candidates.add(v);
            }
            // Shuffle để hiển thị ngẫu nhiên mỗi lần
            Collections.shuffle(candidates);
            // Copy ra list mới tránh ConcurrentModificationException
            List<Vocabulary> result = new ArrayList<>(candidates);

            mainHandler.post(() -> {
                binding.progressBar.setVisibility(View.GONE);
                if (result.isEmpty()) {
                    binding.textEmpty.setVisibility(View.VISIBLE);
                    binding.recyclerWords.setVisibility(View.GONE);
                    binding.btnStartLearn.setEnabled(false);
                    return;
                }
                binding.textEmpty.setVisibility(View.GONE);
                binding.recyclerWords.setVisibility(View.VISIBLE);
                binding.textSubtitle.setText(
                    result.size() + " từ chưa học — chọn " + MIN_PICK + "–" + MAX_PICK + " từ để bắt đầu");
                adapter.setWords(result);
                updateStartButton(0);
            });
        });
    }

    private void updateStartButton(int count) {
        if (count < MIN_PICK) {
            binding.btnStartLearn.setEnabled(false);
            binding.btnStartLearn.setText("Chọn thêm " + (MIN_PICK - count) + " từ nữa");
        } else if (count > MAX_PICK) {
            binding.btnStartLearn.setEnabled(false);
            binding.btnStartLearn.setText("Chọn tối đa " + MAX_PICK + " từ (đang chọn " + count + ")");
        } else {
            binding.btnStartLearn.setEnabled(true);
            binding.btnStartLearn.setText("Bắt đầu học " + count + " từ →");
        }
    }

    private void startLearning() {
        List<Vocabulary> selected = adapter.getSelectedWords();
        if (selected.isEmpty()) return;

        String json = new Gson().toJson(selected);
        Intent intent = new Intent(this, TopicLearnActivity.class);
        intent.putExtra("topic_id", topicId);
        intent.putExtra("topic_title", topicTitle);
        intent.putExtra("is_personal_topic", isPersonal);
        intent.putExtra("selected_words_json", json);
        startActivity(intent);
        finish(); // không cho back về màn chọn từ khi đang học
    }

    // ---- Adapter ----
    static class WordPickAdapter extends RecyclerView.Adapter<WordPickAdapter.VH> {

        interface OnSelectionChanged { void onChange(int count); }

        private List<Vocabulary> words = new ArrayList<>();
        private final Set<String> selectedIds;
        private final OnSelectionChanged listener;

        WordPickAdapter(Set<String> selectedIds, OnSelectionChanged listener) {
            this.selectedIds = selectedIds;
            this.listener = listener;
        }

        void setWords(List<Vocabulary> list) {
            words = list;
            selectedIds.clear();
            notifyDataSetChanged();
            listener.onChange(0);
        }

        void selectAll() {
            selectedIds.clear();
            int limit = Math.min(MAX_PICK, words.size());
            for (int i = 0; i < limit; i++) selectedIds.add(words.get(i).getVocabularyId());
            notifyDataSetChanged();
            listener.onChange(selectedIds.size());
        }

        List<Vocabulary> getSelectedWords() {
            List<Vocabulary> result = new ArrayList<>();
            for (Vocabulary v : words) {
                if (selectedIds.contains(v.getVocabularyId())) result.add(v);
            }
            return result;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemWordPickBinding b = ItemWordPickBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(b);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Vocabulary v = words.get(position);
            boolean checked = selectedIds.contains(v.getVocabularyId());
            holder.bind(v, checked, id -> {
                if (selectedIds.contains(id)) {
                    selectedIds.remove(id);
                } else {
                    selectedIds.add(id);
                }
                listener.onChange(selectedIds.size());
                notifyItemChanged(position);
            });
        }

        @Override
        public int getItemCount() { return words.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final ItemWordPickBinding b;
            VH(ItemWordPickBinding b) { super(b.getRoot()); this.b = b; }

            interface OnToggle { void toggle(String id); }

            void bind(Vocabulary v, boolean checked, OnToggle toggle) {
                b.textWord.setText(v.getWord());
                b.textDefinition.setText(v.getDefinition() != null ? v.getDefinition() : "");
                b.textPhonetic.setText(v.getPhonetic() != null ? v.getPhonetic() : "");
                b.checkbox.setChecked(checked);

                // Status chip
                if (v.getLearnStatus() == 1) {
                    b.chipStatus.setVisibility(View.VISIBLE);
                    b.chipStatus.setText("Đang học");
                } else {
                    b.chipStatus.setVisibility(View.GONE);
                }

                b.getRoot().setOnClickListener(vw -> toggle.toggle(v.getVocabularyId()));
                b.checkbox.setOnClickListener(vw -> toggle.toggle(v.getVocabularyId()));
            }
        }
    }
}
