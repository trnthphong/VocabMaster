package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.databinding.ActivityTopicWordListBinding;
import com.example.vocabmaster.ui.study.StudyActivity;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TopicWordListActivity extends AppCompatActivity {
    private ActivityTopicWordListBinding binding;
    private WordAdapter listAdapter;
    private TopicFlashcardAdapter swipeAdapter;
    private String topic;
    private String displayTitle;
    private String langCode;
    private String selectedLevel;
    private FirebaseFirestore db;
    private List<Vocabulary> currentWords = new ArrayList<>();
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTopicWordListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        mediaPlayer = new MediaPlayer();
        
        topic = getIntent().getStringExtra("selected_topic");
        displayTitle = getIntent().getStringExtra("display_title");
        langCode = getIntent().getStringExtra("lang_code");
        selectedLevel = getIntent().getStringExtra("selected_level");

        if (langCode != null) {
            if (langCode.contains("Anh")) langCode = "en";
            else if (langCode.contains("Nga")) langCode = "ru";
        }

        binding.textHeaderTitle.setText(displayTitle != null ? displayTitle : "Danh sách từ");
        binding.btnBack.setOnClickListener(v -> {
            if (binding.layoutSwipe.getVisibility() == View.VISIBLE) {
                showListView();
            } else {
                finish();
            }
        });
        
        binding.btnAddNewWord.setOnClickListener(v -> showAddEditWordDialog(null));
        binding.btnChangeLang.setVisibility(View.GONE);
        binding.tabLayoutLevel.setVisibility(View.GONE);

        setupAdapters();
        loadWords();
    }

    private void setupAdapters() {
        // List Adapter
        listAdapter = new WordAdapter(new WordAdapter.OnWordClickListener() {
            @Override
            public void onWordClick(Vocabulary vocab) {
                showSwipeView(currentWords.indexOf(vocab));
            }

            @Override
            public void onEditClick(Vocabulary vocab) {
                showAddEditWordDialog(vocab);
            }

            @Override
            public void onDeleteClick(Vocabulary vocab) {
                confirmDeleteWord(vocab);
            }
        });
        binding.recyclerWords.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerWords.setAdapter(listAdapter);

        // Swipe Adapter (Flashcard mode)
        swipeAdapter = new TopicFlashcardAdapter(this, url -> playAudio(url));
        binding.viewPagerFlashcards.setAdapter(swipeAdapter);
        
        // Cấu hình hiệu ứng vuốt giống My Library
        binding.viewPagerFlashcards.setOffscreenPageLimit(3);
        binding.viewPagerFlashcards.setClipToPadding(false);
        binding.viewPagerFlashcards.setClipChildren(false);
        float margin = 40 * getResources().getDisplayMetrics().density;
        binding.viewPagerFlashcards.setPageTransformer((page, position) -> {
            float absPos = Math.abs(position);
            page.setScaleY(0.85f + (1 - absPos) * 0.15f);
            page.setAlpha(0.5f + (1 - absPos) * 0.5f);
            page.setTranslationX(-position * margin);
        });
    }

    private void showListView() {
        binding.layoutList.setVisibility(View.VISIBLE);
        binding.layoutSwipe.setVisibility(View.GONE);
        binding.textHeaderTitle.setText(displayTitle);
    }

    private void showSwipeView(int position) {
        binding.layoutList.setVisibility(View.GONE);
        binding.layoutSwipe.setVisibility(View.VISIBLE);
        binding.viewPagerFlashcards.setCurrentItem(position, false);
        binding.textHeaderTitle.setText("Flashcards");
    }

    private void playAudio(String url) {
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
        } catch (IOException e) {
            Log.e("TopicWordList", "Error playing audio", e);
        }
    }

    private void showAddEditWordDialog(Vocabulary vocab) {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_word, null);
        EditText editWord = view.findViewById(R.id.edit_word_name);
        EditText editDef = view.findViewById(R.id.edit_word_definition);
        
        if (vocab != null) {
            editWord.setText(vocab.getWord());
            editDef.setText(vocab.getDefinition());
        }

        new AlertDialog.Builder(this)
                .setTitle(vocab == null ? "Thêm từ mới" : "Sửa từ vựng")
                .setView(view)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String name = editWord.getText().toString().trim();
                    String def = editDef.getText().toString().trim();
                    if (name.isEmpty()) return;
                    
                    if (vocab == null) {
                        addNewWordToFirestore(name, def);
                    } else {
                        updateWordInFirestore(vocab, name, def);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void addNewWordToFirestore(String name, String def) {
        Vocabulary v = new Vocabulary();
        v.setWord(name);
        v.setDefinition(def);
        v.setTopic(topic != null ? topic.toLowerCase() : "custom");
        v.setCefr(selectedLevel != null ? selectedLevel : "A1");
        
        String collectionPath = "ru".equals(langCode) ? "russian_vocabularies" : "vocabularies";
        db.collection(collectionPath).add(v).addOnSuccessListener(doc -> loadWords());
    }

    private void updateWordInFirestore(Vocabulary vocab, String name, String def) {
        vocab.setWord(name);
        vocab.setDefinition(def);
        String collectionPath = "ru".equals(langCode) ? "russian_vocabularies" : "vocabularies";
        db.collection(collectionPath).document(vocab.getVocabularyId()).set(vocab).addOnSuccessListener(aVoid -> loadWords());
    }

    private void confirmDeleteWord(Vocabulary vocab) {
        new AlertDialog.Builder(this)
                .setTitle("Xóa từ vựng")
                .setMessage("Bạn có chắc chắn muốn xóa từ '" + vocab.getWord() + "' không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    String collectionPath = "ru".equals(langCode) ? "russian_vocabularies" : "vocabularies";
                    db.collection(collectionPath).document(vocab.getVocabularyId()).delete().addOnSuccessListener(aVoid -> loadWords());
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void loadWords() {
        if (binding.progressBar != null) binding.progressBar.setVisibility(View.VISIBLE);
        String collectionPath = "ru".equals(langCode) ? "russian_vocabularies" : "vocabularies";
        Query query = db.collection(collectionPath);
        
        if (topic != null) {
            query = query.whereEqualTo("topic", topic.toLowerCase());
        }
        
        if (selectedLevel != null) {
            query = query.whereEqualTo("cefr", selectedLevel);
        }
        
        query.get().addOnSuccessListener(snapshots -> {
            currentWords.clear();
            for (DocumentSnapshot doc : snapshots) {
                Vocabulary v = doc.toObject(Vocabulary.class);
                if (v != null) {
                    v.setVocabularyId(doc.getId());
                    currentWords.add(v);
                }
            }
            if (binding.progressBar != null) binding.progressBar.setVisibility(View.GONE);
            listAdapter.submitList(currentWords);
            swipeAdapter.submitList(currentWords);
            
            binding.textCount.setText(currentWords.size() + " từ vựng (" + selectedLevel + ")");
            
            if (currentWords.isEmpty()) {
                binding.layoutEmpty.setVisibility(View.VISIBLE);
            } else {
                binding.layoutEmpty.setVisibility(View.GONE);
            }
        }).addOnFailureListener(e -> {
            if (binding.progressBar != null) binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Lỗi khi tải từ vựng", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onBackPressed() {
        if (binding.layoutSwipe.getVisibility() == View.VISIBLE) {
            showListView();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private static class WordAdapter extends RecyclerView.Adapter<WordAdapter.ViewHolder> {
        private List<Vocabulary> words = new ArrayList<>();
        private final OnWordClickListener listener;

        public interface OnWordClickListener {
            void onWordClick(Vocabulary vocab);
            void onEditClick(Vocabulary vocab);
            void onDeleteClick(Vocabulary vocab);
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
            holder.defText.setText(vocab.getDefinition());
            
            holder.itemView.setOnClickListener(v -> listener.onWordClick(vocab));
            holder.btnEdit.setOnClickListener(v -> listener.onEditClick(vocab));
            holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(vocab));
        }

        @Override
        public int getItemCount() { return words.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView wordText, defText;
            ImageButton btnEdit, btnDelete;
            ViewHolder(View itemView) {
                super(itemView);
                wordText = itemView.findViewById(R.id.text_word);
                defText = itemView.findViewById(R.id.text_definition);
                btnEdit = itemView.findViewById(R.id.btn_edit_word);
                btnDelete = itemView.findViewById(R.id.btn_delete_word);
            }
        }
    }
}
