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

    // Static reference để tránh mất MediaPlayer khi Fragment recreate
    private static MediaPlayer sSharedMediaPlayer;

    private FragmentLearnStepBinding b;
    private OnNextListener nextListener;
    private MediaPlayer mediaPlayer;

    public static LearnStepFragment newIntro(Vocabulary v) {
        return create("INTRO", v, null, null);
    }
    public static LearnStepFragment newWordToMeaning(Vocabulary v, List<Vocabulary> batch) {
        return create("WORD_TO_MEANING", v, batch, null);
    }
    public static LearnStepFragment newMeaningToWord(Vocabulary v, List<Vocabulary> batch) {
        return create("MEANING_TO_WORD", v, batch, null);
    }
    public static LearnStepFragment newListenChoose(Vocabulary v, List<Vocabulary> batch, MediaPlayer mp) {
        // mediaPlayer không thể serialize vào Bundle, truyền qua static reference
        sSharedMediaPlayer = mp;
        return create("LISTEN_CHOOSE", v, batch, null);
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

    private static LearnStepFragment create(String type, Vocabulary v, List<Vocabulary> batch, MediaPlayer mp) {
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
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentLearnStepBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() == null) return;

        String type = getArguments().getString(ARG_TYPE, "INTRO");
        String vocabJson = getArguments().getString(ARG_VOCAB);
        String batchJson = getArguments().getString(ARG_BATCH);

        Vocabulary vocab = vocabJson != null ? new Gson().fromJson(vocabJson, Vocabulary.class) : null;
        List<Vocabulary> batch = batchJson != null
                ? new Gson().fromJson(batchJson, new TypeToken<List<Vocabulary>>(){}.getType())
                : new ArrayList<>();

        switch (type) {
            case "INTRO":           setupIntro(vocab); break;
            case "WORD_TO_MEANING": setupWordToMeaning(vocab, batch); break;
            case "MEANING_TO_WORD": setupMeaningToWord(vocab, batch); break;
            case "LISTEN_CHOOSE":
                mediaPlayer = sSharedMediaPlayer;
                setupListenChoose(vocab, batch);
                break;
            case "SUMMARY":         setupSummary(); break;
        }
    }

    // --- DẠNG 1: Làm quen với từ ---
    private void setupIntro(Vocabulary v) {
        b.layoutIntro.setVisibility(View.VISIBLE);
        b.layoutMcq.setVisibility(View.GONE);
        b.layoutTypeAnswer.setVisibility(View.GONE);
        b.layoutSummary.setVisibility(View.GONE);

        b.textIntroWord.setText(v.getWord());
        b.textIntroPhonetic.setText(v.getPhonetic() != null ? v.getPhonetic() : "");
        b.textIntroDefinition.setText(v.getDefinition() != null ? v.getDefinition() : "");
        b.textIntroExample.setText(v.getExample_sentence() != null ? "\"" + v.getExample_sentence() + "\"" : "");
        b.textIntroLabel.setText("Làm quen với từ mới");

        if (v.getImage_url() != null && !v.getImage_url().isEmpty()) {
            b.imgIntro.setVisibility(View.VISIBLE);
            Glide.with(this).load(v.getImage_url()).into(b.imgIntro);
        } else {
            b.imgIntro.setVisibility(View.GONE);
        }

        String audioUrl = v.getAnyAudioUrl();
        b.btnIntroAudio.setVisibility(audioUrl != null && !audioUrl.isEmpty() ? View.VISIBLE : View.GONE);
        b.btnIntroAudio.setOnClickListener(vw -> playAudio(audioUrl));

        b.btnIntroNext.setText("Tiếp tục");
        b.btnIntroSkip.setVisibility(View.VISIBLE);
        b.btnIntroNext.setOnClickListener(vw -> { if (nextListener != null) nextListener.onNext(false); });
        b.btnIntroSkip.setOnClickListener(vw -> { if (nextListener != null) nextListener.onNext(true); });
    }

    // --- DẠNG 2: Cho từ → chọn nghĩa ---
    private void setupWordToMeaning(Vocabulary v, List<Vocabulary> batch) {
        b.layoutIntro.setVisibility(View.GONE);
        b.layoutMcq.setVisibility(View.VISIBLE);
        b.layoutTypeAnswer.setVisibility(View.GONE);
        b.layoutSummary.setVisibility(View.GONE);

        b.textMcqQuestion.setText(v.getWord());
        b.textMcqLabel.setText("Chọn nghĩa đúng");

        List<String> options = buildOptions(v.getDefinition(), batch, false);
        setupMcqButtons(options, v.getDefinition());
    }

    // --- DẠNG 3: Cho nghĩa → ghi từ ---
    private void setupMeaningToWord(Vocabulary v, List<Vocabulary> batch) {
        b.layoutIntro.setVisibility(View.GONE);
        b.layoutMcq.setVisibility(View.GONE);
        b.layoutTypeAnswer.setVisibility(View.VISIBLE);
        b.layoutSummary.setVisibility(View.GONE);

        b.textTypeQuestion.setText(v.getDefinition());
        b.textTypeLabel.setText("Ghi lại từ tương ứng");
        b.editTypeAnswer.setText("");
        b.editTypeAnswer.setHint("Nhập từ...");
        b.btnTypeSubmit.setEnabled(false);

        b.editTypeAnswer.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                b.btnTypeSubmit.setEnabled(s.toString().trim().length() > 0);
            }
            public void afterTextChanged(Editable s) {}
        });

        b.btnTypeSubmit.setOnClickListener(vw -> {
            String answer = b.editTypeAnswer.getText().toString().trim().toLowerCase();
            String correct = v.getWord() != null ? v.getWord().trim().toLowerCase() : "";
            boolean isCorrect = answer.equals(correct);
            showTypeResult(isCorrect, v.getWord());
        });
    }

    private void showTypeResult(boolean correct, String correctWord) {
        b.btnTypeSubmit.setEnabled(false);
        if (correct) {
            b.textTypeFeedback.setText("✓ Chính xác!");
            b.textTypeFeedback.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            b.textTypeFeedback.setText("✗ Đáp án đúng: " + correctWord);
            b.textTypeFeedback.setTextColor(Color.parseColor("#F44336"));
        }
        b.textTypeFeedback.setVisibility(View.VISIBLE);
        b.btnTypeContinue.setVisibility(View.VISIBLE);
        b.btnTypeContinue.setOnClickListener(vw -> { if (nextListener != null) nextListener.onNext(correct); });
    }

    // --- DẠNG 4: Nghe → chọn từ ---
    private void setupListenChoose(Vocabulary v, List<Vocabulary> batch) {
        b.layoutIntro.setVisibility(View.GONE);
        b.layoutMcq.setVisibility(View.VISIBLE);
        b.layoutTypeAnswer.setVisibility(View.GONE);
        b.layoutSummary.setVisibility(View.GONE);

        b.textMcqLabel.setText("Nghe và chọn từ đúng");
        b.textMcqQuestion.setText("🔊 Nhấn để nghe");
        b.textMcqQuestion.setOnClickListener(vw -> playAudio(v.getAnyAudioUrl()));

        List<String> options = buildOptions(v.getWord(), batch, true);
        setupMcqButtons(options, v.getWord());
    }

    // --- DẠNG 5: Summary ---
    private void setupSummary() {
        b.layoutIntro.setVisibility(View.GONE);
        b.layoutMcq.setVisibility(View.GONE);
        b.layoutTypeAnswer.setVisibility(View.GONE);
        b.layoutSummary.setVisibility(View.VISIBLE);

        int total = getArguments() != null ? getArguments().getInt("total", 0) : 0;
        int correct = getArguments() != null ? getArguments().getInt("correct", 0) : 0;

        b.textSummaryTitle.setText("Hoàn thành! 🎉");
        b.textSummaryStats.setText("Bạn đã học " + total + " từ\nTrả lời đúng: " + correct + " lần");
        b.textSummaryXp.setText("+" + (total * 5) + " XP");
        b.btnSummaryDone.setOnClickListener(vw -> { if (nextListener != null) nextListener.onNext(true); });
    }

    // --- Helpers ---
    private List<String> buildOptions(String correct, List<Vocabulary> batch, boolean useWords) {
        List<String> options = new ArrayList<>();
        options.add(correct);
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

    private void setupMcqButtons(List<String> options, String correct) {
        View[] btns = {b.btnOption1, b.btnOption2, b.btnOption3, b.btnOption4};
        for (int i = 0; i < btns.length; i++) {
            final String opt = i < options.size() ? options.get(i) : "—";
            ((android.widget.Button) btns[i]).setText(opt);
            btns[i].setEnabled(true);
            btns[i].setBackgroundResource(com.example.vocabmaster.R.drawable.button_primary);
            btns[i].setOnClickListener(vw -> {
                boolean isCorrect = opt.equals(correct);
                // Disable all buttons
                for (View btn : btns) btn.setEnabled(false);
                // Color feedback
                vw.setBackgroundColor(isCorrect ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
                // Show correct if wrong
                if (!isCorrect) {
                    for (int j = 0; j < btns.length; j++) {
                        if (((android.widget.Button) btns[j]).getText().toString().equals(correct)) {
                            btns[j].setBackgroundColor(Color.parseColor("#4CAF50"));
                        }
                    }
                }
                // Auto advance after 1s
                b.getRoot().postDelayed(() -> {
                    if (nextListener != null) nextListener.onNext(isCorrect);
                }, 1000);
            });
        }
    }

    private void playAudio(String url) {
        if (url == null || url.isEmpty() || mediaPlayer == null) return;
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
        } catch (IOException e) { e.printStackTrace(); }
    }

    @Override
    public void onDestroyView() { super.onDestroyView(); b = null; }
}
