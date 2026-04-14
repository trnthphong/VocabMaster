package com.example.vocabmaster.ui.home;

import android.app.Application;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.data.repository.CourseRepository;
import com.example.vocabmaster.databinding.LayoutFlashcardTopicBinding;
import com.example.vocabmaster.ui.common.UiFeedback;

import java.util.ArrayList;
import java.util.List;

public class TopicFlashcardAdapter extends RecyclerView.Adapter<TopicFlashcardAdapter.ViewHolder> {
    private List<Vocabulary> words = new ArrayList<>();
    private final CourseRepository repository;
    private final OnAudioClickListener audioListener;

    public interface OnAudioClickListener {
        void onAudioClick(String url);
    }

    public TopicFlashcardAdapter(Context context, OnAudioClickListener audioListener) {
        this.repository = new CourseRepository((Application) context.getApplicationContext());
        this.audioListener = audioListener;
    }

    public void submitList(List<Vocabulary> newList) {
        this.words = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutFlashcardTopicBinding binding = LayoutFlashcardTopicBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Vocabulary vocab = words.get(position);
        holder.bind(vocab);
    }

    @Override
    public int getItemCount() {
        return words.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final LayoutFlashcardTopicBinding binding;

        ViewHolder(LayoutFlashcardTopicBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Vocabulary vocab) {
            binding.textTerm.setText(vocab.getWord());
            binding.textDefinition.setText(vocab.getDefinition());
            binding.textPhonetic.setText(vocab.getPhonetic());
            binding.textPhonetic.setVisibility(vocab.getPhonetic() != null ? View.VISIBLE : View.GONE);
            binding.labelTopic.setText(vocab.getTopic() != null ? vocab.getTopic().toUpperCase() : "VOCAB");
            binding.textExample.setText(vocab.getExampleSentence());
            binding.textExample.setVisibility(vocab.getExampleSentence() != null ? View.VISIBLE : View.GONE);
            
            // Front side visible by default
            binding.cardFront.setVisibility(View.VISIBLE);
            binding.cardBack.setVisibility(View.GONE);

            binding.cardFlashcard.setOnClickListener(v -> flipCard());

            String audioUrl = vocab.getAnyAudioUrl();
            binding.btnAudio.setVisibility(audioUrl != null && !audioUrl.isEmpty() ? View.VISIBLE : View.GONE);
            binding.btnAudio.setOnClickListener(v -> {
                if (audioListener != null) audioListener.onAudioClick(audioUrl);
            });

            // Thay nút delete thành nút lưu vào thư viện (như yêu cầu trước đó)
            binding.btnDelete.setImageResource(android.R.drawable.ic_input_add);
            binding.btnDelete.setOnClickListener(v -> {
                Flashcard card = new Flashcard(vocab.getWord(), vocab.getDefinition());
                card.setImageUrl(vocab.getImageUrl());
                card.setAudioUrl(vocab.getAnyAudioUrl());
                card.setPhonetic(vocab.getPhonetic());
                card.setExample(vocab.getExampleSentence());
                card.setTag(vocab.getTopic() != null ? vocab.getTopic() : "Journey");
                
                repository.addPersonalFlashcard(card);
                Toast.makeText(itemView.getContext(), "Đã lưu vào thư viện", Toast.LENGTH_SHORT).show();
                UiFeedback.performHaptic(itemView.getContext(), 10);
                binding.btnDelete.setEnabled(false);
                binding.btnDelete.setAlpha(0.5f);
            });
        }

        private void flipCard() {
            if (binding.cardFront.getVisibility() == View.VISIBLE) {
                binding.cardFront.setVisibility(View.GONE);
                binding.cardBack.setVisibility(View.VISIBLE);
            } else {
                binding.cardFront.setVisibility(View.VISIBLE);
                binding.cardBack.setVisibility(View.GONE);
            }
        }
    }
}
