package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.local.VocabularyDao;
import com.example.vocabmaster.data.model.Topic;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.databinding.ActivityAllTopicsBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AllTopicsActivity extends AppCompatActivity {
    private ActivityAllTopicsBinding binding;
    private FirebaseFirestore db;
    private TopicAdapter adapter;
    private VocabularyDao vocabularyDao;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private List<Topic> topicList = new ArrayList<>();
    private static final int REQUEST_TOPIC_DETAIL = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAllTopicsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        vocabularyDao = AppDatabase.getDatabase(this).vocabularyDao();
        
        setupRecyclerView();
        loadAllTopics();

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        
        binding.fabCreateTopic.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateTopicActivity.class);
            startActivity(intent);
        });
    }

    private void setupRecyclerView() {
        adapter = new TopicAdapter(new TopicAdapter.OnTopicClickListener() {
            @Override
            public void onTopicClick(Topic topic) {
                if (topic.isDownloaded()) {
                    navigateToTopic(topic);
                } else {
                    UiFeedback.performHaptic(AllTopicsActivity.this, 50);
                    showDownloadRequiredDialog(topic);
                }
            }

            @Override
            public void onDownloadClick(Topic topic) {
                downloadVocabularySet(topic);
            }
        });

        binding.recyclerAllTopics.setLayoutManager(new GridLayoutManager(this, 2));
        binding.recyclerAllTopics.setAdapter(adapter);
    }

    private void showDownloadRequiredDialog(Topic topic) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Cần tải dữ liệu")
                .setMessage("Bạn cần tải xuống bộ từ vựng \"" + topic.getName() + "\" trước khi bắt đầu học. Tải ngay?")
                .setPositiveButton("Tải về", (dialog, which) -> downloadVocabularySet(topic))
                .setNegativeButton("Để sau", null)
                .show();
    }

    private void navigateToTopic(Topic topic) {
        Intent intent = new Intent(AllTopicsActivity.this, com.example.vocabmaster.ui.topic.TopicDetailActivity.class);
        intent.putExtra("topic_id", topic.getId());
        intent.putExtra("topic_title", topic.getName());
        intent.putExtra("is_personal_topic", false);
        startActivityForResult(intent, REQUEST_TOPIC_DETAIL);
    }

    private void loadAllTopics() {
        binding.progressBar.setVisibility(View.VISIBLE);
        db.collection("topics")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        binding.progressBar.setVisibility(View.GONE);
                        return;
                    }
                    if (snapshot != null) {
                        topicList.clear();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Topic t = doc.toObject(Topic.class);
                            if (t != null) {
                                t.setId(doc.getId());
                                checkIfDownloaded(t);
                                topicList.add(t);
                            }
                        }
                        adapter.setTopics(topicList);
                        binding.progressBar.setVisibility(View.GONE);
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_TOPIC_DETAIL && resultCode == RESULT_OK) {
            // Refresh word counts for all topics
            refreshTopicWordCounts();
        }
    }

    private void refreshTopicWordCounts() {
        for (Topic topic : topicList) {
            executorService.execute(() -> {
                String key = topic.getId().toLowerCase();
                int count = vocabularyDao.getCountByTopic(key);
                runOnUiThread(() -> {
                    topic.setWordCount(count);
                    adapter.notifyDataSetChanged();
                });
            });
        }
    }

    private void checkIfDownloaded(Topic topic) {
        executorService.execute(() -> {
            int count = vocabularyDao.getCountByTopic(topic.getId().toLowerCase());
            if (count > 0) {
                runOnUiThread(() -> {
                    topic.setDownloaded(true);
                    adapter.notifyDataSetChanged();
                });
            }
        });
    }

    private void downloadVocabularySet(Topic topic) {
        Toast.makeText(this, "Đang tải dữ liệu cho: " + topic.getName(), Toast.LENGTH_SHORT).show();
        
        db.collection("topics")
                .document(topic.getId())
                .collection("vocabularies")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Vocabulary> vocabs = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot) {
                        Vocabulary v = doc.toObject(Vocabulary.class);
                        if (v != null) {
                            v.setVocabularyId(doc.getId());
                            v.setTopic(topic.getId().toLowerCase());
                            vocabs.add(v);
                        }
                    }

                    if (!vocabs.isEmpty()) {
                        executorService.execute(() -> {
                            vocabularyDao.insertAll(vocabs);
                            runOnUiThread(() -> {
                                topic.setDownloaded(true);
                                adapter.notifyDataSetChanged();
                                Toast.makeText(AllTopicsActivity.this, "Tải về thành công " + vocabs.size() + " từ!", Toast.LENGTH_SHORT).show();
                            });
                        });
                    } else {
                        Toast.makeText(this, "Chủ đề này hiện chưa có từ vựng.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi kết nối: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
