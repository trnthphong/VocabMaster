package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.data.repository.CourseRepository;
import com.example.vocabmaster.databinding.ActivityTopicWordListBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.example.vocabmaster.ui.study.StudyActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class TopicWordListActivity extends AppCompatActivity {
    private static final String TAG = "TopicWordListActivity";
    private ActivityTopicWordListBinding binding;
    private WordAdapter adapter;
    private String topic;
    private String displayTitle;
    private String langCode;
    private String selectedLevel; // Thêm trường trình độ
    private CourseRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTopicWordListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new CourseRepository(getApplication());
        topic = getIntent().getStringExtra("topic");
        displayTitle = getIntent().getStringExtra("display_title");
        langCode = getIntent().getStringExtra("lang_code");
        selectedLevel = getIntent().getStringExtra("selected_level"); // Nhận từ JourneySetup

        if (langCode != null) {
            if (langCode.contains("Anh")) langCode = "en";
            else if (langCode.contains("Nga")) langCode = "ru";
        }

        Log.d(TAG, "onCreate: topic=" + topic + ", level=" + selectedLevel);

        binding.textHeaderTitle.setText(displayTitle + (selectedLevel != null ? " (" + selectedLevel + ")" : ""));
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnChangeLang.setOnClickListener(v -> {
            UiFeedback.performHaptic(this, 10);
            Intent intent = new Intent(this, JourneySetupActivity.class);
            intent.putExtra("is_change_only", true);
            intent.putExtra("selected_topic", topic);
            intent.putExtra("display_title", displayTitle);
            startActivity(intent);
        });

        setupRecyclerView();
        
        // Nếu chưa có level từ intent, thử lấy từ profile user
        if (selectedLevel == null) {
            fetchUserLevelAndLoad();
        } else {
            loadWords();
        }
    }

    private void fetchUserLevelAndLoad() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            selectedLevel = doc.getString("proficiencyLevel");
                            Log.d(TAG, "User level from profile: " + selectedLevel);
                        }
                        loadWords();
                    })
                    .addOnFailureListener(e -> loadWords());
        } else {
            loadWords();
        }
    }

    private void setupRecyclerView() {
        adapter = new WordAdapter(new WordAdapter.OnWordClickListener() {
            @Override
            public void onWordClick(Vocabulary vocab) {
                Intent intent = new Intent(TopicWordListActivity.this, StudyActivity.class);
                intent.putExtra("topic", topic);
                intent.putExtra("word_id", vocab.getVocabularyId());
                intent.putExtra("lesson_title", vocab.getWord());
                startActivity(intent);
            }

            @Override
            public void onAddClick(Vocabulary vocab) {
                saveToLibrary(vocab);
            }
        });
        binding.recyclerWords.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerWords.setAdapter(adapter);
    }

    private void saveToLibrary(Vocabulary v) {
        Flashcard card = new Flashcard(v.getWord(), v.getDefinition());
        card.setImageUrl(v.getImageUrl());
        card.setAudioUrl(v.getAudioUrl());
        card.setPhonetic(v.getPhonetic());
        card.setExample(v.getExampleSentence());
        card.setTag(displayTitle != null ? displayTitle : topic);
        
        repository.addPersonalFlashcard(card);
        
        UiFeedback.showSnack(binding.getRoot(), "Đã thêm '" + v.getWord() + "' vào thư viện");
        UiFeedback.performHaptic(this, 10);
    }

    private void loadWords() {
        if (topic == null || langCode == null) {
            binding.layoutEmpty.setVisibility(View.VISIBLE);
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.layoutEmpty.setVisibility(View.GONE);
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String collectionPath = "ru".equals(langCode) ? "russian_vocabularies" : "vocabularies";
        
        Log.d(TAG, "Querying " + collectionPath + " | Topic: " + topic + " | Level: " + selectedLevel);

        Query query = db.collection(collectionPath)
                .whereEqualTo("topic", topic.toLowerCase());

        // CHỈ LỌC THEO TRÌNH ĐỘ NẾU selectedLevel KHÔNG NULL
        if (selectedLevel != null && !selectedLevel.isEmpty()) {
            query = query.whereEqualTo("cefr", selectedLevel);
        }

        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Vocabulary> words = new ArrayList<>();
                    Log.d(TAG, "Documents found with criteria: " + queryDocumentSnapshots.size());
                    
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        try {
                            Vocabulary v = doc.toObject(Vocabulary.class);
                            if (v != null) {
                                v.setVocabularyId(doc.getId());
                                words.add(v);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing vocabulary: " + doc.getId(), e);
                        }
                    }
                    
                    binding.progressBar.setVisibility(View.GONE);
                    adapter.submitList(words);
                    binding.textCount.setText(words.size() + " từ vựng phù hợp");
                    
                    if (words.isEmpty()) {
                        binding.layoutEmpty.setVisibility(View.VISIBLE);
                    } else {
                        binding.layoutEmpty.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading words", e);
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private static class WordAdapter extends RecyclerView.Adapter<WordAdapter.ViewHolder> {
        private List<Vocabulary> words = new ArrayList<>();
        private final OnWordClickListener listener;

        public interface OnWordClickListener {
            void onWordClick(Vocabulary vocab);
            void onAddClick(Vocabulary vocab);
        }

        public WordAdapter(OnWordClickListener listener) {
            this.listener = listener;
        }

        public void submitList(List<Vocabulary> newList) {
            this.words = newList;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_topic_word, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Vocabulary vocab = words.get(position);
            holder.wordText.setText(vocab.getWord());
            
            // Hiển thị thêm CEFR Level nếu có
            String cefr = vocab.getCefr() != null ? "[" + vocab.getCefr() + "] " : "";
            String pos = vocab.getPartOfSpeech() != null ? "(" + vocab.getPartOfSpeech() + ") " : "";
            holder.defText.setText(cefr + pos + (vocab.getDefinition() != null ? vocab.getDefinition() : ""));
            
            holder.itemView.setOnClickListener(v -> listener.onWordClick(vocab));
            holder.btnAdd.setOnClickListener(v -> listener.onAddClick(vocab));
        }

        @Override
        public int getItemCount() { return words.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView wordText, defText;
            ImageButton btnAdd;
            ViewHolder(View itemView) {
                super(itemView);
                wordText = itemView.findViewById(R.id.text_word);
                defText = itemView.findViewById(R.id.text_definition);
                btnAdd = itemView.findViewById(R.id.btn_add_library);
            }
        }
    }
}
