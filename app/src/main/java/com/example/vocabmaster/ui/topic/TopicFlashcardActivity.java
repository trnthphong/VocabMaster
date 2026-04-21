package com.example.vocabmaster.ui.topic;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.local.VocabularyDao;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.databinding.ActivityTopicFlashcardBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TopicFlashcardActivity extends AppCompatActivity {

    private ActivityTopicFlashcardBinding binding;
    private VocabularyDao vocabularyDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String topicId;
    private String topicTitle;
    private boolean isPersonal;
    private TopicFlashcardAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTopicFlashcardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        topicId = getIntent().getStringExtra("topic_id");
        topicTitle = getIntent().getStringExtra("topic_title");
        isPersonal = getIntent().getBooleanExtra("is_personal_topic", false);

        binding.toolbar.setTitle(topicTitle != null ? topicTitle : "Flashcard");
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        vocabularyDao = AppDatabase.getDatabase(this).vocabularyDao();

        setupViewPager();
        loadFlashcards();
    }

    private void setupViewPager() {
        adapter = new TopicFlashcardAdapter();
        binding.viewPagerFlashcards.setAdapter(adapter);
        binding.viewPagerFlashcards.setOffscreenPageLimit(2);

        binding.viewPagerFlashcards.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateCardCount(position);
            }
        });
    }

    private void loadFlashcards() {
        String key = isPersonal ? topicId : topicId.toLowerCase();
        binding.progressBar.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            List<Vocabulary> vocabularies = vocabularyDao.getVocabulariesByTopic(key);
            List<Vocabulary> result = new ArrayList<>(vocabularies);

            mainHandler.post(() -> {
                binding.progressBar.setVisibility(View.GONE);
                if (result.isEmpty()) {
                    binding.textEmpty.setVisibility(View.VISIBLE);
                    binding.viewPagerFlashcards.setVisibility(View.GONE);
                } else {
                    binding.textEmpty.setVisibility(View.GONE);
                    binding.viewPagerFlashcards.setVisibility(View.VISIBLE);
                    adapter.submitList(result);
                    updateCardCount(0);
                }
            });
        });
    }

    private void updateCardCount(int position) {
        int total = adapter.getItemCount();
        binding.textCardCount.setText((position + 1) + " / " + total);
    }
}
