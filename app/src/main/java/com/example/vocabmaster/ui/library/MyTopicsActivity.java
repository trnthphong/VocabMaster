package com.example.vocabmaster.ui.library;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.local.VocabularyDao;
import com.example.vocabmaster.data.model.Topic;
import com.example.vocabmaster.databinding.ActivityMyTopicsBinding;
import com.example.vocabmaster.ui.home.CreateTopicActivity;
import com.example.vocabmaster.ui.home.TopicAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyTopicsActivity extends AppCompatActivity {

    private ActivityMyTopicsBinding binding;
    private FirebaseFirestore db;
    private VocabularyDao vocabularyDao;
    private TopicAdapter personalAdapter;
    private TopicAdapter downloadedAdapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMyTopicsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        vocabularyDao = AppDatabase.getDatabase(this).vocabularyDao();

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.fabCreateTopic.setOnClickListener(v ->
                startActivity(new Intent(this, CreateTopicActivity.class)));

        setupAdapters();
        loadPersonalTopics();
        loadDownloadedTopics();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPersonalTopics();
        loadDownloadedTopics();
    }

    private void setupAdapters() {
        TopicAdapter.OnTopicClickListener personalListener = new TopicAdapter.OnTopicClickListener() {
            @Override
            public void onTopicClick(Topic topic) {
                Intent intent = new Intent(MyTopicsActivity.this, com.example.vocabmaster.ui.topic.TopicDetailActivity.class);
                intent.putExtra("topic_id", topic.getId());
                intent.putExtra("topic_title", topic.getName());
                intent.putExtra("is_personal_topic", true);
                startActivity(intent);
            }
            @Override
            public void onDownloadClick(Topic topic) { /* personal topics are always available */ }
        };

        TopicAdapter.OnTopicClickListener downloadedListener = new TopicAdapter.OnTopicClickListener() {
            @Override
            public void onTopicClick(Topic topic) {
                Intent intent = new Intent(MyTopicsActivity.this, com.example.vocabmaster.ui.topic.TopicDetailActivity.class);
                intent.putExtra("topic_id", topic.getId());
                intent.putExtra("topic_title", topic.getName());
                intent.putExtra("is_personal_topic", false);
                startActivity(intent);
            }
            @Override
            public void onDownloadClick(Topic topic) { /* already downloaded */ }
        };

        personalAdapter = new TopicAdapter(personalListener);
        downloadedAdapter = new TopicAdapter(downloadedListener);

        binding.recyclerPersonalTopics.setLayoutManager(new GridLayoutManager(this, 2));
        binding.recyclerPersonalTopics.setAdapter(personalAdapter);

        binding.recyclerDownloadedTopics.setLayoutManager(new GridLayoutManager(this, 2));
        binding.recyclerDownloadedTopics.setAdapter(downloadedAdapter);
    }

    private void loadPersonalTopics() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        db.collection("users").document(uid)
                .collection("personal_topics")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Topic> topics = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Topic t = new Topic();
                        t.setId(doc.getId());
                        t.setName(doc.getString("name"));
                        t.setImageUrl(doc.getString("imageUrl"));
                        Long wc = doc.getLong("word_count");
                        t.setWordCount(wc != null ? wc.intValue() : 0);
                        t.setDownloaded(true);
                        topics.add(t);
                    }
                    personalAdapter.setTopics(topics);
                    binding.sectionPersonal.setVisibility(topics.isEmpty() ? View.GONE : View.VISIBLE);
                    binding.textEmptyPersonal.setVisibility(topics.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    private void loadDownloadedTopics() {
        // Load all topics from Firestore, then filter by what's in Room DB
        db.collection("topics").get().addOnSuccessListener(snapshot -> {
            List<Topic> allTopics = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                Topic t = doc.toObject(Topic.class);
                if (t != null) {
                    t.setId(doc.getId());
                    allTopics.add(t);
                }
            }
            // Check which ones are downloaded in Room
            executor.execute(() -> {
                List<Topic> downloaded = new ArrayList<>();
                for (Topic t : allTopics) {
                    int count = vocabularyDao.getCountByTopic(t.getId().toLowerCase());
                    if (count > 0) {
                        t.setDownloaded(true);
                        t.setWordCount(count);
                        downloaded.add(t);
                    }
                }
                mainHandler.post(() -> {
                    downloadedAdapter.setTopics(downloaded);
                    binding.sectionDownloaded.setVisibility(downloaded.isEmpty() ? View.GONE : View.VISIBLE);
                    binding.textEmptyDownloaded.setVisibility(downloaded.isEmpty() ? View.VISIBLE : View.GONE);
                });
            });
        });
    }
}
