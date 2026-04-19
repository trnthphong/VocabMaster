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

import com.bumptech.glide.Glide;
import com.example.vocabmaster.R;
import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.local.VocabularyDao;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.databinding.ActivityTopicWordListBinding;
import com.example.vocabmaster.databinding.DialogEditWordBinding;
import com.example.vocabmaster.ui.study.StudyActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TopicWordListActivity extends AppCompatActivity {
    private ActivityTopicWordListBinding binding;
    private WordAdapter listAdapter;
    private TopicFlashcardAdapter swipeAdapter;
    private String topic;
    private String displayTitle;
    private String langCode;
    private String selectedLevel;
    private boolean isPersonal = false;
    private FirebaseFirestore db;
    private VocabularyDao vocabularyDao;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private List<Vocabulary> currentWords = new ArrayList<>();
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTopicWordListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        vocabularyDao = AppDatabase.getDatabase(this).vocabularyDao();
        mediaPlayer = new MediaPlayer();
        
        topic = getIntent().getStringExtra("selected_topic");
        displayTitle = getIntent().getStringExtra("display_title");
        langCode = getIntent().getStringExtra("lang_code");
        selectedLevel = getIntent().getStringExtra("selected_level");
        isPersonal = getIntent().getBooleanExtra("is_personal", false);

        binding.textHeaderTitle.setText(displayTitle != null ? displayTitle : "Danh sách từ");
        binding.btnBack.setOnClickListener(v -> finish());
        
        setupAdapters();
        checkLocalAndLoad();
    }

    private void checkLocalAndLoad() {
        if (binding.progressBar != null) binding.progressBar.setVisibility(View.VISIBLE);
        
        executorService.execute(() -> {
            List<Vocabulary> localWords = vocabularyDao.getVocabulariesByTopic(topic.toLowerCase());
            
            runOnUiThread(() -> {
                if (!localWords.isEmpty()) {
                    updateUIWithWords(localWords, " (Offline)");
                } else {
                    loadWordsFromFirestore();
                }
            });
        });
    }

    private void loadWordsFromFirestore() {
        if (isPersonal) {
            String uid = FirebaseAuth.getInstance().getUid();
            if (uid == null) return;

            db.collection("users").document(uid)
                    .collection("personal_topics").document(topic)
                    .collection("vocabularies")
                    .get()
                    .addOnSuccessListener(snapshots -> {
                        List<Vocabulary> words = new ArrayList<>();
                        for (DocumentSnapshot doc : snapshots) {
                            Vocabulary v = doc.toObject(Vocabulary.class);
                            if (v != null) {
                                v.setVocabularyId(doc.getId());
                                words.add(v);
                            }
                        }
                        updateUIWithWords(words, "");
                    })
                    .addOnFailureListener(e -> {
                        if (binding.progressBar != null) binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Lỗi khi tải từ vựng cá nhân", Toast.LENGTH_SHORT).show();
                    });
        } else {
            String collectionPath = "ru".equals(langCode) ? "russian_vocabularies" : "vocabularies";
            Query query = db.collection(collectionPath).whereEqualTo("topic", topic.toLowerCase());
            
            if (selectedLevel != null) {
                query = query.whereEqualTo("cefr", selectedLevel);
            }
            
            query.get().addOnSuccessListener(snapshots -> {
                List<Vocabulary> words = new ArrayList<>();
                for (DocumentSnapshot doc : snapshots) {
                    Vocabulary v = doc.toObject(Vocabulary.class);
                    if (v != null) {
                        v.setVocabularyId(doc.getId());
                        words.add(v);
                    }
                }
                updateUIWithWords(words, "");
            }).addOnFailureListener(e -> {
                if (binding.progressBar != null) binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Không có kết nối mạng và chưa tải bộ từ này", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void updateUIWithWords(List<Vocabulary> words, String suffix) {
        currentWords = words;
        if (binding.progressBar != null) binding.progressBar.setVisibility(View.GONE);
        listAdapter.submitList(currentWords);
        swipeAdapter.submitList(currentWords);
        binding.textCount.setText(currentWords.size() + " từ vựng" + suffix);
        binding.layoutEmpty.setVisibility(words.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setupAdapters() {
        listAdapter = new WordAdapter(new WordAdapter.OnWordActionListener() {
            @Override
            public void onWordClick(Vocabulary vocab) {
                showSwipeView(currentWords.indexOf(vocab));
            }

            @Override
            public void onEditClick(Vocabulary vocab) {
                if (isPersonal) showEditWordDialog(vocab);
                else Toast.makeText(TopicWordListActivity.this, "Chỉ có thể sửa từ vựng trong bộ từ cá nhân", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDeleteClick(Vocabulary vocab) {
                if (isPersonal) confirmDeleteWord(vocab);
                else Toast.makeText(TopicWordListActivity.this, "Chỉ có thể xóa từ vựng trong bộ từ cá nhân", Toast.LENGTH_SHORT).show();
            }
        });
        binding.recyclerWords.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerWords.setAdapter(listAdapter);

        swipeAdapter = new TopicFlashcardAdapter(this, url -> playAudio(url));
        binding.viewPagerFlashcards.setAdapter(swipeAdapter);
    }

    private void showEditWordDialog(Vocabulary vocab) {
        DialogEditWordBinding dialogBinding = DialogEditWordBinding.inflate(getLayoutInflater());
        dialogBinding.editWordName.setText(vocab.getWord());
        dialogBinding.editWordDefinition.setText(vocab.getDefinition());
        dialogBinding.editWordImageUrl.setText(vocab.getImageUrl());
        
        if (vocab.getImageUrl() != null && !vocab.getImageUrl().isEmpty()) {
            Glide.with(this).load(vocab.getImageUrl()).into(dialogBinding.imgWordPreview);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Sửa từ vựng")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Cập nhật", (dialog, which) -> {
                    String newDef = dialogBinding.editWordDefinition.getText().toString().trim();
                    String newImg = dialogBinding.editWordImageUrl.getText().toString().trim();
                    updateWordInFirestore(vocab, newDef, newImg);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void updateWordInFirestore(Vocabulary vocab, String newDef, String newImg) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("definition", newDef);
        updates.put("image_url", newImg);

        db.collection("users").document(uid)
                .collection("personal_topics").document(topic)
                .collection("vocabularies").document(vocab.getVocabularyId())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    vocab.setDefinition(newDef);
                    vocab.setImageUrl(newImg);
                    listAdapter.notifyDataSetChanged();
                    swipeAdapter.notifyDataSetChanged();
                    Toast.makeText(this, "Đã cập nhật", Toast.LENGTH_SHORT).show();
                });
    }

    private void confirmDeleteWord(Vocabulary vocab) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Xóa từ vựng")
                .setMessage("Bạn có chắc chắn muốn xóa từ '" + vocab.getWord() + "'?")
                .setPositiveButton("Xóa", (dialog, which) -> deleteWordFromFirestore(vocab))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteWordFromFirestore(Vocabulary vocab) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        db.collection("users").document(uid)
                .collection("personal_topics").document(topic)
                .collection("vocabularies").document(vocab.getVocabularyId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    currentWords.remove(vocab);
                    listAdapter.submitList(new ArrayList<>(currentWords));
                    swipeAdapter.submitList(new ArrayList<>(currentWords));
                    Toast.makeText(this, "Đã xóa", Toast.LENGTH_SHORT).show();
                });
    }

    private void showSwipeView(int position) {
        binding.layoutList.setVisibility(View.GONE);
        binding.layoutSwipe.setVisibility(View.VISIBLE);
        binding.viewPagerFlashcards.setCurrentItem(position, false);
    }

    private void playAudio(String url) {
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
        } catch (Exception e) {
            Log.e("TopicWordList", "Error audio", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) mediaPlayer.release();
    }

    private static class WordAdapter extends RecyclerView.Adapter<WordAdapter.ViewHolder> {
        private List<Vocabulary> words = new ArrayList<>();
        private final OnWordActionListener listener;
        
        public interface OnWordActionListener { 
            void onWordClick(Vocabulary vocab);
            void onEditClick(Vocabulary vocab);
            void onDeleteClick(Vocabulary vocab);
        }

        public WordAdapter(OnWordActionListener listener) { this.listener = listener; }
        public void submitList(List<Vocabulary> newList) { this.words = newList; notifyDataSetChanged(); }

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
            View btnEdit, btnDelete;
            ViewHolder(View v) { 
                super(v); 
                wordText = v.findViewById(R.id.text_word); 
                defText = v.findViewById(R.id.text_definition);
                btnEdit = v.findViewById(R.id.btn_edit_word);
                btnDelete = v.findViewById(R.id.btn_delete_word);
            }
        }
    }
}
