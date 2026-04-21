package com.example.vocabmaster.ui.topic;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.databinding.ItemFlashcardHorizontalBinding;

public class TopicFlashcardAdapter extends ListAdapter<Vocabulary, TopicFlashcardAdapter.VH> {

    public TopicFlashcardAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<Vocabulary> DIFF_CALLBACK = new DiffUtil.ItemCallback<Vocabulary>() {
        @Override
        public boolean areItemsTheSame(@NonNull Vocabulary oldItem, @NonNull Vocabulary newItem) {
            return oldItem.getVocabularyId().equals(newItem.getVocabularyId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Vocabulary oldItem, @NonNull Vocabulary newItem) {
            return oldItem.equals(newItem);
        }
    };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFlashcardHorizontalBinding binding = ItemFlashcardHorizontalBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemFlashcardHorizontalBinding binding;
        private boolean isFlipped = false;

        public VH(ItemFlashcardHorizontalBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Vocabulary vocab) {
            isFlipped = false;
            binding.cardFront.setVisibility(View.VISIBLE);
            binding.cardBack.setVisibility(View.GONE);

            // Front side
            binding.textWord.setText(vocab.getWord());
            binding.textPhonetic.setText(vocab.getPhonetic() != null ? vocab.getPhonetic() : "");
            binding.textPhonetic.setVisibility(vocab.getPhonetic() != null && !vocab.getPhonetic().isEmpty() ? View.VISIBLE : View.GONE);

            // Vietnamese translation — hiển thị bên dưới phiên âm
            String vi = vocab.getVietnamese_translation();
            if (vi != null && !vi.isEmpty()) {
                binding.textVietnamese.setText("🇻🇳 " + vi);
                binding.textVietnamese.setVisibility(View.VISIBLE);
            } else {
                binding.textVietnamese.setVisibility(View.GONE);
            }

            // Topic label
            binding.labelTopic.setText(vocab.getTopic() != null ? vocab.getTopic().toUpperCase() : "VOCAB");

            // Image
            String imgUrl = vocab.getImage_url();
            if (imgUrl != null && !imgUrl.isEmpty() && !imgUrl.contains("defaultImage")) {
                binding.imageVocab.setVisibility(View.VISIBLE);
                com.bumptech.glide.Glide.with(binding.getRoot().getContext())
                        .load(imgUrl)
                        .placeholder(R.drawable.macdinh)
                        .error(R.drawable.macdinh)
                        .centerCrop()
                        .into(binding.imageVocab);
            } else {
                binding.imageVocab.setVisibility(View.GONE);
            }

            // Back side
            binding.textDefinition.setText(vocab.getDefinition() != null ? vocab.getDefinition() : "");
            binding.textExample.setText(vocab.getExample_sentence() != null ? "\"" + vocab.getExample_sentence() + "\"" : "");
            binding.textExample.setVisibility(vocab.getExample_sentence() != null && !vocab.getExample_sentence().isEmpty() ? View.VISIBLE : View.GONE);

            // Hide delete button
            binding.btnDelete.setVisibility(View.GONE);

            // Click to flip
            binding.cardFlashcard.setOnClickListener(v -> {
                isFlipped = !isFlipped;
                binding.cardFront.setVisibility(isFlipped ? View.GONE : View.VISIBLE);
                binding.cardBack.setVisibility(isFlipped ? View.VISIBLE : View.GONE);
            });
        }
    }
}
