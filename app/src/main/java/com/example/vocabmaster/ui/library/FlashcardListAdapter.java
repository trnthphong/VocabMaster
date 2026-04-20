package com.example.vocabmaster.ui.library;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.databinding.LayoutFlashcardTopicBinding;

import java.io.IOException;

public class FlashcardListAdapter extends ListAdapter<Flashcard, FlashcardListAdapter.ViewHolder> {

    private final OnFlashcardDeleteListener deleteListener;
    private OnFlashcardEditListener editListener;
    private static MediaPlayer mediaPlayer;
    private boolean isViewPagerMode = false;

    public interface OnFlashcardDeleteListener {
        void onDelete(Flashcard flashcard);
    }

    public interface OnFlashcardEditListener {
        void onEdit(Flashcard flashcard);
    }

    public FlashcardListAdapter(OnFlashcardDeleteListener deleteListener) {
        super(new DiffUtil.ItemCallback<Flashcard>() {
            @Override
            public boolean areItemsTheSame(@NonNull Flashcard oldItem, @NonNull Flashcard newItem) {
                return oldItem.getId() == newItem.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull Flashcard oldItem, @NonNull Flashcard newItem) {
                return oldItem.getTerm().equals(newItem.getTerm()) &&
                        oldItem.getDefinition().equals(newItem.getDefinition());
            }
        });
        this.deleteListener = deleteListener;
        if (mediaPlayer == null) mediaPlayer = new MediaPlayer();
    }

    public void setEditListener(OnFlashcardEditListener listener) {
        this.editListener = listener;
    }

    public void setViewPagerMode(boolean viewPagerMode) {
        this.isViewPagerMode = viewPagerMode;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutFlashcardTopicBinding binding = LayoutFlashcardTopicBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        
        if (isViewPagerMode) {
            ViewGroup.LayoutParams lp = binding.getRoot().getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            binding.getRoot().setLayoutParams(lp);
        }
        
        return new ViewHolder(binding, deleteListener, editListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    private static void playAudio(String url, View view) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(view.getContext(), "Không có âm thanh", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
        } catch (IOException e) {
            Log.e("FlashcardAdapter", "Error playing audio", e);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final LayoutFlashcardTopicBinding binding;
        private final OnFlashcardDeleteListener deleteListener;
        private final OnFlashcardEditListener editListener;

        public ViewHolder(LayoutFlashcardTopicBinding binding,
                          OnFlashcardDeleteListener deleteListener,
                          OnFlashcardEditListener editListener) {
            super(binding.getRoot());
            this.binding = binding;
            this.deleteListener = deleteListener;
            this.editListener = editListener;
        }

        public void bind(Flashcard card) {
            binding.textTerm.setText(card.getTerm());
            binding.textDefinition.setText(card.getDefinition());
            binding.textExample.setText(card.getExample());
            binding.labelTopic.setText(card.getTag() != null ? card.getTag().toUpperCase() : "PERSONAL");

            if (card.getPhonetic() != null && !card.getPhonetic().isEmpty()) {
                binding.textPhonetic.setText(card.getPhonetic());
                binding.textPhonetic.setVisibility(View.VISIBLE);
            } else {
                binding.textPhonetic.setVisibility(View.GONE);
            }

            if (card.getAudioUrl() != null && !card.getAudioUrl().isEmpty()) {
                binding.btnAudio.setVisibility(View.VISIBLE);
                binding.btnAudio.setOnClickListener(v -> playAudio(card.getAudioUrl(), v));
            } else {
                binding.btnAudio.setVisibility(View.GONE);
            }

            binding.imageVocab.setVisibility(View.VISIBLE);
            if (card.getImageUrl() != null && !card.getImageUrl().trim().isEmpty()) {
                Glide.with(binding.imageVocab.getContext())
                        .load(card.getImageUrl())
                        .placeholder(R.drawable.macdinh)
                        .error(R.drawable.macdinh)
                        .into(binding.imageVocab);
            } else {
                binding.imageVocab.setImageResource(R.drawable.macdinh);
            }

            binding.cardFlashcard.setOnClickListener(v -> {
                if (binding.cardFront.getVisibility() == View.VISIBLE) {
                    binding.cardFront.setVisibility(View.GONE);
                    binding.cardBack.setVisibility(View.VISIBLE);
                } else {
                    binding.cardFront.setVisibility(View.VISIBLE);
                    binding.cardBack.setVisibility(View.GONE);
                }
            });

            // Long press → edit
            binding.cardFlashcard.setOnLongClickListener(v -> {
                if (editListener != null) editListener.onEdit(card);
                return true;
            });

            binding.btnDelete.setOnClickListener(v -> {
                if (deleteListener != null) deleteListener.onDelete(card);
            });
        }
    }
}
