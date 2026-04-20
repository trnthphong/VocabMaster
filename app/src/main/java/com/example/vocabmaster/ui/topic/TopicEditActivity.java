package com.example.vocabmaster.ui.topic;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.vocabmaster.R;
import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.local.VocabularyDao;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.databinding.ActivityTopicEditBinding;
import com.example.vocabmaster.databinding.ItemVocabEditBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TopicEditActivity extends AppCompatActivity {

    private ActivityTopicEditBinding binding;
    private VocabularyDao vocabularyDao;
    private FirebaseFirestore db;
    private String uid;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String topicId;
    private String topicTitle;
    private boolean isPersonal;

    private VocabEditAdapter adapter;
    private final List<Vocabulary> vocabularies = new ArrayList<>();

    private static final int PICK_IMAGE_REQUEST = 1001;
    private String currentImageUrl = null;
    private boolean hasChanges = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTopicEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        topicId = getIntent().getStringExtra("topic_id");
        topicTitle = getIntent().getStringExtra("topic_title");
        isPersonal = getIntent().getBooleanExtra("is_personal_topic", false);

        binding.toolbar.setTitle("Chỉnh sửa: " + (topicTitle != null ? topicTitle : ""));
        binding.toolbar.setNavigationOnClickListener(v -> {
            if (hasChanges) {
                setResult(RESULT_OK);
            }
            finish();
        });

        vocabularyDao = AppDatabase.getDatabase(this).vocabularyDao();
        db = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getUid();

        setupRecycler();
        loadVocabularies();

        binding.btnAddWord.setOnClickListener(v -> showAddWordDialog());
        binding.btnSave.setOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
        });
    }

    private void setupRecycler() {
        adapter = new VocabEditAdapter();
        binding.recyclerVocab.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerVocab.setAdapter(adapter);
    }

    private void loadVocabularies() {
        String key = isPersonal ? topicId : topicId.toLowerCase();
        binding.progressBar.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            List<Vocabulary> result = vocabularyDao.getVocabulariesByTopic(key);
            mainHandler.post(() -> {
                binding.progressBar.setVisibility(View.GONE);
                vocabularies.clear();
                vocabularies.addAll(result);
                adapter.notifyDataSetChanged();
                updateEmptyState();
            });
        });
    }

    private void updateEmptyState() {
        if (vocabularies.isEmpty()) {
            binding.textEmpty.setVisibility(View.VISIBLE);
            binding.recyclerVocab.setVisibility(View.GONE);
        } else {
            binding.textEmpty.setVisibility(View.GONE);
            binding.recyclerVocab.setVisibility(View.VISIBLE);
        }
    }

    private void showAddWordDialog() {
        showEditWordDialog(null, -1);
    }

    private void showEditWordDialog(@Nullable Vocabulary vocab, int position) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_vocab, null);
        EditText etWord = dialogView.findViewById(R.id.edit_word);
        EditText etDefinition = dialogView.findViewById(R.id.edit_definition);
        EditText etExample = dialogView.findViewById(R.id.edit_example);
        EditText etPhonetic = dialogView.findViewById(R.id.edit_phonetic);
        EditText etVietnamese = dialogView.findViewById(R.id.edit_vietnamese);
        ImageView imgPreview = dialogView.findViewById(R.id.img_preview);
        View btnSelectImage = dialogView.findViewById(R.id.btn_select_image);

        // Reset current image URL
        if (vocab != null && vocab.getImage_url() != null) {
            currentImageUrl = vocab.getImage_url();
            Glide.with(this).load(currentImageUrl).into(imgPreview);
        } else {
            currentImageUrl = null;
            imgPreview.setImageResource(R.drawable.macdinh);
        }

        if (vocab != null) {
            etWord.setText(vocab.getWord());
            etDefinition.setText(vocab.getDefinition());
            etExample.setText(vocab.getExample_sentence());
            etPhonetic.setText(vocab.getPhonetic());
            etVietnamese.setText(vocab.getVietnamese_translation());
        }

        btnSelectImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(vocab == null ? "Thêm từ mới" : "Chỉnh sửa từ")
                .setView(dialogView)
                .setPositiveButton("Lưu", null)
                .setNegativeButton("Hủy", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String word = etWord.getText().toString().trim();
                String definition = etDefinition.getText().toString().trim();
                String example = etExample.getText().toString().trim();
                String phonetic = etPhonetic.getText().toString().trim();
                String vietnamese = etVietnamese.getText().toString().trim();

                if (TextUtils.isEmpty(word) || TextUtils.isEmpty(definition)) {
                    Toast.makeText(this, "Vui lòng nhập từ và định nghĩa", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (vocab == null) {
                    // Add new word
                    addNewWord(word, definition, example, phonetic, vietnamese, currentImageUrl);
                } else {
                    // Edit existing word
                    editWord(vocab, word, definition, example, phonetic, vietnamese, currentImageUrl, position);
                }
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void addNewWord(String word, String definition, String example, String phonetic, String vietnamese, String imageUrl) {
        Vocabulary newVocab = new Vocabulary();
        newVocab.setVocabularyId(UUID.randomUUID().toString());
        newVocab.setWord(word);
        newVocab.setDefinition(definition);
        newVocab.setExample_sentence(example);
        newVocab.setPhonetic(phonetic);
        newVocab.setVietnamese_translation(vietnamese);
        newVocab.setImage_url(imageUrl);
        newVocab.setTopic(isPersonal ? topicId : topicId.toLowerCase());
        newVocab.setLearnStatus(0);

        // Save to local database immediately
        executor.execute(() -> {
            vocabularyDao.insertAll(java.util.Collections.singletonList(newVocab));
            
            // Save to Firestore if personal topic
            if (isPersonal && uid != null) {
                saveVocabToFirestore(newVocab);
            }

            mainHandler.post(() -> {
                vocabularies.add(newVocab);
                adapter.notifyItemInserted(vocabularies.size() - 1);
                updateEmptyState();
                hasChanges = true;
                Toast.makeText(this, "Đã thêm từ mới", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void editWord(Vocabulary vocab, String word, String definition, String example, String phonetic, String vietnamese, String imageUrl, int position) {
        vocab.setWord(word);
        vocab.setDefinition(definition);
        vocab.setExample_sentence(example);
        vocab.setPhonetic(phonetic);
        vocab.setVietnamese_translation(vietnamese);
        vocab.setImage_url(imageUrl);

        // Update local database immediately
        executor.execute(() -> {
            vocabularyDao.update(vocab);
            
            // Update Firestore if personal topic
            if (isPersonal && uid != null) {
                updateVocabInFirestore(vocab);
            }

            mainHandler.post(() -> {
                adapter.notifyItemChanged(position);
                hasChanges = true;
                Toast.makeText(this, "Đã cập nhật từ", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void deleteWord(Vocabulary vocab, int position) {
        executor.execute(() -> {
            // Delete from local database
            vocabularyDao.delete(vocab);
            
            // Delete from Firestore if personal topic
            if (isPersonal && uid != null) {
                deleteVocabFromFirestore(vocab.getVocabularyId());
            }

            mainHandler.post(() -> {
                vocabularies.remove(position);
                adapter.notifyItemRemoved(position);
                updateEmptyState();
                hasChanges = true;
                Toast.makeText(this, "Đã xóa từ", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void saveVocabToFirestore(Vocabulary vocab) {
        if (uid == null) return;

        Map<String, Object> vocabMap = new HashMap<>();
        vocabMap.put("vocabularyId", vocab.getVocabularyId());
        vocabMap.put("word", vocab.getWord());
        vocabMap.put("definition", vocab.getDefinition());
        vocabMap.put("example_sentence", vocab.getExample_sentence());
        vocabMap.put("phonetic", vocab.getPhonetic());
        vocabMap.put("vietnamese_translation", vocab.getVietnamese_translation());
        vocabMap.put("image_url", vocab.getImage_url());
        vocabMap.put("learnStatus", vocab.getLearnStatus());

        db.collection("users").document(uid)
                .collection("personal_courses").document(topicId)
                .collection("vocabularies").document(vocab.getVocabularyId())
                .set(vocabMap);

        // Update word count
        updateWordCountInFirestore();
    }

    private void updateVocabInFirestore(Vocabulary vocab) {
        if (uid == null) return;

        Map<String, Object> vocabMap = new HashMap<>();
        vocabMap.put("word", vocab.getWord());
        vocabMap.put("definition", vocab.getDefinition());
        vocabMap.put("example_sentence", vocab.getExample_sentence());
        vocabMap.put("phonetic", vocab.getPhonetic());
        vocabMap.put("vietnamese_translation", vocab.getVietnamese_translation());
        vocabMap.put("image_url", vocab.getImage_url());

        db.collection("users").document(uid)
                .collection("personal_courses").document(topicId)
                .collection("vocabularies").document(vocab.getVocabularyId())
                .update(vocabMap);
    }

    private void deleteVocabFromFirestore(String vocabularyId) {
        if (uid == null) return;

        db.collection("users").document(uid)
                .collection("personal_courses").document(topicId)
                .collection("vocabularies").document(vocabularyId)
                .delete();

        // Update word count
        updateWordCountInFirestore();
    }

    private void updateWordCountInFirestore() {
        if (uid == null) return;

        db.collection("users").document(uid)
                .collection("personal_courses").document(topicId)
                .update("wordCount", vocabularies.size());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                currentImageUrl = imageUri.toString();
            }
        }
    }

    class VocabEditAdapter extends RecyclerView.Adapter<VocabEditAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemVocabEditBinding b = ItemVocabEditBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(b);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Vocabulary v = vocabularies.get(position);
            holder.bind(v, position);
        }

        @Override
        public int getItemCount() {
            return vocabularies.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ItemVocabEditBinding b;

            VH(ItemVocabEditBinding b) {
                super(b.getRoot());
                this.b = b;
            }

            void bind(Vocabulary v, int position) {
                b.textWord.setText(v.getWord());
                b.textDefinition.setText(v.getDefinition());
                
                if (v.getImage_url() != null && !v.getImage_url().isEmpty()) {
                    b.imgVocab.setVisibility(View.VISIBLE);
                    Glide.with(b.getRoot().getContext())
                            .load(v.getImage_url())
                            .placeholder(R.drawable.macdinh)
                            .into(b.imgVocab);
                } else {
                    b.imgVocab.setVisibility(View.GONE);
                }

                b.btnEdit.setOnClickListener(btn -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        showEditWordDialog(vocabularies.get(pos), pos);
                    }
                });

                b.btnDelete.setOnClickListener(btn -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        new AlertDialog.Builder(TopicEditActivity.this)
                                .setTitle("Xóa từ")
                                .setMessage("Bạn có chắc muốn xóa từ \"" + vocabularies.get(pos).getWord() + "\"?")
                                .setPositiveButton("Xóa", (d, w) -> {
                                    deleteWord(vocabularies.get(pos), pos);
                                })
                                .setNegativeButton("Hủy", null)
                                .show();
                    }
                });
            }
        }
    }
}
