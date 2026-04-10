package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.os.Bundle;
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
import com.example.vocabmaster.data.repository.CourseRepository;
import com.example.vocabmaster.databinding.ActivityTopicWordListBinding;
import com.example.vocabmaster.ui.study.StudyActivity;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class TopicWordListActivity extends AppCompatActivity {
    private ActivityTopicWordListBinding binding;
    private WordAdapter adapter;
    private String topic;
    private String displayTitle;
    private String langCode;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTopicWordListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        topic = getIntent().getStringExtra("topic");
        displayTitle = getIntent().getStringExtra("display_title");
        langCode = getIntent().getStringExtra("lang_code");

        if (langCode != null) {
            if (langCode.contains("Anh")) langCode = "en";
            else if (langCode.contains("Nga")) langCode = "ru";
        }

        binding.textHeaderTitle.setText(displayTitle != null ? displayTitle : "Danh sách từ");
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnAddNewWord.setOnClickListener(v -> showAddEditWordDialog(null));
        
        // Ẩn nút đổi ngôn ngữ nếu ứng dụng chỉ để học tiếng anh (hoặc xử lý theo yêu cầu)
        binding.btnChangeLang.setVisibility(View.GONE);

        setupRecyclerView();
        loadWords();
    }

    private void setupRecyclerView() {
        adapter = new WordAdapter(new WordAdapter.OnWordClickListener() {
            @Override
            public void onWordClick(Vocabulary vocab) {
                ArrayList<String> allIds = new ArrayList<>();
                List<Vocabulary> currentList = adapter.getWords();
                for (Vocabulary v : currentList) {
                    allIds.add(v.getVocabularyId());
                }
                int startIndex = currentList.indexOf(vocab);
                Intent intent = new Intent(TopicWordListActivity.this, StudyActivity.class);
                intent.putStringArrayListExtra("word_ids", allIds);
                intent.putExtra("start_index", startIndex);
                intent.putExtra("lesson_title", displayTitle);
                startActivity(intent);
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
        binding.recyclerWords.setAdapter(adapter);
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
        
        db.collection("vocabularies").add(v).addOnSuccessListener(doc -> loadWords());
    }

    private void updateWordInFirestore(Vocabulary vocab, String name, String def) {
        vocab.setWord(name);
        vocab.setDefinition(def);
        db.collection("vocabularies").document(vocab.getVocabularyId()).set(vocab).addOnSuccessListener(aVoid -> loadWords());
    }

    private void confirmDeleteWord(Vocabulary vocab) {
        new AlertDialog.Builder(this)
                .setTitle("Xóa từ vựng")
                .setMessage("Bạn có chắc chắn muốn xóa từ '" + vocab.getWord() + "' không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    db.collection("vocabularies").document(vocab.getVocabularyId()).delete().addOnSuccessListener(aVoid -> loadWords());
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void loadWords() {
        binding.progressBar.setVisibility(View.VISIBLE);
        Query query = db.collection("vocabularies");
        if (topic != null) {
            query = query.whereEqualTo("topic", topic.toLowerCase());
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
            binding.progressBar.setVisibility(View.GONE);
            adapter.submitList(words);
            binding.textCount.setText(words.size() + " từ vựng");
        });
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

        public List<Vocabulary> getWords() { return words; }

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
