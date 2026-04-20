package com.example.vocabmaster.ui.topic;

import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.databinding.FragmentLearnStepBinding;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LearnStepFragment extends Fragment {

    interface OnNextListener { void onNext(boolean result); }

    private static final String ARG_TYPE = "type";
    private static final String ARG_VOCAB = "vocab";
    private static final String ARG_BATCH = "batch";

    // Static — tránh mất khi Fragment recreate
    static MediaPlayer sSharedMediaPlayer;

    private FragmentLearnStepBinding b;
    private com.example.vocabmaster.databinding.LayoutFlashcardTopicBinding fcBinding;
    private OnNextListener nextListener;

    // --- Factory methods ---
    public static LearnStepFragment newIntroAll(List<Vocabulary> batch, MediaPlayer mp) {
        sSharedMediaPlayer = mp;
        LearnStepFragment f = new LearnStepFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, "INTRO_ALL");
        args.putString(ARG_BATCH, new Gson().toJson(batch));
        f.setArguments(args);
        return f;
    }

    public static LearnStepFragment newWordToMeaning(Vocabulary v, List<Vocabulary> batch) {
        return create("WORD_TO_MEANING", v, batch);
    }

    public static LearnStepFragment newMeaningToWord(Vocabulary v, List<Vocabulary> batch) {
        return create("MEANING_TO_WORD", v, batch);
    }

    public static LearnStepFragment newWordToVietnamese(Vocabulary v, List<Vocabulary> batch) {
        return create("WORD_TO_VIETNAMESE", v, batch);
    }

    public static LearnStepFragment newVietnameseToWord(Vocabulary v, List<Vocabulary> batch) {
        return create("VIETNAMESE_TO_WORD", v, batch);
    }

    public static LearnStepFragment newListenChoose(Vocabulary v, List<Vocabulary> batch, MediaPlayer mp) {
        sSharedMediaPlayer = mp;
        return create("LISTEN_CHOOSE", v, batch);
    }

    public static LearnStepFragment newSummary(int total, int correct) {
        LearnStepFragment f = new LearnStepFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, "SUMMARY");
        args.putInt("total", total);
        args.putInt("correct", correct);
        f.setArguments(args);
        return f;
    }

    private static LearnStepFragment create(String type, Vocabulary v, List<Vocabulary> batch) {
        LearnStepFragment f = new LearnStepFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        args.putString(ARG_VOCAB, new Gson().toJson(v));
        if (batch != null) args.putString(ARG_BATCH, new Gson().toJson(batch));
        f.setArguments(args);
        return f;
    }

    public void setOnNextListener(OnNextListener l) { this.nextListener = l; }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentLearnStepBinding.inflate(inflater, container, false);
        // Bind flashcard topic layout từ include
        fcBinding = com.example.vocabmaster.databinding.LayoutFlashcardTopicBinding.bind(
                b.flashcardContainer.getChildAt(0));
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() == null) return;

        String type = getArguments().getString(ARG_TYPE, "INTRO_ALL");
        String vocabJson = getArguments().getString(ARG_VOCAB);
        String batchJson = getArguments().getString(ARG_BATCH);

        Vocabulary vocab = vocabJson != null ? new Gson().fromJson(vocabJson, Vocabulary.class) : null;
        List<Vocabulary> batch = batchJson != null
                ? new Gson().fromJson(batchJson, new TypeToken<List<Vocabulary>>(){}.getType())
                : new ArrayList<>();

        switch (type) {
            case "INTRO_ALL":           setupIntroAll(batch); break;
            case "WORD_TO_MEANING":     setupWordToMeaning(vocab, batch); break;
            case "MEANING_TO_WORD":     setupMeaningToWord(vocab, batch); break;
            case "WORD_TO_VIETNAMESE":  setupWordToVietnamese(vocab, batch); break;
            case "VIETNAMESE_TO_WORD":  setupVietnameseToWord(vocab, batch); break;
            case "LISTEN_CHOOSE":       setupListenChoose(vocab, batch); break;
            case "SUMMARY":             setupSummary(); break;
        }
    }

    // ── DẠNG 1: Làm quen từng từ dạng flashcard ──────────────────────────
    private void setupIntroAll(List<Vocabulary> batch) {
        b.layoutIntro.setVisibility(View.VISIBLE);
        b.layoutMcq.setVisibility(View.GONE);
        b.layoutTypeAnswer.setVisibility(View.GONE);
        b.layoutSummary.setVisibility(View.GONE);

        // State
        final int[] currentIndex = {0};
        final boolean[] isFlipped = {false};

        Runnable showCard = new Runnable() {
            @Override
            public void run() {
                if (currentIndex[0] >= batch.size()) {
                    // Xong tất cả → hiện nút bắt đầu luyện tập
                    b.flashcardContainer.setVisibility(View.GONE);
                    b.layoutKnowButtons.setVisibility(View.GONE);
                    b.btnIntroNext.setVisibility(View.VISIBLE);
                    b.textIntroLabel.setText("Làm quen xong! 🎉");
                    b.textIntroProgress.setText("Đã xem " + batch.size() + " từ");
                    b.btnIntroNext.setText("Tiếp tục →");
                    b.btnIntroNext.setOnClickListener(vw -> {
                        if (nextListener != null) nextListener.onNext(true);
                    });
                    return;
                }

                Vocabulary v = batch.get(currentIndex[0]);
                isFlipped[0] = false;

                // Reset về mặt trước
                fcBinding.cardFront.setVisibility(View.VISIBLE);
                fcBinding.cardBack.setVisibility(View.GONE);
                b.layoutKnowButtons.setVisibility(View.GONE);

                b.textIntroLabel.setText("Làm quen với từ mới");
                b.textIntroProgress.setText((currentIndex[0] + 1) + " / " + batch.size());

                // Front
                fcBinding.textTerm.setText(v.getWord() != null ? v.getWord() : "");
                fcBinding.textPhonetic.setText(v.getPhonetic() != null ? v.getPhonetic() : "");
                fcBinding.textPhonetic.setVisibility(v.getPhonetic() != null && !v.getPhonetic().isEmpty() ? View.VISIBLE : View.GONE);

                // Nghĩa tiếng Việt dưới phonetic
                String vi = v.getVietnamese_translation();
                fcBinding.textVietnamese.setText(vi != null && !vi.isEmpty() ? "🇻🇳 " + vi : "");
                fcBinding.textVietnamese.setVisibility(vi != null && !vi.isEmpty() ? View.VISIBLE : View.GONE);

                // Topic label
                fcBinding.labelTopic.setText(v.getTopic() != null ? v.getTopic().toUpperCase() : "VOCAB");

                // Ảnh minh họa
                String imgUrl = v.getImage_url();
                if (imgUrl != null && !imgUrl.isEmpty() && !imgUrl.contains("defaultImage")) {
                    fcBinding.imageVocab.setVisibility(View.VISIBLE);
                    com.bumptech.glide.Glide.with(requireContext())
                            .load(imgUrl)
                            .placeholder(R.drawable.macdinh)
                            .error(R.drawable.macdinh)
                            .fitCenter()
                            .into(fcBinding.imageVocab);
                } else {
                    fcBinding.imageVocab.setVisibility(View.GONE);
                }

                // Audio
                String audioUrl = v.getAnyAudioUrl();
                if (audioUrl != null && !audioUrl.isEmpty()) {
                    fcBinding.btnAudio.setVisibility(View.VISIBLE);
                    fcBinding.btnAudio.setOnClickListener(av -> playAudio(audioUrl));
                    fcBinding.btnAudio.post(() -> playAudio(audioUrl));
                } else {
                    fcBinding.btnAudio.setVisibility(View.GONE);
                }

                // Back
                fcBinding.textDefinition.setText(v.getDefinition() != null ? v.getDefinition() : "");
                fcBinding.textExample.setText(v.getExample_sentence() != null ? "\"" + v.getExample_sentence() + "\"" : "");

                // Ẩn nút delete
                fcBinding.btnDelete.setVisibility(View.GONE);

                // Flip on tap
                b.flashcardContainer.setOnClickListener(cv -> {
                    isFlipped[0] = !isFlipped[0];
                    fcBinding.cardFront.setVisibility(isFlipped[0] ? View.GONE : View.VISIBLE);
                    fcBinding.cardBack.setVisibility(isFlipped[0] ? View.VISIBLE : View.GONE);
                    b.layoutKnowButtons.setVisibility(isFlipped[0] ? View.VISIBLE : View.GONE);
                });
            }
        };

        // Tạo nút tiếp tục
        b.layoutKnowButtons.setVisibility(View.GONE);
        b.layoutKnowButtons.removeAllViews();
        
        com.google.android.material.button.MaterialButton btnContinue = new com.google.android.material.button.MaterialButton(requireContext());
        btnContinue.setId(android.R.id.button1);
        btnContinue.setText("Tiếp tục →");
        btnContinue.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        btnContinue.setOnClickListener(vw -> {
            currentIndex[0]++;
            showCard.run();
        });
        b.layoutKnowButtons.addView(btnContinue);

        b.flashcardContainer.setVisibility(View.VISIBLE);
        b.btnIntroNext.setVisibility(View.GONE);
        showCard.run();
    }

    // ── DẠNG 2: Từ → chọn nghĩa tiếng Anh ───────────────────────────────
    private void setupWordToMeaning(Vocabulary v, List<Vocabulary> batch) {
        b.layoutIntro.setVisibility(View.GONE);
        b.layoutMcq.setVisibility(View.VISIBLE);
        b.layoutTypeAnswer.setVisibility(View.GONE);
        b.layoutSummary.setVisibility(View.GONE);

        b.textMcqLabel.setText("Chọn nghĩa tiếng Anh đúng");
        b.textMcqQuestion.setText(v.getWord());
        b.textMcqQuestion.setOnClickListener(null);
        showMcqImage(v);

        List<String> options = buildOptions(v.getDefinition(), batch, false);
        setupMcqButtons(options, v.getDefinition());
    }

    // ── DẠNG 3: Nghĩa tiếng Anh → ghi từ ────────────────────────────────
    private void setupMeaningToWord(Vocabulary v, List<Vocabulary> batch) {
        b.layoutIntro.setVisibility(View.GONE);
        b.layoutMcq.setVisibility(View.GONE);
        b.layoutTypeAnswer.setVisibility(View.VISIBLE);
        b.layoutSummary.setVisibility(View.GONE);

        b.textTypeLabel.setText("Ghi lại từ tương ứng với nghĩa");
        b.textTypeQuestion.setText(v.getDefinition());

        // Thêm ảnh gợi ý vào type answer nếu có
        String imgUrl = v.getImage_url();
        if (imgUrl != null && !imgUrl.isEmpty() && !imgUrl.contains("defaultImage")) {
            b.imgTypeHint.setVisibility(View.VISIBLE);
            com.bumptech.glide.Glide.with(requireContext())
                    .load(imgUrl)
                    .placeholder(R.drawable.macdinh)
                    .error(R.drawable.macdinh)
                    .centerCrop()
                    .into(b.imgTypeHint);
        } else {
            b.imgTypeHint.setVisibility(View.GONE);
        }
        b.editTypeAnswer.setText("");
        b.editTypeAnswer.setHint("Nhập từ...");
        b.btnTypeSubmit.setEnabled(false);
        b.textTypeFeedback.setVisibility(View.GONE);
        b.btnTypeContinue.setVisibility(View.GONE);

        b.editTypeAnswer.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {}
            public void onTextChanged(CharSequence s, int i, int i1, int i2) {
                b.btnTypeSubmit.setEnabled(s.toString().trim().length() > 0);
            }
            public void afterTextChanged(Editable s) {}
        });

        b.btnTypeSubmit.setOnClickListener(vw -> {
            String answer = b.editTypeAnswer.getText().toString().trim().toLowerCase();
            String correct = v.getWord() != null ? v.getWord().trim().toLowerCase() : "";
            boolean isCorrect = answer.equals(correct);
            b.btnTypeSubmit.setEnabled(false);
            if (isCorrect) {
                b.textTypeFeedback.setText("✓ Chính xác!");
                b.textTypeFeedback.setTextColor(Color.parseColor("#4CAF50"));
            } else {
                b.textTypeFeedback.setText("✗ Đáp án đúng: " + v.getWord());
                b.textTypeFeedback.setTextColor(Color.parseColor("#F44336"));
            }
            b.textTypeFeedback.setVisibility(View.VISIBLE);
            b.btnTypeContinue.setVisibility(View.VISIBLE);
            playFeedbackSound(isCorrect);
            b.btnTypeContinue.setOnClickListener(cv -> {
                if (nextListener != null) nextListener.onNext(isCorrect);
            });
        });
    }

    // ── DẠNG 4b: Từ → chọn nghĩa tiếng Việt ─────────────────────────────
    private void setupWordToVietnamese(Vocabulary v, List<Vocabulary> batch) {
        b.layoutIntro.setVisibility(View.GONE);
        b.layoutMcq.setVisibility(View.VISIBLE);
        b.layoutTypeAnswer.setVisibility(View.GONE);
        b.layoutSummary.setVisibility(View.GONE);

        b.textMcqLabel.setText("Chọn nghĩa tiếng Việt đúng");
        b.textMcqQuestion.setText(v.getWord());
        b.textMcqQuestion.setOnClickListener(null);
        b.imgMcq.setVisibility(View.GONE); // không cần ảnh

        String correct = v.getVietnamese_translation();
        List<String> options = buildOptionsVietnamese(correct, batch);
        setupMcqButtons(options, correct);
    }

    // ── DẠNG 4c: Nghĩa tiếng Việt → chọn từ ─────────────────────────────
    private void setupVietnameseToWord(Vocabulary v, List<Vocabulary> batch) {
        b.layoutIntro.setVisibility(View.GONE);
        b.layoutMcq.setVisibility(View.VISIBLE);
        b.layoutTypeAnswer.setVisibility(View.GONE);
        b.layoutSummary.setVisibility(View.GONE);

        b.textMcqLabel.setText("Từ nào có nghĩa tiếng Việt này?");
        b.textMcqQuestion.setText(v.getVietnamese_translation());
        b.textMcqQuestion.setOnClickListener(null);
        b.imgMcq.setVisibility(View.GONE); // không cần ảnh

        List<String> options = buildOptions(v.getWord(), batch, true);
        setupMcqButtons(options, v.getWord());
    }

    // ── DẠNG 5: Nghe → chọn từ ────────────────────────────────────────────
    private void setupListenChoose(Vocabulary v, List<Vocabulary> batch) {
        b.layoutIntro.setVisibility(View.GONE);
        b.layoutMcq.setVisibility(View.VISIBLE);
        b.layoutTypeAnswer.setVisibility(View.GONE);
        b.layoutSummary.setVisibility(View.GONE);

        b.textMcqLabel.setText("Nghe và chọn từ đúng");
        b.textMcqQuestion.setText("🔊 Nhấn để nghe");
        // Auto play khi vào
        b.getRoot().post(() -> playAudio(v.getAnyAudioUrl()));
        b.textMcqQuestion.setOnClickListener(vw -> playAudio(v.getAnyAudioUrl()));

        List<String> options = buildOptions(v.getWord(), batch, true);
        setupMcqButtons(options, v.getWord());
    }

    // ── DẠNG 5: Summary ───────────────────────────────────────────────────
    private void setupSummary() {
        b.layoutIntro.setVisibility(View.GONE);
        b.layoutMcq.setVisibility(View.GONE);
        b.layoutTypeAnswer.setVisibility(View.GONE);
        b.layoutSummary.setVisibility(View.VISIBLE);

        int total = getArguments() != null ? getArguments().getInt("total", 0) : 0;
        int correct = getArguments() != null ? getArguments().getInt("correct", 0) : 0;

        b.textSummaryTitle.setText("Hoàn thành! 🎉");
        b.textSummaryStats.setText("Đã học " + total + " từ  •  Trả lời đúng: " + correct + " lần");
        b.textSummaryXp.setText("+" + (total * 5) + " XP");
        b.btnSummaryDone.setOnClickListener(vw -> {
            if (nextListener != null) nextListener.onNext(true);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private List<String> buildOptions(String correct, List<Vocabulary> batch, boolean useWords) {
        List<String> options = new ArrayList<>();
        if (correct != null) options.add(correct);
        List<Vocabulary> others = new ArrayList<>(batch);
        Collections.shuffle(others);
        for (Vocabulary o : others) {
            String val = useWords ? o.getWord() : o.getDefinition();
            if (val != null && !val.equals(correct) && options.size() < 4) {
                options.add(val);
            }
        }
        while (options.size() < 4) options.add("—");
        Collections.shuffle(options);
        return options;
    }

    /** Build options ưu tiên tiếng Việt, fallback sang definition tiếng Anh */
    private List<String> buildOptionsVietnamese(String correct, List<Vocabulary> batch) {
        List<String> options = new ArrayList<>();
        if (correct != null) options.add(correct);
        List<Vocabulary> others = new ArrayList<>(batch);
        Collections.shuffle(others);
        for (Vocabulary o : others) {
            String vi = o.getVietnamese_translation();
            String val = (vi != null && !vi.isEmpty()) ? vi : o.getDefinition();
            if (val != null && !val.equals(correct) && options.size() < 4) {
                options.add(val);
            }
        }
        while (options.size() < 4) options.add("—");
        Collections.shuffle(options);
        return options;
    }

    private void setupMcqButtons(List<String> options, String correct) {
        View[] btns = {b.btnOption1, b.btnOption2, b.btnOption3, b.btnOption4};

        // Reset feedback
        b.cardMcqFeedback.setVisibility(View.GONE);

        for (int i = 0; i < btns.length; i++) {
            final String opt = i < options.size() ? options.get(i) : "—";
            ((android.widget.Button) btns[i]).setText(opt);
            btns[i].setEnabled(true);
            btns[i].setBackgroundResource(R.drawable.button_primary);
            btns[i].setOnClickListener(vw -> {
                boolean isCorrect = opt.equals(correct);

                // Disable tất cả nút
                for (View btn : btns) btn.setEnabled(false);

                // Màu feedback trên nút
                vw.setBackgroundColor(isCorrect
                        ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
                if (!isCorrect) {
                    for (View btn : btns) {
                        if (((android.widget.Button) btn).getText().toString().equals(correct)) {
                            btn.setBackgroundColor(Color.parseColor("#4CAF50"));
                        }
                    }
                }

                // Hiện feedback card
                b.cardMcqFeedback.setVisibility(View.VISIBLE);
                b.cardMcqFeedback.setCardBackgroundColor(isCorrect
                        ? Color.parseColor("#E8F5E9") : Color.parseColor("#FFEBEE"));
                if (isCorrect) {
                    b.textMcqFeedback.setText("✓ Chính xác!");
                    b.textMcqFeedback.setTextColor(Color.parseColor("#2E7D32"));
                } else {
                    b.textMcqFeedback.setText("✗ Đáp án đúng: " + correct);
                    b.textMcqFeedback.setTextColor(Color.parseColor("#C62828"));
                }

                playFeedbackSound(isCorrect);

                b.btnMcqContinue.setOnClickListener(cv -> {
                    if (nextListener != null) nextListener.onNext(isCorrect);
                });
            });
        }
    }

    private void playAudio(String url) {
        if (url == null || url.isEmpty()) return;
        MediaPlayer mp = sSharedMediaPlayer;
        if (mp == null) return;
        try {
            mp.reset();
            mp.setDataSource(url);
            mp.prepareAsync();
            mp.setOnPreparedListener(MediaPlayer::start);
            mp.setOnErrorListener((m, w, e) -> { m.reset(); return true; });
        } catch (IOException e) { e.printStackTrace(); }
    }

    /** Hiển thị ảnh trong MCQ question card nếu có */
    private void showMcqImage(Vocabulary v) {
        String imgUrl = v.getImage_url();
        if (imgUrl != null && !imgUrl.isEmpty() && !imgUrl.contains("defaultImage")) {
            b.imgMcq.setVisibility(View.VISIBLE);
            com.bumptech.glide.Glide.with(requireContext())
                    .load(imgUrl)
                    .placeholder(R.drawable.macdinh)
                    .error(R.drawable.macdinh)
                    .centerCrop()
                    .into(b.imgMcq);
        } else {
            b.imgMcq.setVisibility(View.GONE);
        }
    }

    /** Phát âm báo đúng/sai dùng ToneGenerator (không cần file asset) */
    private void playFeedbackSound(boolean correct) {
        new Thread(() -> {
            android.media.ToneGenerator tg = null;
            try {
                tg = new android.media.ToneGenerator(
                        android.media.AudioManager.STREAM_MUSIC, 80);
                if (correct) {
                    // Đúng: 2 nốt cao ngắn
                    tg.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 120);
                    Thread.sleep(150);
                } else {
                    // Sai: 1 nốt thấp dài hơn
                    tg.startTone(android.media.ToneGenerator.TONE_PROP_NACK, 300);
                    Thread.sleep(350);
                }
            } catch (Exception ignored) {
            } finally {
                if (tg != null) tg.release();
            }
        }).start();
    }

    @Override
    public void onDestroyView() { super.onDestroyView(); b = null; fcBinding = null; }
}
