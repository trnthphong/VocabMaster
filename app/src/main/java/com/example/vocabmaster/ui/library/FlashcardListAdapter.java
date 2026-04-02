package com.example.vocabmaster.ui.library;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.databinding.ItemFlashcardRowBinding;
import com.example.vocabmaster.databinding.LayoutFlashcardTopicBinding;

public class FlashcardListAdapter extends ListAdapter<Flashcard, FlashcardListAdapter.ViewHolder> {

    protected FlashcardListAdapter() {
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
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFlashcardRowBinding binding = ItemFlashcardRowBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemFlashcardRowBinding binding;

        public ViewHolder(ItemFlashcardRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
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

            // Bind data to the flippable card
            dialogBinding.textTerm.setText(card.getTerm());
            dialogBinding.textDefinition.setText(card.getDefinition());
            dialogBinding.textExample.setText(card.getExample());
            dialogBinding.labelTopic.setText(card.getTag() != null ? card.getTag().toUpperCase() : "PERSONAL");

            if (card.getImageUrl() != null && !card.getImageUrl().isEmpty()) {
                dialogBinding.imageVocab.setVisibility(View.VISIBLE);
                Glide.with(dialogBinding.imageVocab).load(card.getImageUrl()).into(dialogBinding.imageVocab);
            } else {
                dialogBinding.imageVocab.setVisibility(View.GONE);
            }

            // Flip logic inside dialog
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

            dialog.show();
            
            // Set width for dialog
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(
                        (int) (binding.getRoot().getContext().getResources().getDisplayMetrics().widthPixels * 0.9),
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
            }
        }
    }
}
