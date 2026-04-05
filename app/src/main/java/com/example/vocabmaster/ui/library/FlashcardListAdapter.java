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
import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.databinding.ItemFlashcardRowBinding;
import com.example.vocabmaster.databinding.LayoutFlashcardTopicBinding;

import java.io.IOException;

public class FlashcardListAdapter extends ListAdapter<Flashcard, FlashcardListAdapter.ViewHolder> {

    private final OnFlashcardDeleteListener deleteListener;
    private static MediaPlayer mediaPlayer;

    public interface OnFlashcardDeleteListener {
        void onDelete(Flashcard flashcard);
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
                        oldItem.getDefinition().equals(newItem.getDefinition()) &&
                        (oldItem.getImageUrl() == null ? newItem.getImageUrl() == null : oldItem.getImageUrl().equals(newItem.getImageUrl()));
            }
        });
        this.deleteListener = deleteListener;
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFlashcardRowBinding binding = ItemFlashcardRowBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding, deleteListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    private static void playAudio(String url, View view) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(view.getContext(), "Không có âm thanh cho từ này", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e("FlashcardAdapter", "MediaPlayer error: " + what + ", " + extra);
                return true;
            });
        } catch (IOException e) {
            Log.e("FlashcardAdapter", "Error playing audio", e);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemFlashcardRowBinding binding;
        private final OnFlashcardDeleteListener deleteListener;

        public ViewHolder(ItemFlashcardRowBinding binding, OnFlashcardDeleteListener deleteListener) {
            super(binding.getRoot());
            this.binding = binding;
            this.deleteListener = deleteListener;
        }

        public void bind(Flashcard card) {
            binding.textTerm.setText(card.getTerm());
            binding.textDefinition.setText(card.getDefinition());

            binding.getRoot().setOnClickListener(v -> showFlashcardDialog(card));
        }

        private void showFlashcardDialog(Flashcard card) {
            LayoutFlashcardTopicBinding dialogBinding = LayoutFlashcardTopicBinding.inflate(
                    LayoutInflater.from(binding.getRoot().getContext()));
            
            AlertDialog dialog = new AlertDialog.Builder(binding.getRoot().getContext(), 
                    com.example.vocabmaster.R.style.Theme_VocabMaster_Dialog_Transparent)
                    .setView(dialogBinding.getRoot())
                    .create();

            // Bind data
            dialogBinding.textTerm.setText(card.getTerm());
            dialogBinding.textDefinition.setText(card.getDefinition());
            dialogBinding.textExample.setText(card.getExample());
            dialogBinding.labelTopic.setText(card.getTag() != null ? card.getTag().toUpperCase() : "PERSONAL");

            // PHONETIC LOGIC
            if (card.getPhonetic() != null && !card.getPhonetic().trim().isEmpty()) {
                dialogBinding.textPhonetic.setText(card.getPhonetic());
                dialogBinding.textPhonetic.setVisibility(View.VISIBLE);
            } else {
                dialogBinding.textPhonetic.setVisibility(View.GONE);
            }

            // AUDIO LOGIC
            if (card.getAudioUrl() != null && !card.getAudioUrl().trim().isEmpty()) {
                dialogBinding.btnAudio.setVisibility(View.VISIBLE);
                dialogBinding.btnAudio.setOnClickListener(v -> playAudio(card.getAudioUrl(), v));
            } else {
                dialogBinding.btnAudio.setVisibility(View.GONE);
            }

            if (card.getImageUrl() != null && !card.getImageUrl().isEmpty()) {
                dialogBinding.imageVocab.setVisibility(View.VISIBLE);
                Glide.with(dialogBinding.imageVocab.getContext())
                        .load(card.getImageUrl())
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.stat_notify_error)
                        .centerInside()
                        .into(dialogBinding.imageVocab);
            } else {
                dialogBinding.imageVocab.setVisibility(View.GONE);
            }

            // Flip logic
            final boolean[] isFront = {true};
            dialogBinding.cardFlashcard.setOnClickListener(v -> {
                if (isFront[0]) {
                    dialogBinding.cardFront.setVisibility(View.GONE);
                    dialogBinding.cardBack.setVisibility(View.VISIBLE);
                } else {
                    dialogBinding.cardFront.setVisibility(View.VISIBLE);
                    dialogBinding.cardBack.setVisibility(View.GONE);
                }
                isFront[0] = !isFront[0];
            });

            // Delete logic
            dialogBinding.btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(binding.getRoot().getContext())
                        .setTitle("Delete Flashcard")
                        .setMessage("Are you sure you want to delete this card?")
                        .setPositiveButton("Delete", (d, which) -> {
                            if (deleteListener != null) {
                                deleteListener.onDelete(card);
                            }
                            dialog.dismiss();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });

            dialog.show();
            
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                lp.copyFrom(window.getAttributes());
                lp.width = WindowManager.LayoutParams.MATCH_PARENT;
                lp.height = WindowManager.LayoutParams.MATCH_PARENT;
                window.setAttributes(lp);
            }
        }
    }
}
