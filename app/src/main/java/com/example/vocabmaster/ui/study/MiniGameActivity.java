package com.example.vocabmaster.ui.study;

import android.content.SharedPreferences;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.model.Notification;
import com.example.vocabmaster.data.model.Topic;
import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.data.repository.GamificationRepository;
import com.example.vocabmaster.databinding.ActivityMiniGameBinding;
import com.example.vocabmaster.ui.common.GamificationStatusBinder;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.example.vocabmaster.util.SoundEffectManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class MiniGameActivity extends AppCompatActivity {

    private ActivityMiniGameBinding binding;
    private GamificationRepository gamificationRepository;
    private GamificationStatusBinder gamificationStatusBinder;
    private String currentUid;
    private FirebaseFirestore firestore;
    private User currentUserData;
    
    private TextToSpeech tts;
    private boolean isTtsReady = false;
    private MediaPlayer mediaPlayer;
    private boolean quickGameHeartSpendInProgress = false;

    // AI Riddle Mode variables
    private boolean isAiRiddleMode = false;
    private boolean isAiRiddleLobbyVisible = false;
    private int aiRiddleScore = 0;
    private Vocabulary currentRiddleWord;
    private CountDownTimer riddleTimer;
    private static final int RIDDLE_TIME_LIMIT = 30000; // 30 seconds
    private int currentPointsPossible = 5;
    
    private boolean hintEngShown = false;
    private boolean hintViShown = false;
    private boolean hint50Shown = false;
    private boolean hint75Shown = false;

    // Sentence Scramble variables
    private boolean isSentenceScrambleMode = false;
    private boolean isSentenceScrambleLobbyVisible = false;
    private int sentenceScrambleScore = 0;
    private CountDownTimer sentenceScrambleTimer;
    private final List<String> sentenceCorrectWords = new ArrayList<>();
    private final List<String> sentenceBoardWords = new ArrayList<>();
    private final List<String> sentenceHandWords = new ArrayList<>();
    private LinearLayout sentenceBoardContainer;
    private LinearLayout sentenceHandContainer;
    private TextView sentenceTimerText;
    private ProgressBar sentenceTimerProgress;
    private static final int SENTENCE_TIME_LIMIT = 30000;
    private static final Pattern SENTENCE_WORD_PATTERN = Pattern.compile("[A-Za-z]+(?:['-][A-Za-z]+)?");

    // Letter Scramble variables
    private boolean isLetterScrambleMode = false;
    private boolean isLetterScrambleLobbyVisible = false;
    private int letterScrambleScore = 0;
    private CountDownTimer letterScrambleTimer;
    private String currentLetterAnswer = "";
    private final List<String> letterBoardItems = new ArrayList<>();
    private final List<String> letterHandItems = new ArrayList<>();
    private LinearLayout letterBoardContainer;
    private LinearLayout letterHandContainer;
    private TextView letterTimerText;
    private ProgressBar letterTimerProgress;
    private static final int LETTER_TIME_LIMIT = 30000;
    private static final Pattern LETTER_WORD_PATTERN = Pattern.compile("[A-Za-z]{3,12}");

    // Lightning Mode variables
    private boolean isLightningMode = false;
    private boolean isLightningLobbyVisible = false;
    private int lightningScore = 0;
    private boolean currentLightningAnswer = false;
    private CountDownTimer lightningTimer;
    private final List<Vocabulary> lightningVocabs = new ArrayList<>();
    private TextView lightningTimerText;
    private ProgressBar lightningTimerProgress;
    private TextView lightningWordText;
    private TextView lightningMeaningText;
    private static final int LIGHTNING_TIME_LIMIT = 60000;

    // Friend Battle variables
    private boolean isFriendBattleMode = false;
    private boolean isFriendBattleLobbyVisible = false;
    private String friendRoomId;
    private String friendRoomCode;
    private String friendHostId;
    private String friendRoomStatus = "waiting";
    private int friendQuestionCount = 10;
    private int friendQuestionTimeSeconds = 30;
    private int friendCurrentQuestionIndex = -1;
    private long friendRoundStartedAtMillis = 0L;
    private int friendCurrentPointsPossible = 5;
    private boolean friendAnsweredCurrentQuestion = false;
    private boolean friendHintEngShown = false;
    private boolean friendHintViShown = false;
    private boolean friendHint50Shown = false;
    private boolean friendHint75Shown = false;
    private CountDownTimer friendBattleTimer;
    private ListenerRegistration friendRoomListener;
    private ListenerRegistration friendPlayersListener;
    private final List<Map<String, Object>> friendPlayers = new ArrayList<>();
    private Map<String, Object> friendCurrentQuestion = new HashMap<>();
    private TextView friendTimerText;
    private ProgressBar friendTimerProgress;
    private TextView friendPromptText;
    private TextView friendEnglishHintText;
    private TextView friendVietnameseHintText;
    private TextView friendPlaceholderText;
    private TextView friendLetterCountText;
    private TextInputEditText friendAnswerEdit;
    private MaterialButton friendSubmitButton;
    private BottomSheetDialog friendRoundRankingDialog;
    private LinearLayout friendRoundRankingContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMiniGameBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        firestore = FirebaseFirestore.getInstance();
        gamificationRepository = new GamificationRepository(this);
        gamificationStatusBinder = new GamificationStatusBinder(this, binding.getRoot());
        currentUid = FirebaseAuth.getInstance().getUid();
        gamificationStatusBinder.start(currentUid);
        loadCurrentUserData();

        initTTS();
        initMediaPlayer();
        setupClickListeners();
    }

    private void initTTS() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isTtsReady = true;
                }
            }
        });
    }

    private void initMediaPlayer() {
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        }
    }

    private void loadCurrentUserData() {
        if (currentUid == null) {
            Toast.makeText(this, "Bạn cần đăng nhập để chơi Quick Games", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        firestore.collection("users").document(currentUid).get().addOnSuccessListener(snapshot -> {
            currentUserData = snapshot.toObject(User.class);
            if (currentUserData != null) currentUserData.setUid(currentUid);
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Không tải được thông tin tài khoản", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private String currentPlayerName() {
        if (currentUserData != null && !isBlank(currentUserData.getName())) return currentUserData.getName();
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String displayName = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
            if (!isBlank(displayName)) return displayName;
            String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            if (!isBlank(email)) return email;
        }
        return "Người chơi";
    }

    private void spendHeartBeforeQuickGame(Runnable onAllowed) {
        if (currentUid == null) {
            Toast.makeText(this, "Bạn cần đăng nhập để chơi Quick Games", Toast.LENGTH_SHORT).show();
            return;
        }
        if (quickGameHeartSpendInProgress) return;
        quickGameHeartSpendInProgress = true;
        gamificationRepository.spendHeart(currentUid)
                .addOnSuccessListener(result -> {
                    quickGameHeartSpendInProgress = false;
                    if (result.isAllowed()) {
                        onAllowed.run();
                    } else {
                        showOutOfHeartsDialog();
                    }
                })
                .addOnFailureListener(e -> {
                    quickGameHeartSpendInProgress = false;
                    Toast.makeText(this, "Không kiểm tra được tim, thử lại sau nhé", Toast.LENGTH_SHORT).show();
                });
    }

    private void showOutOfHeartsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Hết tim")
                .setMessage("Bạn cần chờ tim hồi lại trước khi chơi Quick Games tiếp.")
                .setPositiveButton("Đã hiểu", null)
                .show();
    }

    private void playSoundEffect(boolean isSuccess) {
        if (isSuccess) {
            SoundEffectManager.playCorrect(this);
        } else {
            SoundEffectManager.playWrong(this);
        }
        if (isSuccess) {
            UiFeedback.performHaptic(this, 30);
        } else {
            UiFeedback.performHaptic(this, 100);
        }
    }

    private void setupClickListeners() {
        binding.btnClose.setOnClickListener(v -> handleBack());
        binding.cardPlayAi.setOnClickListener(v -> showAiModes());
        binding.cardPlayFriends.setOnClickListener(v -> showFriendBattleHome());
        binding.cardAiRiddle.setOnClickListener(v -> showAiRiddleLobby());
        binding.cardSentenceScramble.setOnClickListener(v -> showSentenceScrambleLobby());
        binding.cardLetterScramble.setOnClickListener(v -> showLetterScrambleLobby());
        binding.cardLightning.setOnClickListener(v -> showLightningLobby());
    }

    private void showAiModes() {
        if (riddleTimer != null) riddleTimer.cancel();
        if (sentenceScrambleTimer != null) sentenceScrambleTimer.cancel();
        if (letterScrambleTimer != null) letterScrambleTimer.cancel();
        if (lightningTimer != null) lightningTimer.cancel();
        stopFriendBattleSession(true);
        isAiRiddleMode = false;
        isAiRiddleLobbyVisible = false;
        isSentenceScrambleMode = false;
        isSentenceScrambleLobbyVisible = false;
        isLetterScrambleMode = false;
        isLetterScrambleLobbyVisible = false;
        isLightningMode = false;
        isLightningLobbyVisible = false;
        isFriendBattleMode = false;
        isFriendBattleLobbyVisible = false;
        binding.gameContainer.removeAllViews();
        binding.gameContainer.setVisibility(View.GONE);
        binding.layoutModeSelection.setVisibility(View.GONE);
        binding.layoutAiModes.setVisibility(View.VISIBLE);
        binding.textTitle.setText("Chơi với máy");
        updateScoreUI();
    }

    // --- FRIEND BATTLE MODE ---
    private void showFriendBattleHome() {
        if (currentUid == null) {
            Toast.makeText(this, "Bạn cần đăng nhập để chơi với bạn bè", Toast.LENGTH_SHORT).show();
            return;
        }
        stopFriendBattleSession(true);
        isFriendBattleMode = true;
        isFriendBattleLobbyVisible = true;
        isAiRiddleMode = false;
        isAiRiddleLobbyVisible = false;
        isSentenceScrambleMode = false;
        isSentenceScrambleLobbyVisible = false;
        isLetterScrambleMode = false;
        isLetterScrambleLobbyVisible = false;
        isLightningMode = false;
        isLightningLobbyVisible = false;
        binding.layoutModeSelection.setVisibility(View.GONE);
        binding.layoutAiModes.setVisibility(View.GONE);
        binding.gameContainer.removeAllViews();
        binding.gameContainer.setVisibility(View.VISIBLE);
        binding.textTitle.setText("Chơi với bạn bè");
        updateScoreUI();

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("ĐẤU TỪ NHANH");
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitle = new TextView(this);
        subtitle.setText("Tạo phòng hoặc nhập mã để cùng bạn bè đoán từ qua gợi ý.");
        subtitle.setTextColor(getColor(R.color.text_secondary));
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(8), 0, dp(28));
        root.addView(subtitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        MaterialButton createButton = new MaterialButton(this);
        createButton.setText("TẠO PHÒNG");
        createButton.setTextSize(17);
        createButton.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(createButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
        ));
        createButton.setOnClickListener(v -> showCreateFriendRoomDialog());

        TextView joinLabel = sectionLabel("Nhập mã phòng");
        LinearLayout.LayoutParams joinLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        joinLabelParams.setMargins(0, dp(28), 0, dp(8));
        root.addView(joinLabel, joinLabelParams);

        EditText codeEdit = new EditText(this);
        codeEdit.setSingleLine(true);
        codeEdit.setGravity(Gravity.CENTER);
        codeEdit.setHint("VD: A1B2C3");
        codeEdit.setTextSize(20);
        codeEdit.setAllCaps(true);
        codeEdit.setPadding(dp(16), dp(12), dp(16), dp(12));
        codeEdit.setBackgroundResource(R.drawable.bg_white_translucent_stroke);
        root.addView(codeEdit, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
        ));

        MaterialButton joinButton = new MaterialButton(this);
        joinButton.setText("VÀO PHÒNG");
        joinButton.setTextSize(16);
        LinearLayout.LayoutParams joinParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
        );
        joinParams.setMargins(0, dp(16), 0, 0);
        root.addView(joinButton, joinParams);
        joinButton.setOnClickListener(v -> joinFriendRoomByCode(codeEdit.getText().toString().trim()));

        binding.gameContainer.addView(scrollView);
    }

    private void showCreateFriendRoomDialog() {
        Toast.makeText(this, "Đang tải chủ đề...", Toast.LENGTH_SHORT).show();
        firestore.collection("topics")
                .orderBy("order", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<TopicChoice> topics = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Topic topic = doc.toObject(Topic.class);
                        if (topic == null) continue;
                        String id = !isBlank(topic.getId()) ? topic.getId() : doc.getId();
                        String name = !isBlank(topic.getName()) ? topic.getName() : id;
                        topics.add(new TopicChoice(id, name));
                    }
                    if (topics.isEmpty()) {
                        Toast.makeText(this, "Chưa có chủ đề hệ thống để chơi", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    showCreateFriendRoomDialog(topics);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Không tải được chủ đề", Toast.LENGTH_SHORT).show());
    }

    private void showCreateFriendRoomDialog(List<TopicChoice> topics) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(8), dp(16), 0);

        TextView topicLabel = sectionLabel("Chủ đề");
        form.addView(topicLabel);
        Spinner topicSpinner = new Spinner(this);
        topicSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, topics));
        form.addView(topicSpinner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView countLabel = sectionLabel("Số lượng câu");
        LinearLayout.LayoutParams countLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        countLabelParams.setMargins(0, dp(16), 0, 0);
        form.addView(countLabel, countLabelParams);
        Spinner countSpinner = new Spinner(this);
        countSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new Integer[]{5, 10, 15, 20}));
        countSpinner.setSelection(1);
        form.addView(countSpinner);

        TextView timeLabel = sectionLabel("Thời gian mỗi câu");
        LinearLayout.LayoutParams timeLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        timeLabelParams.setMargins(0, dp(16), 0, 0);
        form.addView(timeLabel, timeLabelParams);
        Spinner timeSpinner = new Spinner(this);
        timeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"20 giây", "30 giây", "45 giây", "60 giây"}));
        timeSpinner.setSelection(1);
        form.addView(timeSpinner);

        new AlertDialog.Builder(this)
                .setTitle("Tạo phòng Đấu Từ Nhanh")
                .setView(form)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Tạo", (dialog, which) -> {
                    TopicChoice selectedTopic = (TopicChoice) topicSpinner.getSelectedItem();
                    int count = (Integer) countSpinner.getSelectedItem();
                    int[] timeOptions = new int[]{20, 30, 45, 60};
                    int seconds = timeOptions[timeSpinner.getSelectedItemPosition()];
                    createFriendRoom(selectedTopic, count, seconds);
                })
                .show();
    }

    private void createFriendRoom(TopicChoice topic, int questionCount, int questionTimeSeconds) {
        Toast.makeText(this, "Đang tạo phòng...", Toast.LENGTH_SHORT).show();
        firestore.collection("topics").document(topic.id).collection("vocabularies")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Vocabulary> usableVocabs = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Vocabulary vocab = doc.toObject(Vocabulary.class);
                        if (vocab == null) continue;
                        if (isBlank(vocab.getVocabularyId())) vocab.setVocabularyId(doc.getId());
                        if (isUsableRiddleWord(vocab)) usableVocabs.add(vocab);
                    }
                    if (usableVocabs.size() < questionCount) {
                        Toast.makeText(this, "Chủ đề này chưa đủ từ phù hợp để tạo " + questionCount + " câu", Toast.LENGTH_LONG).show();
                        return;
                    }
                    Collections.shuffle(usableVocabs);
                    spendHeartBeforeQuickGame(() ->
                            writeFriendRoom(topic, questionCount, questionTimeSeconds, usableVocabs.subList(0, questionCount)));
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Không tải được từ vựng của chủ đề", Toast.LENGTH_SHORT).show());
    }

    private void writeFriendRoom(TopicChoice topic, int questionCount, int questionTimeSeconds, List<Vocabulary> questions) {
        DocumentReference roomRef = firestore.collection("friend_game_rooms").document();
        String code = generateRoomCode();
        Map<String, Object> roomData = new HashMap<>();
        roomData.put("code", code);
        roomData.put("hostId", currentUid);
        roomData.put("hostName", currentPlayerName());
        roomData.put("status", "waiting");
        roomData.put("topicId", topic.id);
        roomData.put("topicName", topic.name);
        roomData.put("questionCount", questionCount);
        roomData.put("questionTimeSeconds", questionTimeSeconds);
        roomData.put("currentQuestionIndex", -1);
        roomData.put("createdAt", FieldValue.serverTimestamp());

        WriteBatch batch = firestore.batch();
        batch.set(roomRef, roomData);
        batch.set(roomRef.collection("players").document(currentUid), createFriendPlayerData());
        for (int i = 0; i < questions.size(); i++) {
            batch.set(roomRef.collection("questions").document(String.valueOf(i)), createFriendQuestionData(questions.get(i)));
        }

        batch.commit()
                .addOnSuccessListener(v -> enterFriendRoom(roomRef.getId(), code))
                .addOnFailureListener(e -> Toast.makeText(this, "Không tạo được phòng", Toast.LENGTH_SHORT).show());
    }

    private Map<String, Object> createFriendPlayerData() {
        Map<String, Object> player = new HashMap<>();
        player.put("uid", currentUid);
        player.put("name", currentPlayerName());
        player.put("avatar", currentUserData != null ? safe(currentUserData.getAvatar()) : "");
        player.put("score", 0);
        player.put("answeredQuestion", -1);
        player.put("joinedAt", FieldValue.serverTimestamp());
        return player;
    }

    private Map<String, Object> createFriendQuestionData(Vocabulary vocab) {
        Map<String, Object> data = new HashMap<>();
        data.put("word", safe(vocab.getWord()));
        data.put("definition", safe(vocab.getDefinition()));
        data.put("example", safe(vocab.getExample_sentence()));
        String vi = safe(vocab.getVietnamese_translation());
        if (vi.isEmpty()) vi = safe(vocab.getVietnameseTranslation());
        data.put("vietnamese", vi);
        return data;
    }

    private String generateRoomCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.US);
    }

    private void joinFriendRoomByCode(String code) {
        String normalizedCode = safe(code).toUpperCase(Locale.US);
        if (normalizedCode.length() < 4) {
            Toast.makeText(this, "Vui lòng nhập mã phòng", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Đang vào phòng...", Toast.LENGTH_SHORT).show();
        firestore.collection("friend_game_rooms")
                .whereEqualTo("code", normalizedCode)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        Toast.makeText(this, "Không tìm thấy phòng", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    DocumentSnapshot room = snapshot.getDocuments().get(0);
                    String status = safe(room.getString("status"));
                    if (!status.equals("waiting")) {
                        Toast.makeText(this, "Phòng đã bắt đầu hoặc đã kết thúc", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    spendHeartBeforeQuickGame(() ->
                            firestore.collection("friend_game_rooms").document(room.getId())
                                    .collection("players").document(currentUid)
                                    .set(createFriendPlayerData(), SetOptions.merge())
                                    .addOnSuccessListener(v -> enterFriendRoom(room.getId(), normalizedCode))
                                    .addOnFailureListener(e -> Toast.makeText(this, "Không vào được phòng", Toast.LENGTH_SHORT).show()));
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Không vào được phòng", Toast.LENGTH_SHORT).show());
    }

    private void enterFriendRoom(String roomId, String code) {
        stopFriendBattleSession(false);
        friendRoomId = roomId;
        friendRoomCode = code;
        isFriendBattleMode = true;
        isFriendBattleLobbyVisible = true;
        binding.layoutModeSelection.setVisibility(View.GONE);
        binding.layoutAiModes.setVisibility(View.GONE);
        binding.gameContainer.setVisibility(View.VISIBLE);
        binding.textTitle.setText("Đấu Từ Nhanh");
        updateScoreUI();
        listenFriendRoom();
        listenFriendPlayers();
    }

    private void listenFriendRoom() {
        if (friendRoomId == null) return;
        friendRoomListener = firestore.collection("friend_game_rooms").document(friendRoomId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !snapshot.exists()) {
                        Toast.makeText(this, "Phòng không còn tồn tại", Toast.LENGTH_SHORT).show();
                        showFriendBattleHome();
                        return;
                    }
                    friendRoomStatus = safe(snapshot.getString("status"));
                    friendHostId = safe(snapshot.getString("hostId"));
                    friendRoomCode = safe(snapshot.getString("code"));
                    Number count = (Number) snapshot.get("questionCount");
                    Number time = (Number) snapshot.get("questionTimeSeconds");
                    Number index = (Number) snapshot.get("currentQuestionIndex");
                    friendQuestionCount = count != null ? count.intValue() : 10;
                    friendQuestionTimeSeconds = time != null ? time.intValue() : 30;
                    friendCurrentQuestionIndex = index != null ? index.intValue() : -1;
                    Object startedAt = snapshot.get("roundStartedAt");
                    friendRoundStartedAtMillis = startedAt instanceof com.google.firebase.Timestamp
                            ? ((com.google.firebase.Timestamp) startedAt).toDate().getTime()
                            : System.currentTimeMillis();

                    if (friendRoomStatus.equals("waiting")) {
                        renderFriendBattleLobby(snapshot);
                    } else if (friendRoomStatus.equals("playing")) {
                        loadFriendQuestion(friendCurrentQuestionIndex);
                    } else if (friendRoomStatus.equals("finished")) {
                        renderFriendBattleResults();
                    }
                });
    }

    private void listenFriendPlayers() {
        if (friendRoomId == null) return;
        friendPlayersListener = firestore.collection("friend_game_rooms").document(friendRoomId)
                .collection("players")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null) return;
                    friendPlayers.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Map<String, Object> player = new HashMap<>(doc.getData() != null ? doc.getData() : new HashMap<>());
                        player.put("uid", doc.getId());
                        friendPlayers.add(player);
                    }
                    friendPlayers.sort((a, b) -> Integer.compare(getPlayerScore(b), getPlayerScore(a)));
                    updateScoreUI();
                    if (friendRoomStatus.equals("waiting")) {
                        firestore.collection("friend_game_rooms").document(friendRoomId).get()
                                .addOnSuccessListener(this::renderFriendBattleLobby);
                    } else if (friendRoomStatus.equals("finished")) {
                        renderFriendBattleResults();
                    } else if (friendRoomStatus.equals("playing")
                            && friendRoundRankingDialog != null
                            && friendRoundRankingDialog.isShowing()) {
                        renderFriendBattleRoundRanking();
                    }
                });
    }

    private void renderFriendBattleLobby(DocumentSnapshot roomSnapshot) {
        isFriendBattleLobbyVisible = true;
        if (friendBattleTimer != null) friendBattleTimer.cancel();
        binding.gameContainer.removeAllViews();

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(20), dp(24), dp(24));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView codeTitle = sectionLabel("Mã phòng");
        root.addView(codeTitle);

        TextView codeText = new TextView(this);
        codeText.setText(friendRoomCode);
        codeText.setTextColor(getColor(R.color.brand_primary));
        codeText.setTextSize(34);
        codeText.setTypeface(Typeface.DEFAULT_BOLD);
        codeText.setGravity(Gravity.CENTER);
        codeText.setPadding(0, dp(10), 0, dp(10));
        codeText.setBackgroundResource(R.drawable.bg_white_translucent_stroke);
        LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        codeParams.setMargins(0, dp(8), 0, dp(16));
        root.addView(codeText, codeParams);
        codeText.setOnClickListener(v -> copyRoomCode());

        String topicName = roomSnapshot != null ? safe(roomSnapshot.getString("topicName")) : "";
        TextView configText = hintBox("Chủ đề: " + topicName + "\n"
                + "Số câu: " + friendQuestionCount + "\n"
                + "Thời gian mỗi câu: " + friendQuestionTimeSeconds + " giây");
        root.addView(configText, hintBoxParams());

        TextView playersTitle = sectionLabel("Người chơi (" + friendPlayers.size() + ")");
        LinearLayout.LayoutParams playersTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        playersTitleParams.setMargins(0, dp(20), 0, dp(8));
        root.addView(playersTitle, playersTitleParams);

        TextView playersText = new TextView(this);
        playersText.setText(buildPlayersText(false));
        playersText.setTextColor(getColor(R.color.text_primary));
        playersText.setTextSize(16);
        playersText.setLineSpacing(dp(4), 1f);
        playersText.setPadding(dp(16), dp(14), dp(16), dp(14));
        playersText.setBackgroundResource(R.drawable.bg_white_translucent_stroke);
        root.addView(playersText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        MaterialButton copyButton = new MaterialButton(this);
        copyButton.setText("SAO CHÉP MÃ PHÒNG");
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
        );
        copyParams.setMargins(0, dp(18), 0, 0);
        root.addView(copyButton, copyParams);
        copyButton.setOnClickListener(v -> copyRoomCode());

        MaterialButton inviteButton = new MaterialButton(this);
        inviteButton.setText("MỜI BẠN BÈ");
        LinearLayout.LayoutParams inviteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
        );
        inviteParams.setMargins(0, dp(10), 0, 0);
        root.addView(inviteButton, inviteParams);
        inviteButton.setOnClickListener(v -> inviteFriendsToRoom());

        if (currentUid != null && currentUid.equals(friendHostId)) {
            MaterialButton startButton = new MaterialButton(this);
            startButton.setText("BẮT ĐẦU");
            startButton.setTextSize(17);
            startButton.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(58)
            );
            startParams.setMargins(0, dp(18), 0, 0);
            root.addView(startButton, startParams);
            startButton.setOnClickListener(v -> startFriendBattleGame());
        } else {
            TextView waitText = new TextView(this);
            waitText.setText("Đang chờ trưởng phòng bắt đầu...");
            waitText.setTextColor(getColor(R.color.text_secondary));
            waitText.setGravity(Gravity.CENTER);
            waitText.setPadding(0, dp(18), 0, 0);
            root.addView(waitText, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }

        binding.gameContainer.addView(scrollView);
    }

    private String buildPlayersText(boolean includeScore) {
        if (friendPlayers.isEmpty()) return "Chưa có người chơi";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < friendPlayers.size(); i++) {
            Map<String, Object> player = friendPlayers.get(i);
            if (i > 0) sb.append("\n");
            sb.append(i + 1).append(". ").append(safe((String) player.get("name")));
            if (safe((String) player.get("uid")).equals(friendHostId)) sb.append(" (Trưởng phòng)");
            if (includeScore) sb.append(" - ").append(getPlayerScore(player)).append(" điểm");
        }
        return sb.toString();
    }

    private int getPlayerScore(Map<String, Object> player) {
        Object score = player.get("score");
        return score instanceof Number ? ((Number) score).intValue() : 0;
    }

    private void copyRoomCode() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Mã phòng VocabMaster", friendRoomCode));
            Toast.makeText(this, "Đã sao chép mã phòng", Toast.LENGTH_SHORT).show();
        }
    }

    private void inviteFriendsToRoom() {
        if (currentUid == null || friendRoomCode == null) return;
        firestore.collection("users").document(currentUid).collection("following").get()
                .addOnSuccessListener(followingSnap ->
                        firestore.collection("users").document(currentUid).collection("followers").get()
                                .addOnSuccessListener(followerSnap -> {
                                    List<String> followingIds = new ArrayList<>();
                                    for (DocumentSnapshot doc : followingSnap.getDocuments()) followingIds.add(doc.getId());
                                    List<String> friendIds = new ArrayList<>();
                                    for (DocumentSnapshot doc : followerSnap.getDocuments()) {
                                        if (followingIds.contains(doc.getId())) friendIds.add(doc.getId());
                                    }
                                    if (friendIds.isEmpty()) {
                                        Toast.makeText(this, "Bạn chưa có bạn bè để mời", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    sendFriendGameInvites(friendIds);
                                }))
                .addOnFailureListener(e -> Toast.makeText(this, "Không tải được danh sách bạn bè", Toast.LENGTH_SHORT).show());
    }

    private void sendFriendGameInvites(List<String> friendIds) {
        WriteBatch batch = firestore.batch();
        for (String friendId : friendIds) {
            Notification notification = new Notification(
                    "game_invite",
                    "Lời mời chơi Đấu Từ Nhanh",
                    currentPlayerName() + " mời bạn vào phòng " + friendRoomCode + ". Hãy nhập mã này trong Chơi với bạn bè.",
                    currentUid,
                    currentPlayerName()
            );
            batch.set(firestore.collection("users").document(friendId).collection("notifications").document(), notification);
        }
        batch.commit()
                .addOnSuccessListener(v -> Toast.makeText(this, "Đã gửi lời mời cho " + friendIds.size() + " bạn bè", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Không gửi được lời mời", Toast.LENGTH_SHORT).show());
    }

    private void startFriendBattleGame() {
        if (friendRoomId == null || !currentUid.equals(friendHostId)) return;
        if (friendPlayers.isEmpty()) {
            Toast.makeText(this, "Cần ít nhất 1 người chơi", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, Object> update = new HashMap<>();
        update.put("status", "playing");
        update.put("currentQuestionIndex", 0);
        update.put("roundStartedAt", FieldValue.serverTimestamp());
        firestore.collection("friend_game_rooms").document(friendRoomId)
                .update(update)
                .addOnFailureListener(e -> Toast.makeText(this, "Không bắt đầu được game", Toast.LENGTH_SHORT).show());
    }

    private void loadFriendQuestion(int questionIndex) {
        if (questionIndex < 0 || friendRoomId == null) return;
        firestore.collection("friend_game_rooms").document(friendRoomId)
                .collection("questions").document(String.valueOf(questionIndex))
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) return;
                    friendCurrentQuestion = new HashMap<>(snapshot.getData() != null ? snapshot.getData() : new HashMap<>());
                    renderFriendBattleQuestion();
                });
    }

    private void renderFriendBattleQuestion() {
        isFriendBattleLobbyVisible = false;
        if (friendBattleTimer != null) friendBattleTimer.cancel();
        dismissFriendRoundRankingDialog();
        friendHintEngShown = false;
        friendHintViShown = false;
        friendHint50Shown = false;
        friendHint75Shown = false;
        friendCurrentPointsPossible = 5;
        friendAnsweredCurrentQuestion = currentPlayerAnsweredQuestion();

        View view = getLayoutInflater().inflate(R.layout.layout_game_ai_riddle, binding.gameContainer, false);
        binding.gameContainer.removeAllViews();
        binding.gameContainer.addView(view);

        friendTimerText = view.findViewById(R.id.text_timer);
        friendTimerProgress = view.findViewById(R.id.progress_timer);
        friendPromptText = view.findViewById(R.id.text_riddle_hint);
        friendEnglishHintText = view.findViewById(R.id.text_english_hint);
        friendVietnameseHintText = view.findViewById(R.id.text_vietnamese_hint);
        friendPlaceholderText = view.findViewById(R.id.text_answer_placeholders);
        friendLetterCountText = view.findViewById(R.id.text_letter_count);
        friendAnswerEdit = view.findViewById(R.id.edit_answer);
        friendSubmitButton = view.findViewById(R.id.btn_submit_answer);

        String word = safe((String) friendCurrentQuestion.get("word")).toLowerCase(Locale.US);
        String example = safe((String) friendCurrentQuestion.get("example"));
        String definition = safe((String) friendCurrentQuestion.get("definition"));
        String prompt = example.isEmpty() ? definition : example;
        if (prompt.isEmpty()) prompt = "Từ tiếng Anh nào phù hợp với gợi ý này?";
        prompt = prompt.replaceAll("(?i)" + Pattern.quote(word), "_______");
        if (!prompt.contains("_______")) prompt = "Từ tiếng Anh nào có nghĩa là: " + definition;

        friendPromptText.setText("Câu " + (friendCurrentQuestionIndex + 1) + "/" + friendQuestionCount + "\n\n" + prompt);
        friendEnglishHintText.setVisibility(View.GONE);
        friendVietnameseHintText.setVisibility(View.GONE);
        friendPlaceholderText.setText(getMaskedWord(word, 0));
        friendLetterCountText.setText("(" + word.length() + " ký tự)");
        friendSubmitButton.setText(friendAnsweredCurrentQuestion ? "ĐÃ TRẢ LỜI" : "NỘP ĐÁP ÁN");
        friendSubmitButton.setEnabled(!friendAnsweredCurrentQuestion);
        friendAnswerEdit.setEnabled(!friendAnsweredCurrentQuestion);
        friendSubmitButton.setOnClickListener(v -> submitFriendBattleAnswer());

        startFriendBattleQuestionTimer();
    }

    private boolean currentPlayerAnsweredQuestion() {
        for (Map<String, Object> player : friendPlayers) {
            if (!safe((String) player.get("uid")).equals(currentUid)) continue;
            Object answered = player.get("answeredQuestion");
            return answered instanceof Number && ((Number) answered).intValue() >= friendCurrentQuestionIndex;
        }
        return false;
    }

    private void startFriendBattleQuestionTimer() {
        int totalMillis = friendQuestionTimeSeconds * 1000;
        int elapsedMillis = (int) Math.max(0, System.currentTimeMillis() - friendRoundStartedAtMillis);
        int remainingMillis = Math.max(0, totalMillis - elapsedMillis);
        friendTimerProgress.setMax(totalMillis / 100);
        friendTimerProgress.setProgress(remainingMillis / 100);

        friendBattleTimer = new CountDownTimer(remainingMillis, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                int remainingSeconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                friendTimerText.setText(String.valueOf(remainingSeconds));
                friendTimerProgress.setProgress((int) (millisUntilFinished / 100));
                updateFriendBattleHints(totalMillis - (int) millisUntilFinished, totalMillis);
            }

            @Override
            public void onFinish() {
                friendTimerText.setText("0");
                friendTimerProgress.setProgress(0);
                revealFriendBattleAnswer();
                new Handler(Looper.getMainLooper()).postDelayed(() -> showFriendBattleRoundRanking(), 500);
                if (currentUid != null && currentUid.equals(friendHostId)) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> advanceFriendBattleQuestion(), 3000);
                }
            }
        }.start();
    }

    private void revealFriendBattleAnswer() {
        String word = safe((String) friendCurrentQuestion.get("word"));
        if (word.isEmpty()) return;

        String definition = safe((String) friendCurrentQuestion.get("definition"));
        String vietnamese = safe((String) friendCurrentQuestion.get("vietnamese"));

        if (friendEnglishHintText != null && !definition.isEmpty()) {
            friendEnglishHintText.setVisibility(View.VISIBLE);
            friendEnglishHintText.setText("Hint (English): " + definition);
        }
        if (friendVietnameseHintText != null) {
            friendVietnameseHintText.setVisibility(View.VISIBLE);
            friendVietnameseHintText.setText("Gợi ý (Tiếng Việt): "
                    + (vietnamese.isEmpty() ? "Chưa có bản dịch" : vietnamese));
        }
        if (friendPlaceholderText != null) {
            friendPlaceholderText.setText("Đáp án: " + word);
        }
        if (friendLetterCountText != null) {
            friendLetterCountText.setText("Hết giờ");
        }
        if (friendSubmitButton != null) {
            friendSubmitButton.setEnabled(false);
            friendSubmitButton.setText("HẾT GIỜ");
        }
        if (friendAnswerEdit != null) {
            friendAnswerEdit.setEnabled(false);
        }
    }

    private void showFriendBattleRoundRanking() {
        if (!friendRoomStatus.equals("playing")) return;
        if (friendRoundRankingDialog == null) {
            friendRoundRankingDialog = new BottomSheetDialog(this);
            friendRoundRankingDialog.setCancelable(false);

            ScrollView scrollView = new ScrollView(this);
            scrollView.setFillViewport(false);
            friendRoundRankingContent = new LinearLayout(this);
            friendRoundRankingContent.setOrientation(LinearLayout.VERTICAL);
            friendRoundRankingContent.setPadding(dp(24), dp(18), dp(24), dp(22));
            friendRoundRankingContent.setBackground(makeRoundRect(Color.WHITE, dp(22)));
            scrollView.addView(friendRoundRankingContent, new ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT,
                    ScrollView.LayoutParams.WRAP_CONTENT
            ));
            friendRoundRankingDialog.setContentView(scrollView);
        }
        renderFriendBattleRoundRanking();
        if (!friendRoundRankingDialog.isShowing()) {
            friendRoundRankingDialog.show();
        }
    }

    private void renderFriendBattleRoundRanking() {
        if (friendRoundRankingContent == null) return;
        friendRoundRankingContent.removeAllViews();

        TextView title = sectionLabel("Xếp hạng hiện tại");
        title.setGravity(Gravity.CENTER);
        friendRoundRankingContent.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitle = new TextView(this);
        subtitle.setText("Chuẩn bị sang câu tiếp theo...");
        subtitle.setTextColor(getColor(R.color.text_secondary));
        subtitle.setTextSize(13);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(4), 0, dp(6));
        friendRoundRankingContent.addView(subtitle, subtitleParams);

        List<Map<String, Object>> ranking = new ArrayList<>(friendPlayers);
        ranking.sort((a, b) -> Integer.compare(getPlayerScore(b), getPlayerScore(a)));
        if (ranking.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Đang cập nhật điểm...");
            empty.setTextColor(getColor(R.color.text_secondary));
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(10), 0, 0);
            friendRoundRankingContent.addView(empty);
            return;
        }

        for (int i = 0; i < ranking.size(); i++) {
            addFriendRoundRankingRow(friendRoundRankingContent, ranking.get(i), i + 1);
        }
    }

    private void dismissFriendRoundRankingDialog() {
        if (friendRoundRankingDialog != null) {
            friendRoundRankingDialog.dismiss();
            friendRoundRankingDialog = null;
        }
        friendRoundRankingContent = null;
    }

    private void addFriendRoundRankingRow(LinearLayout container, Map<String, Object> player, int rank) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);
        container.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView rankText = new TextView(this);
        rankText.setText("#" + rank);
        rankText.setTextColor(rank == 1 ? getColor(R.color.warning) : getColor(R.color.text_secondary));
        rankText.setTextSize(15);
        rankText.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(rankText, new LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView nameText = new TextView(this);
        nameText.setText(getPlayerName(player));
        nameText.setTextColor(getColor(R.color.text_primary));
        nameText.setTextSize(15);
        nameText.setSingleLine(true);
        row.addView(nameText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView scoreText = new TextView(this);
        scoreText.setText(getPlayerScore(player) + " điểm");
        scoreText.setTextColor(getColor(R.color.brand_primary));
        scoreText.setTextSize(15);
        scoreText.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(scoreText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private void updateFriendBattleHints(int elapsedMillis, int totalMillis) {
        String word = safe((String) friendCurrentQuestion.get("word")).toLowerCase(Locale.US);
        if (elapsedMillis >= totalMillis * 0.25f && !friendHintEngShown) {
            friendHintEngShown = true;
            friendCurrentPointsPossible = 4;
            friendEnglishHintText.setVisibility(View.VISIBLE);
            friendEnglishHintText.setText("Hint (English): " + safe((String) friendCurrentQuestion.get("definition")));
        }
        if (elapsedMillis >= totalMillis * 0.50f && !friendHintViShown) {
            friendHintViShown = true;
            friendCurrentPointsPossible = 3;
            String vi = safe((String) friendCurrentQuestion.get("vietnamese"));
            friendVietnameseHintText.setVisibility(View.VISIBLE);
            friendVietnameseHintText.setText("Gợi ý (Tiếng Việt): " + (vi.isEmpty() ? "Chưa có bản dịch" : vi));
        }
        if (elapsedMillis >= totalMillis * 0.70f && !friendHint50Shown) {
            friendHint50Shown = true;
            friendCurrentPointsPossible = 2;
            friendPlaceholderText.setText(getMaskedWord(word, 50));
        }
        if (elapsedMillis >= totalMillis * 0.85f && !friendHint75Shown) {
            friendHint75Shown = true;
            friendCurrentPointsPossible = 1;
            friendPlaceholderText.setText(getMaskedWord(word, 75));
        }
    }

    private void submitFriendBattleAnswer() {
        if (friendAnsweredCurrentQuestion || friendRoomId == null || currentUid == null) return;
        String answer = friendAnswerEdit.getText() == null ? "" : friendAnswerEdit.getText().toString().trim();
        String word = safe((String) friendCurrentQuestion.get("word"));
        friendAnsweredCurrentQuestion = true;
        friendSubmitButton.setEnabled(false);
        friendAnswerEdit.setEnabled(false);

        Map<String, Object> update = new HashMap<>();
        update.put("answeredQuestion", friendCurrentQuestionIndex);
        update.put("lastAnswer", answer);
        update.put("lastPoints", 0);

        boolean isCorrect = answer.equalsIgnoreCase(word);
        if (isCorrect) {
            update.put("score", FieldValue.increment(friendCurrentPointsPossible));
            update.put("lastPoints", friendCurrentPointsPossible);
            playSoundEffect(true);
            UiFeedback.showSnack(binding.getRoot(), "Chính xác! +" + friendCurrentPointsPossible + " điểm");
        } else {
            playSoundEffect(false);
            UiFeedback.showSnack(binding.getRoot(), "Chưa đúng rồi");
        }

        firestore.collection("friend_game_rooms").document(friendRoomId)
                .collection("players").document(currentUid)
                .set(update, SetOptions.merge());
        friendSubmitButton.setText("ĐÃ TRẢ LỜI");
    }

    private void advanceFriendBattleQuestion() {
        if (friendRoomId == null || !currentUid.equals(friendHostId) || !friendRoomStatus.equals("playing")) return;
        dismissFriendRoundRankingDialog();
        if (friendCurrentQuestionIndex + 1 >= friendQuestionCount) {
            firestore.collection("friend_game_rooms").document(friendRoomId)
                    .update("status", "finished", "finishedAt", FieldValue.serverTimestamp());
            return;
        }
        Map<String, Object> update = new HashMap<>();
        update.put("currentQuestionIndex", friendCurrentQuestionIndex + 1);
        update.put("roundStartedAt", FieldValue.serverTimestamp());
        firestore.collection("friend_game_rooms").document(friendRoomId).update(update);
    }

    private void renderFriendBattleResults() {
        if (friendBattleTimer != null) friendBattleTimer.cancel();
        dismissFriendRoundRankingDialog();
        binding.gameContainer.removeAllViews();
        binding.textTitle.setText("Tổng kết");

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("BẢNG XẾP HẠNG");
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout podium = new LinearLayout(this);
        podium.setOrientation(LinearLayout.HORIZONTAL);
        podium.setGravity(Gravity.BOTTOM);
        podium.setClipChildren(false);
        podium.setClipToPadding(false);
        LinearLayout.LayoutParams podiumParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(252)
        );
        podiumParams.setMargins(0, dp(18), 0, dp(18));
        root.addView(podium, podiumParams);

        addPodiumStep(podium, 2, getPlayerAtRank(2), dp(150), "#CBD5E1");
        addPodiumStep(podium, 1, getPlayerAtRank(1), dp(190), "#F59E0B");
        addPodiumStep(podium, 3, getPlayerAtRank(3), dp(130), "#D97706");

        addRemainingRankingList(root);

        MaterialButton backButton = new MaterialButton(this);
        backButton.setText("VỀ CHỌN CHẾ ĐỘ");
        backButton.setTextSize(16);
        root.addView(backButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
        ));
        backButton.setOnClickListener(v -> showFriendBattleHome());

        binding.gameContainer.addView(scrollView);
    }

    private Map<String, Object> getPlayerAtRank(int rank) {
        int index = rank - 1;
        if (index < 0 || index >= friendPlayers.size()) return null;
        return friendPlayers.get(index);
    }

    private void addPodiumStep(LinearLayout podium, int rank, Map<String, Object> player, int stepHeight, String colorHex) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        wrapperParams.setMargins(dp(4), 0, dp(4), 0);
        podium.addView(wrapper, wrapperParams);
        if (player == null) {
            wrapper.setVisibility(View.INVISIBLE);
            return;
        }

        TextView medal = new TextView(this);
        medal.setText(String.valueOf(rank));
        medal.setTextColor(Color.WHITE);
        medal.setTextSize(rank == 1 ? 22 : 18);
        medal.setTypeface(Typeface.DEFAULT_BOLD);
        medal.setGravity(Gravity.CENTER);
        medal.setBackground(makeRoundRect(Color.parseColor(colorHex), dp(18)));
        LinearLayout.LayoutParams medalParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        medalParams.setMargins(0, 0, 0, dp(8));
        wrapper.addView(medal, medalParams);

        LinearLayout step = new LinearLayout(this);
        step.setOrientation(LinearLayout.VERTICAL);
        step.setGravity(Gravity.CENTER);
        step.setPadding(dp(8), dp(10), dp(8), dp(10));
        step.setBackground(makeRoundRect(Color.WHITE, dp(16), getColor(R.color.card_border)));
        wrapper.addView(step, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                stepHeight
        ));

        TextView name = new TextView(this);
        name.setText(getPlayerName(player));
        name.setTextColor(getColor(R.color.text_primary));
        name.setTextSize(rank == 1 ? 16 : 14);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(false);
        step.addView(name, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView score = new TextView(this);
        score.setText(getPlayerScore(player) + " điểm");
        score.setTextColor(rank == 1 ? getColor(R.color.warning) : getColor(R.color.brand_primary));
        score.setTextSize(rank == 1 ? 18 : 15);
        score.setTypeface(Typeface.DEFAULT_BOLD);
        score.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams scoreParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        scoreParams.setMargins(0, dp(8), 0, 0);
        step.addView(score, scoreParams);
    }

    private void addRemainingRankingList(LinearLayout root) {
        if (friendPlayers.size() <= 3) return;

        TextView listTitle = sectionLabel("Các hạng còn lại");
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, dp(6), 0, dp(8));
        root.addView(listTitle, titleParams);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(8), dp(12), dp(8));
        list.setBackground(makeRoundRect(Color.WHITE, dp(16), getColor(R.color.card_border)));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        listParams.setMargins(0, 0, 0, dp(24));
        root.addView(list, listParams);

        for (int i = 3; i < friendPlayers.size(); i++) {
            Map<String, Object> player = friendPlayers.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            list.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            TextView rankText = new TextView(this);
            rankText.setText("#" + (i + 1));
            rankText.setTextColor(getColor(R.color.text_secondary));
            rankText.setTextSize(15);
            rankText.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(rankText, new LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView nameText = new TextView(this);
            nameText.setText(getPlayerName(player));
            nameText.setTextColor(getColor(R.color.text_primary));
            nameText.setTextSize(16);
            row.addView(nameText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView scoreText = new TextView(this);
            scoreText.setText(getPlayerScore(player) + " điểm");
            scoreText.setTextColor(getColor(R.color.brand_primary));
            scoreText.setTextSize(16);
            scoreText.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(scoreText, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }
    }

    private String getPlayerName(Map<String, Object> player) {
        String name = safe((String) player.get("name"));
        return name.isEmpty() ? "Người chơi" : name;
    }

    private GradientDrawable makeRoundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable makeRoundRect(int color, int radius, int strokeColor) {
        GradientDrawable drawable = makeRoundRect(color, radius);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void stopFriendBattleSession(boolean clearRoomState) {
        if (friendBattleTimer != null) {
            friendBattleTimer.cancel();
            friendBattleTimer = null;
        }
        if (friendRoomListener != null) {
            friendRoomListener.remove();
            friendRoomListener = null;
        }
        if (friendPlayersListener != null) {
            friendPlayersListener.remove();
            friendPlayersListener = null;
        }
        if (clearRoomState) {
            friendRoomId = null;
            friendRoomCode = null;
            friendHostId = null;
            friendRoomStatus = "waiting";
            friendCurrentQuestionIndex = -1;
            friendPlayers.clear();
            friendCurrentQuestion.clear();
            dismissFriendRoundRankingDialog();
        }
    }

    private static class TopicChoice {
        final String id;
        final String name;

        TopicChoice(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }




    // --- AI RIDDLE MODE ---
    private void showAiRiddleLobby() {
        isAiRiddleMode = true;
        isAiRiddleLobbyVisible = true;
        isSentenceScrambleMode = false;
        isSentenceScrambleLobbyVisible = false;
        isLetterScrambleMode = false;
        isLetterScrambleLobbyVisible = false;
        isLightningMode = false;
        isLightningLobbyVisible = false;
        isFriendBattleMode = false;
        isFriendBattleLobbyVisible = false;
        binding.textTitle.setText("Đố vui cùng AI");
        binding.layoutAiModes.setVisibility(View.GONE);
        View lobby = getLayoutInflater().inflate(R.layout.layout_game_ai_riddle_lobby, binding.gameContainer, false);
        binding.gameContainer.removeAllViews();
        binding.gameContainer.setVisibility(View.VISIBLE);
        binding.gameContainer.addView(lobby);

        SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
        int highScore = prefs.getInt("ai_riddle_high_score", 0);
        String history = prefs.getString("ai_riddle_history", "Chưa có dữ liệu");

        ((TextView)lobby.findViewById(R.id.text_high_score)).setText("Kỉ lục: " + highScore + " điểm");
        ((TextView)lobby.findViewById(R.id.text_history)).setText(history);

        lobby.findViewById(R.id.btn_start_riddle).setOnClickListener(v -> startAiRiddleGame());
    }

    private void startAiRiddleGame() {
        spendHeartBeforeQuickGame(this::beginAiRiddleGame);
    }

    // --- SENTENCE SCRAMBLE MODE ---
    private void showSentenceScrambleLobby() {
        if (sentenceScrambleTimer != null) sentenceScrambleTimer.cancel();
        isSentenceScrambleMode = true;
        isSentenceScrambleLobbyVisible = true;
        isAiRiddleMode = false;
        isAiRiddleLobbyVisible = false;
        isLetterScrambleMode = false;
        isLetterScrambleLobbyVisible = false;
        isLightningMode = false;
        isLightningLobbyVisible = false;
        isFriendBattleMode = false;
        isFriendBattleLobbyVisible = false;
        binding.textTitle.setText("Sắp xếp câu");
        binding.layoutAiModes.setVisibility(View.GONE);
        View lobby = getLayoutInflater().inflate(R.layout.layout_game_sentence_scramble_lobby, binding.gameContainer, false);
        binding.gameContainer.removeAllViews();
        binding.gameContainer.setVisibility(View.VISIBLE);
        binding.gameContainer.addView(lobby);

        SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
        int highScore = prefs.getInt("sentence_scramble_high_score", 0);
        String history = prefs.getString("sentence_scramble_history", "Chưa có dữ liệu");

        ((TextView) lobby.findViewById(R.id.text_sentence_high_score)).setText("Kỉ lục: " + highScore + " câu");
        ((TextView) lobby.findViewById(R.id.text_sentence_history)).setText(history);

        lobby.findViewById(R.id.btn_start_sentence_scramble).setOnClickListener(v -> startSentenceScrambleGame());
        updateScoreUI();
    }

    private void startSentenceScrambleGame() {
        spendHeartBeforeQuickGame(() -> {
            sentenceScrambleScore = 0;
            updateScoreUI();
            fetchAndShowNextSentence();
        });
    }

    private void fetchAndShowNextSentence() {
        if (sentenceScrambleTimer != null) sentenceScrambleTimer.cancel();
        isSentenceScrambleLobbyVisible = false;
        binding.gameContainer.setVisibility(View.VISIBLE);

        new Thread(() -> {
            List<Vocabulary> vocabs = AppDatabase.getDatabase(this).vocabularyDao().getRandomLearnedVocabularies(30);
            if (vocabs.isEmpty()) {
                vocabs = AppDatabase.getDatabase(this).vocabularyDao().getRandomVocabularies(30);
            }

            Vocabulary selectedVocab = null;
            for (Vocabulary vocab : vocabs) {
                String sentence = safe(vocab.getExample_sentence());
                if (isUsableSentence(sentence)) {
                    selectedVocab = vocab;
                    break;
                }
            }

            Vocabulary finalVocab = selectedVocab;
            runOnUiThread(() -> {
                if (finalVocab == null) {
                    Toast.makeText(this, "Không tìm thấy câu ví dụ phù hợp!", Toast.LENGTH_SHORT).show();
                    showSentenceScrambleLobby();
                } else {
                    displaySentenceScrambleQuestion(finalVocab);
                }
            });
        }).start();
    }

    private boolean isUsableSentence(String sentence) {
        List<String> words = extractSentenceWords(sentence);
        return words.size() >= 3 && words.size() <= 12;
    }

    private void displaySentenceScrambleQuestion(Vocabulary vocab) {
        isSentenceScrambleLobbyVisible = false;
        String sentence = safe(vocab.getExample_sentence());
        sentenceCorrectWords.clear();
        sentenceCorrectWords.addAll(extractSentenceWords(sentence));
        sentenceBoardWords.clear();
        sentenceHandWords.clear();
        sentenceHandWords.addAll(sentenceCorrectWords);
        Collections.shuffle(sentenceHandWords);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(20), dp(24), dp(24));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout timerWrap = new LinearLayout(this);
        timerWrap.setOrientation(LinearLayout.VERTICAL);
        timerWrap.setGravity(Gravity.CENTER);
        root.addView(timerWrap, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        sentenceTimerText = new TextView(this);
        sentenceTimerText.setText("30");
        sentenceTimerText.setTextColor(getColor(R.color.brand_primary));
        sentenceTimerText.setTextSize(24);
        sentenceTimerText.setTypeface(Typeface.DEFAULT_BOLD);
        sentenceTimerText.setGravity(Gravity.CENTER);
        timerWrap.addView(sentenceTimerText, new LinearLayout.LayoutParams(dp(64), dp(48)));

        sentenceTimerProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        sentenceTimerProgress.setMax(SENTENCE_TIME_LIMIT / 100);
        sentenceTimerProgress.setProgress(SENTENCE_TIME_LIMIT / 100);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
        );
        progressParams.setMargins(0, dp(8), 0, dp(24));
        timerWrap.addView(sentenceTimerProgress, progressParams);

        TextView meaningTitle = sectionLabel("Nghĩa tiếng Việt");
        root.addView(meaningTitle);

        TextView meaningText = new TextView(this);
        String vietnameseHint = safe(vocab.getVietnamese_translation());
        if (vietnameseHint.isEmpty()) vietnameseHint = safe(vocab.getVietnameseTranslation());
        if (vietnameseHint.isEmpty()) vietnameseHint = "Chưa có nghĩa tiếng Việt cho câu này";
        meaningText.setText(vietnameseHint);
        meaningText.setTextColor(getColor(R.color.text_primary));
        meaningText.setTextSize(18);
        meaningText.setGravity(Gravity.CENTER);
        meaningText.setLineSpacing(dp(4), 1f);
        meaningText.setPadding(dp(16), dp(14), dp(16), dp(14));
        meaningText.setBackgroundResource(R.drawable.bg_white_translucent_stroke);
        LinearLayout.LayoutParams meaningParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        meaningParams.setMargins(0, dp(8), 0, dp(20));
        root.addView(meaningText, meaningParams);

        TextView boardTitle = sectionLabel("Bảng sắp xếp");
        root.addView(boardTitle);

        sentenceBoardContainer = wordArea();
        root.addView(sentenceBoardContainer, areaParams());

        TextView handTitle = sectionLabel("Trên tay");
        LinearLayout.LayoutParams handTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        handTitleParams.setMargins(0, dp(18), 0, dp(8));
        root.addView(handTitle, handTitleParams);

        sentenceHandContainer = wordArea();
        root.addView(sentenceHandContainer, areaParams());

        MaterialButton submitButton = new MaterialButton(this);
        submitButton.setText("NỘP BÀI");
        submitButton.setTextSize(16);
        submitButton.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
        );
        submitParams.setMargins(0, dp(24), 0, 0);
        root.addView(submitButton, submitParams);
        submitButton.setOnClickListener(v -> submitSentenceScramble());

        binding.gameContainer.removeAllViews();
        binding.gameContainer.addView(scrollView);
        renderSentenceWordAreas();
        startSentenceScrambleTimer();
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(getColor(R.color.text_primary));
        label.setTextSize(16);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        return label;
    }

    private LinearLayout wordArea() {
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        area.setPadding(dp(8), dp(8), dp(8), dp(8));
        area.setBackgroundResource(R.drawable.bg_white_translucent_stroke);
        return area;
    }

    private LinearLayout.LayoutParams areaParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(8), 0, 0);
        return params;
    }

    private void renderSentenceWordAreas() {
        renderWordList(sentenceBoardContainer, sentenceBoardWords, true);
        renderWordList(sentenceHandContainer, sentenceHandWords, false);
    }

    private void renderWordList(LinearLayout container, List<String> words, boolean fromBoard) {
        container.removeAllViews();
        if (words.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(fromBoard ? "Chạm các thẻ ở dưới để đưa lên đây" : "Không còn thẻ nào");
            empty.setTextColor(getColor(R.color.text_secondary));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(12), 0, dp(12));
            container.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            return;
        }

        LinearLayout row = null;
        int rowWidth = 0;
        int maxRowWidth = getResources().getDisplayMetrics().widthPixels - dp(80);
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            int chipOuterWidth = estimateChipOuterWidth(word);
            if (row == null || rowWidth + chipOuterWidth > maxRowWidth) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                container.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                ));
                rowWidth = 0;
            }

            MaterialButton chip = new MaterialButton(this);
            chip.setText(word);
            chip.setTextSize(14);
            chip.setAllCaps(false);
            chip.setSingleLine(true);
            chip.setMinHeight(0);
            chip.setMinWidth(0);
            chip.setMinimumHeight(0);
            chip.setMinimumWidth(0);
            chip.setInsetTop(0);
            chip.setInsetBottom(0);
            chip.setPadding(dp(10), dp(6), dp(10), dp(6));
            int index = i;
            chip.setOnClickListener(v -> moveSentenceWord(index, fromBoard));

            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    Math.min(chipOuterWidth - dp(8), maxRowWidth),
                    dp(42)
            );
            chipParams.setMargins(dp(4), dp(4), dp(4), dp(4));
            row.addView(chip, chipParams);
            rowWidth += chipOuterWidth;
        }
    }

    private int estimateChipOuterWidth(String word) {
        TextView measureView = new TextView(this);
        measureView.setTextSize(14);
        float textWidth = measureView.getPaint().measureText(word);
        return (int) Math.ceil(textWidth) + dp(36);
    }

    private void moveSentenceWord(int index, boolean fromBoard) {
        if (fromBoard) {
            if (index >= 0 && index < sentenceBoardWords.size()) {
                sentenceHandWords.add(sentenceBoardWords.remove(index));
            }
        } else {
            if (index >= 0 && index < sentenceHandWords.size()) {
                sentenceBoardWords.add(sentenceHandWords.remove(index));
            }
        }
        renderSentenceWordAreas();
    }

    private void submitSentenceScramble() {
        if (sentenceBoardWords.equals(sentenceCorrectWords)) {
            if (sentenceScrambleTimer != null) sentenceScrambleTimer.cancel();
            sentenceScrambleScore += 1;
            updateScoreUI();
            playSoundEffect(true);
            UiFeedback.showSnack(binding.getRoot(), "Chính xác! +1 điểm");
            new Handler(Looper.getMainLooper()).postDelayed(this::fetchAndShowNextSentence, 800);
        } else {
            playSoundEffect(false);
            finishSentenceScrambleGame("Sai rồi! Câu đúng là:\n" + String.join(" ", sentenceCorrectWords));
        }
    }

    private void startSentenceScrambleTimer() {
        if (sentenceScrambleTimer != null) sentenceScrambleTimer.cancel();
        sentenceScrambleTimer = new CountDownTimer(SENTENCE_TIME_LIMIT, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                sentenceTimerText.setText(String.valueOf((int) (millisUntilFinished / 1000)));
                sentenceTimerProgress.setProgress((int) (millisUntilFinished / 100));
            }

            @Override
            public void onFinish() {
                finishSentenceScrambleGame("Hết giờ!");
            }
        }.start();
    }

    private void finishSentenceScrambleGame(String reason) {
        if (sentenceScrambleTimer != null) sentenceScrambleTimer.cancel();

        SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
        int oldHighScore = prefs.getInt("sentence_scramble_high_score", 0);
        boolean isNewRecord = sentenceScrambleScore > oldHighScore;
        if (isNewRecord) {
            prefs.edit().putInt("sentence_scramble_high_score", sentenceScrambleScore).apply();
        }

        String history = prefs.getString("sentence_scramble_history", "");
        String newEntry = "Điểm: " + sentenceScrambleScore + " - " + new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date());
        if (history.isEmpty() || history.equals("Chưa có dữ liệu")) history = newEntry;
        else {
            String[] lines = history.split("\n");
            StringBuilder sb = new StringBuilder(newEntry);
            for (int i = 0; i < Math.min(lines.length, 4); i++) {
                sb.append("\n").append(lines[i]);
            }
            history = sb.toString();
        }
        prefs.edit().putString("sentence_scramble_history", history).apply();

        String msg = reason + "\n\nBạn xếp đúng " + sentenceScrambleScore + " câu.";
        if (isNewRecord && sentenceScrambleScore > 0) msg += "\n\nCHÚC MỪNG! BẠN ĐÃ PHÁ KỈ LỤC!";

        new AlertDialog.Builder(this)
                .setTitle("Game Over")
                .setMessage(msg)
                .setPositiveButton("Về sảnh", (dialog, which) -> showSentenceScrambleLobby())
                .setCancelable(false)
                .show();
    }

    private List<String> extractSentenceWords(String sentence) {
        List<String> words = new ArrayList<>();
        Matcher matcher = SENTENCE_WORD_PATTERN.matcher(safe(sentence));
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return words;
    }

    // --- LETTER SCRAMBLE MODE ---
    private void showLetterScrambleLobby() {
        if (letterScrambleTimer != null) letterScrambleTimer.cancel();
        isLetterScrambleMode = true;
        isLetterScrambleLobbyVisible = true;
        isAiRiddleMode = false;
        isAiRiddleLobbyVisible = false;
        isSentenceScrambleMode = false;
        isSentenceScrambleLobbyVisible = false;
        isLightningMode = false;
        isLightningLobbyVisible = false;
        isFriendBattleMode = false;
        isFriendBattleLobbyVisible = false;
        binding.textTitle.setText("Sắp xếp chữ");
        binding.layoutAiModes.setVisibility(View.GONE);
        View lobby = getLayoutInflater().inflate(R.layout.layout_game_letter_scramble_lobby, binding.gameContainer, false);
        binding.gameContainer.removeAllViews();
        binding.gameContainer.setVisibility(View.VISIBLE);
        binding.gameContainer.addView(lobby);

        SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
        int highScore = prefs.getInt("letter_scramble_high_score", 0);
        String history = prefs.getString("letter_scramble_history", "Chưa có dữ liệu");

        ((TextView) lobby.findViewById(R.id.text_letter_high_score)).setText("Kỉ lục: " + highScore + " từ");
        ((TextView) lobby.findViewById(R.id.text_letter_history)).setText(history);

        lobby.findViewById(R.id.btn_start_letter_scramble).setOnClickListener(v -> startLetterScrambleGame());
        updateScoreUI();
    }

    private void startLetterScrambleGame() {
        spendHeartBeforeQuickGame(() -> {
            letterScrambleScore = 0;
            updateScoreUI();
            fetchAndShowNextLetterWord();
        });
    }

    private void fetchAndShowNextLetterWord() {
        if (letterScrambleTimer != null) letterScrambleTimer.cancel();
        isLetterScrambleLobbyVisible = false;
        binding.gameContainer.setVisibility(View.VISIBLE);

        new Thread(() -> {
            List<Vocabulary> vocabs = AppDatabase.getDatabase(this).vocabularyDao().getRandomLearnedVocabularies(30);
            if (vocabs.isEmpty()) {
                vocabs = AppDatabase.getDatabase(this).vocabularyDao().getRandomVocabularies(30);
            }

            Vocabulary selectedVocab = null;
            for (Vocabulary vocab : vocabs) {
                if (isUsableLetterWord(vocab)) {
                    selectedVocab = vocab;
                    break;
                }
            }

            Vocabulary finalVocab = selectedVocab;
            runOnUiThread(() -> {
                if (finalVocab == null) {
                    Toast.makeText(this, "Không tìm thấy từ phù hợp để xếp chữ!", Toast.LENGTH_SHORT).show();
                    showLetterScrambleLobby();
                } else {
                    displayLetterScrambleQuestion(finalVocab);
                }
            });
        }).start();
    }

    private boolean isUsableLetterWord(Vocabulary vocab) {
        return vocab != null && !normalizeLetterAnswer(vocab.getWord()).isEmpty();
    }

    private String normalizeLetterAnswer(String word) {
        String value = safe(word);
        if (!LETTER_WORD_PATTERN.matcher(value).matches()) return "";
        return value.toLowerCase(Locale.US);
    }

    private void displayLetterScrambleQuestion(Vocabulary vocab) {
        isLetterScrambleLobbyVisible = false;
        currentLetterAnswer = normalizeLetterAnswer(vocab.getWord());
        letterBoardItems.clear();
        letterHandItems.clear();
        for (int i = 0; i < currentLetterAnswer.length(); i++) {
            letterHandItems.add(String.valueOf(currentLetterAnswer.charAt(i)).toUpperCase(Locale.US));
        }
        Collections.shuffle(letterHandItems);
        if (String.join("", letterHandItems).equalsIgnoreCase(currentLetterAnswer) && letterHandItems.size() > 3) {
            Collections.shuffle(letterHandItems);
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(20), dp(24), dp(24));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout timerWrap = new LinearLayout(this);
        timerWrap.setOrientation(LinearLayout.VERTICAL);
        timerWrap.setGravity(Gravity.CENTER);
        root.addView(timerWrap, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        letterTimerText = new TextView(this);
        letterTimerText.setText("30");
        letterTimerText.setTextColor(getColor(R.color.brand_primary));
        letterTimerText.setTextSize(24);
        letterTimerText.setTypeface(Typeface.DEFAULT_BOLD);
        letterTimerText.setGravity(Gravity.CENTER);
        timerWrap.addView(letterTimerText, new LinearLayout.LayoutParams(dp(64), dp(48)));

        letterTimerProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        letterTimerProgress.setMax(LETTER_TIME_LIMIT / 100);
        letterTimerProgress.setProgress(LETTER_TIME_LIMIT / 100);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
        );
        progressParams.setMargins(0, dp(8), 0, dp(24));
        timerWrap.addView(letterTimerProgress, progressParams);

        TextView englishTitle = sectionLabel("Nghĩa tiếng Anh");
        root.addView(englishTitle);

        TextView englishText = hintBox(safe(vocab.getDefinition()).isEmpty()
                ? "Chưa có nghĩa tiếng Anh cho từ này"
                : safe(vocab.getDefinition()));
        root.addView(englishText, hintBoxParams());

        TextView vietnameseTitle = sectionLabel("Nghĩa tiếng Việt");
        LinearLayout.LayoutParams vietnameseTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        vietnameseTitleParams.setMargins(0, dp(12), 0, 0);
        root.addView(vietnameseTitle, vietnameseTitleParams);

        String vietnameseHint = safe(vocab.getVietnamese_translation());
        if (vietnameseHint.isEmpty()) vietnameseHint = safe(vocab.getVietnameseTranslation());
        TextView vietnameseText = hintBox(vietnameseHint.isEmpty()
                ? "Chưa có nghĩa tiếng Việt cho từ này"
                : vietnameseHint);
        root.addView(vietnameseText, hintBoxParams());

        TextView letterCount = new TextView(this);
        letterCount.setText("Số chữ: " + currentLetterAnswer.length());
        letterCount.setTextColor(getColor(R.color.text_secondary));
        letterCount.setGravity(Gravity.CENTER);
        letterCount.setTextSize(15);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        countParams.setMargins(0, dp(12), 0, dp(16));
        root.addView(letterCount, countParams);

        TextView boardTitle = sectionLabel("Bảng trả lời");
        root.addView(boardTitle);

        letterBoardContainer = wordArea();
        root.addView(letterBoardContainer, areaParams());

        TextView handTitle = sectionLabel("Chữ cái");
        LinearLayout.LayoutParams handTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        handTitleParams.setMargins(0, dp(18), 0, dp(8));
        root.addView(handTitle, handTitleParams);

        letterHandContainer = wordArea();
        root.addView(letterHandContainer, areaParams());

        MaterialButton submitButton = new MaterialButton(this);
        submitButton.setText("NỘP BÀI");
        submitButton.setTextSize(16);
        submitButton.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
        );
        submitParams.setMargins(0, dp(24), 0, 0);
        root.addView(submitButton, submitParams);
        submitButton.setOnClickListener(v -> submitLetterScramble());

        binding.gameContainer.removeAllViews();
        binding.gameContainer.addView(scrollView);
        renderLetterScrambleAreas();
        startLetterScrambleTimer();
    }

    private TextView hintBox(String text) {
        TextView hint = new TextView(this);
        hint.setText(text);
        hint.setTextColor(getColor(R.color.text_primary));
        hint.setTextSize(17);
        hint.setGravity(Gravity.CENTER);
        hint.setLineSpacing(dp(4), 1f);
        hint.setPadding(dp(16), dp(14), dp(16), dp(14));
        hint.setBackgroundResource(R.drawable.bg_white_translucent_stroke);
        return hint;
    }

    private LinearLayout.LayoutParams hintBoxParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(8), 0, 0);
        return params;
    }

    private void renderLetterScrambleAreas() {
        renderLetterList(letterBoardContainer, letterBoardItems, true);
        renderLetterList(letterHandContainer, letterHandItems, false);
    }

    private void renderLetterList(LinearLayout container, List<String> letters, boolean fromBoard) {
        container.removeAllViews();
        if (letters.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(fromBoard ? "Chạm các chữ ở dưới để đưa lên đây" : "Không còn chữ nào");
            empty.setTextColor(getColor(R.color.text_secondary));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(12), 0, dp(12));
            container.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            return;
        }

        LinearLayout row = null;
        int rowWidth = 0;
        int chipOuterWidth = dp(52);
        int maxRowWidth = getResources().getDisplayMetrics().widthPixels - dp(80);
        for (int i = 0; i < letters.size(); i++) {
            if (row == null || rowWidth + chipOuterWidth > maxRowWidth) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                container.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                ));
                rowWidth = 0;
            }

            MaterialButton chip = new MaterialButton(this);
            chip.setText(letters.get(i));
            chip.setTextSize(18);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setAllCaps(false);
            chip.setSingleLine(true);
            chip.setMinHeight(0);
            chip.setMinWidth(0);
            chip.setMinimumHeight(0);
            chip.setMinimumWidth(0);
            chip.setInsetTop(0);
            chip.setInsetBottom(0);
            chip.setPadding(0, 0, 0, 0);
            int index = i;
            chip.setOnClickListener(v -> moveLetterItem(index, fromBoard));

            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(dp(44), dp(44));
            chipParams.setMargins(dp(4), dp(4), dp(4), dp(4));
            row.addView(chip, chipParams);
            rowWidth += chipOuterWidth;
        }
    }

    private void moveLetterItem(int index, boolean fromBoard) {
        if (fromBoard) {
            if (index >= 0 && index < letterBoardItems.size()) {
                letterHandItems.add(letterBoardItems.remove(index));
            }
        } else {
            if (index >= 0 && index < letterHandItems.size()) {
                letterBoardItems.add(letterHandItems.remove(index));
            }
        }
        renderLetterScrambleAreas();
    }

    private void submitLetterScramble() {
        String answer = String.join("", letterBoardItems).toLowerCase(Locale.US);
        if (answer.equals(currentLetterAnswer)) {
            if (letterScrambleTimer != null) letterScrambleTimer.cancel();
            letterScrambleScore += 1;
            updateScoreUI();
            playSoundEffect(true);
            UiFeedback.showSnack(binding.getRoot(), "Chính xác! +1 điểm");
            new Handler(Looper.getMainLooper()).postDelayed(this::fetchAndShowNextLetterWord, 800);
        } else {
            playSoundEffect(false);
            finishLetterScrambleGame("Sai rồi! Từ đúng là: " + currentLetterAnswer);
        }
    }

    private void startLetterScrambleTimer() {
        if (letterScrambleTimer != null) letterScrambleTimer.cancel();
        letterScrambleTimer = new CountDownTimer(LETTER_TIME_LIMIT, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                letterTimerText.setText(String.valueOf((int) (millisUntilFinished / 1000)));
                letterTimerProgress.setProgress((int) (millisUntilFinished / 100));
            }

            @Override
            public void onFinish() {
                finishLetterScrambleGame("Hết giờ! Từ đúng là: " + currentLetterAnswer);
            }
        }.start();
    }

    private void finishLetterScrambleGame(String reason) {
        if (letterScrambleTimer != null) letterScrambleTimer.cancel();

        SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
        int oldHighScore = prefs.getInt("letter_scramble_high_score", 0);
        boolean isNewRecord = letterScrambleScore > oldHighScore;
        if (isNewRecord) {
            prefs.edit().putInt("letter_scramble_high_score", letterScrambleScore).apply();
        }

        String history = prefs.getString("letter_scramble_history", "");
        String newEntry = "Điểm: " + letterScrambleScore + " - " + new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date());
        if (history.isEmpty() || history.equals("Chưa có dữ liệu")) history = newEntry;
        else {
            String[] lines = history.split("\n");
            StringBuilder sb = new StringBuilder(newEntry);
            for (int i = 0; i < Math.min(lines.length, 4); i++) {
                sb.append("\n").append(lines[i]);
            }
            history = sb.toString();
        }
        prefs.edit().putString("letter_scramble_history", history).apply();

        String msg = reason + "\n\nBạn xếp đúng " + letterScrambleScore + " từ.";
        if (isNewRecord && letterScrambleScore > 0) msg += "\n\nCHÚC MỪNG! BẠN ĐÃ PHÁ KỈ LỤC!";

        new AlertDialog.Builder(this)
                .setTitle("Game Over")
                .setMessage(msg)
                .setPositiveButton("Về sảnh", (dialog, which) -> showLetterScrambleLobby())
                .setCancelable(false)
                .show();
    }

    // --- LIGHTNING MODE ---
    private void showLightningLobby() {
        if (lightningTimer != null) lightningTimer.cancel();
        isLightningMode = true;
        isLightningLobbyVisible = true;
        isAiRiddleMode = false;
        isAiRiddleLobbyVisible = false;
        isSentenceScrambleMode = false;
        isSentenceScrambleLobbyVisible = false;
        isLetterScrambleMode = false;
        isLetterScrambleLobbyVisible = false;
        isFriendBattleMode = false;
        isFriendBattleLobbyVisible = false;
        binding.textTitle.setText("Nhanh như chớp");
        binding.layoutAiModes.setVisibility(View.GONE);
        View lobby = getLayoutInflater().inflate(R.layout.layout_game_lightning_lobby, binding.gameContainer, false);
        binding.gameContainer.removeAllViews();
        binding.gameContainer.setVisibility(View.VISIBLE);
        binding.gameContainer.addView(lobby);

        SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
        int highScore = prefs.getInt("lightning_high_score", 0);
        String history = prefs.getString("lightning_history", "Chưa có dữ liệu");

        ((TextView) lobby.findViewById(R.id.text_lightning_high_score)).setText("Kỉ lục: " + highScore + " điểm");
        ((TextView) lobby.findViewById(R.id.text_lightning_history)).setText(history);

        lobby.findViewById(R.id.btn_start_lightning).setOnClickListener(v -> startLightningGame());
        updateScoreUI();
    }

    private void startLightningGame() {
        spendHeartBeforeQuickGame(() -> {
            lightningScore = 0;
            lightningVocabs.clear();
            updateScoreUI();

            new Thread(() -> {
                List<Vocabulary> vocabs = AppDatabase.getDatabase(this).vocabularyDao().getRandomLearnedVocabularies(100);
                List<Vocabulary> filteredVocabs = filterLightningVocabs(vocabs);
                if (filteredVocabs.size() < 2) {
                    filteredVocabs = filterLightningVocabs(AppDatabase.getDatabase(this).vocabularyDao().getRandomVocabularies(100));
                }

                List<Vocabulary> finalVocabs = filteredVocabs;
                runOnUiThread(() -> {
                    if (finalVocabs.size() < 2) {
                        Toast.makeText(this, "Cần ít nhất 2 từ có nghĩa tiếng Việt để chơi!", Toast.LENGTH_SHORT).show();
                        showLightningLobby();
                        return;
                    }
                    lightningVocabs.clear();
                    lightningVocabs.addAll(finalVocabs);
                    displayLightningGame();
                });
            }).start();
        });
    }

    private List<Vocabulary> filterLightningVocabs(List<Vocabulary> vocabs) {
        List<Vocabulary> filtered = new ArrayList<>();
        for (Vocabulary vocab : vocabs) {
            if (!isBlank(vocab.getWord()) && !getVietnameseMeaning(vocab).isEmpty()) {
                filtered.add(vocab);
            }
        }
        return filtered;
    }

    private void displayLightningGame() {
        isLightningLobbyVisible = false;

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(20), dp(24), dp(24));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        lightningTimerText = new TextView(this);
        lightningTimerText.setText("01:00");
        lightningTimerText.setTextColor(getColor(R.color.brand_primary));
        lightningTimerText.setTextSize(26);
        lightningTimerText.setTypeface(Typeface.DEFAULT_BOLD);
        lightningTimerText.setGravity(Gravity.CENTER);
        root.addView(lightningTimerText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        ));

        lightningTimerProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        lightningTimerProgress.setMax(LIGHTNING_TIME_LIMIT / 100);
        lightningTimerProgress.setProgress(LIGHTNING_TIME_LIMIT / 100);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
        );
        progressParams.setMargins(0, dp(8), 0, dp(28));
        root.addView(lightningTimerProgress, progressParams);

        TextView wordTitle = sectionLabel("Từ tiếng Anh");
        root.addView(wordTitle);

        lightningWordText = new TextView(this);
        lightningWordText.setTextColor(getColor(R.color.text_primary));
        lightningWordText.setTextSize(34);
        lightningWordText.setTypeface(Typeface.DEFAULT_BOLD);
        lightningWordText.setGravity(Gravity.CENTER);
        lightningWordText.setPadding(dp(16), dp(20), dp(16), dp(20));
        lightningWordText.setBackgroundResource(R.drawable.bg_white_translucent_stroke);
        LinearLayout.LayoutParams wordParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        wordParams.setMargins(0, dp(8), 0, dp(20));
        root.addView(lightningWordText, wordParams);

        TextView meaningTitle = sectionLabel("Nghĩa tiếng Việt");
        root.addView(meaningTitle);

        lightningMeaningText = hintBox("");
        lightningMeaningText.setTextSize(20);
        LinearLayout.LayoutParams meaningParams = hintBoxParams();
        meaningParams.setMargins(0, dp(8), 0, dp(28));
        root.addView(lightningMeaningText, meaningParams);

        LinearLayout answerRow = new LinearLayout(this);
        answerRow.setOrientation(LinearLayout.HORIZONTAL);
        answerRow.setGravity(Gravity.CENTER);
        root.addView(answerRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
        ));

        MaterialButton wrongButton = lightningAnswerButton("SAI");
        LinearLayout.LayoutParams wrongParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        wrongParams.setMargins(0, 0, dp(8), 0);
        answerRow.addView(wrongButton, wrongParams);
        wrongButton.setOnClickListener(v -> answerLightning(false));

        MaterialButton correctButton = lightningAnswerButton("ĐÚNG");
        LinearLayout.LayoutParams correctParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        correctParams.setMargins(dp(8), 0, 0, 0);
        answerRow.addView(correctButton, correctParams);
        correctButton.setOnClickListener(v -> answerLightning(true));

        binding.gameContainer.removeAllViews();
        binding.gameContainer.addView(scrollView);
        showNextLightningQuestion();
        startLightningTimer();
    }

    private MaterialButton lightningAnswerButton(String text) {
        MaterialButton button = new MaterialButton(this);
        button.setText(text);
        button.setTextSize(18);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        return button;
    }

    private void showNextLightningQuestion() {
        if (lightningVocabs.size() < 2) return;

        Vocabulary wordVocab = lightningVocabs.get((int) (Math.random() * lightningVocabs.size()));
        boolean shouldMatch = Math.random() < 0.5;
        Vocabulary meaningVocab = wordVocab;

        if (!shouldMatch) {
            List<Vocabulary> shuffledVocabs = new ArrayList<>(lightningVocabs);
            Collections.shuffle(shuffledVocabs);
            for (Vocabulary candidate : shuffledVocabs) {
                if (candidate != wordVocab
                        && !getVietnameseMeaning(candidate).equalsIgnoreCase(getVietnameseMeaning(wordVocab))) {
                    meaningVocab = candidate;
                    break;
                }
            }
            shouldMatch = meaningVocab == wordVocab;
        }

        currentLightningAnswer = shouldMatch;
        lightningWordText.setText(safe(wordVocab.getWord()));
        lightningMeaningText.setText(getVietnameseMeaning(meaningVocab));
    }

    private void answerLightning(boolean userAnswer) {
        boolean isCorrect = userAnswer == currentLightningAnswer;
        lightningScore += isCorrect ? 1 : -1;
        updateScoreUI();
        playSoundEffect(isCorrect);
        UiFeedback.showSnack(binding.getRoot(), isCorrect ? "Chính xác! +1 điểm" : "Sai rồi! -1 điểm");
        showNextLightningQuestion();
    }

    private void startLightningTimer() {
        if (lightningTimer != null) lightningTimer.cancel();
        lightningTimer = new CountDownTimer(LIGHTNING_TIME_LIMIT, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                lightningTimerText.setText(formatGameTime(millisUntilFinished));
                lightningTimerProgress.setProgress((int) (millisUntilFinished / 100));
            }

            @Override
            public void onFinish() {
                lightningTimerText.setText("00:00");
                lightningTimerProgress.setProgress(0);
                finishLightningGame();
            }
        }.start();
    }

    private void finishLightningGame() {
        if (lightningTimer != null) lightningTimer.cancel();

        SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
        int oldHighScore = prefs.getInt("lightning_high_score", 0);
        boolean isNewRecord = lightningScore > oldHighScore;
        if (isNewRecord) {
            prefs.edit().putInt("lightning_high_score", lightningScore).apply();
        }

        String history = prefs.getString("lightning_history", "");
        String newEntry = "Điểm: " + lightningScore + " - " + new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date());
        if (history.isEmpty() || history.equals("Chưa có dữ liệu")) history = newEntry;
        else {
            String[] lines = history.split("\n");
            StringBuilder sb = new StringBuilder(newEntry);
            for (int i = 0; i < Math.min(lines.length, 4); i++) {
                sb.append("\n").append(lines[i]);
            }
            history = sb.toString();
        }
        prefs.edit().putString("lightning_history", history).apply();

        String msg = "Hết giờ! Bạn đạt " + lightningScore + " điểm.";
        if (isNewRecord && lightningScore > 0) msg += "\n\nCHÚC MỪNG! BẠN ĐÃ PHÁ KỈ LỤC!";

        new AlertDialog.Builder(this)
                .setTitle("Tổng kết")
                .setMessage(msg)
                .setPositiveButton("Về sảnh", (dialog, which) -> showLightningLobby())
                .setCancelable(false)
                .show();
    }

    private String getVietnameseMeaning(Vocabulary vocab) {
        if (vocab == null) return "";
        String meaning = safe(vocab.getVietnamese_translation());
        if (meaning.isEmpty()) meaning = safe(vocab.getVietnameseTranslation());
        return meaning;
    }

    private String formatGameTime(long millisUntilFinished) {
        long totalSeconds = Math.max(0L, millisUntilFinished / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private void beginAiRiddleGame() {
        isAiRiddleLobbyVisible = false;
        aiRiddleScore = 0;
        updateScoreUI();
        fetchAndShowNextRiddle();
    }

    private void fetchAndShowNextRiddle() {
        if (riddleTimer != null) riddleTimer.cancel();
        binding.gameContainer.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<Vocabulary> randomVocabs = AppDatabase.getDatabase(this).vocabularyDao().getRandomVocabularies(20);
            Vocabulary selectedLocalWord = null;
            for (Vocabulary vocab : randomVocabs) {
                if (isUsableRiddleWord(vocab)) {
                    selectedLocalWord = vocab;
                    break;
                }
            }

            Vocabulary finalWord = selectedLocalWord;
            runOnUiThread(() -> {
                if (finalWord != null) {
                    currentRiddleWord = finalWord;
                    displayRiddleQuestion();
                } else {
                    Toast.makeText(this, "Không tìm thấy dữ liệu từ vựng phù hợp!", Toast.LENGTH_SHORT).show();
                    showAiRiddleLobby();
                }
            });
        }).start();
    }

    private boolean isUsableRiddleWord(Vocabulary vocab) {
        if (vocab == null || isBlank(vocab.getWord())) return false;
        return !isBlank(vocab.getDefinition()) || !isBlank(vocab.getExample_sentence());
    }

    private void displayRiddleQuestion() {
        isAiRiddleLobbyVisible = false;
        View view = getLayoutInflater().inflate(R.layout.layout_game_ai_riddle, binding.gameContainer, false);
        binding.gameContainer.removeAllViews();
        binding.gameContainer.addView(view);

        TextView textHint = view.findViewById(R.id.text_riddle_hint);
        TextView textEngHint = view.findViewById(R.id.text_english_hint);
        TextView textViHint = view.findViewById(R.id.text_vietnamese_hint);
        TextView textPlaceholders = view.findViewById(R.id.text_answer_placeholders);
        TextView textLetterCount = view.findViewById(R.id.text_letter_count);
        TextInputEditText editAnswer = view.findViewById(R.id.edit_answer);
        MaterialButton btnSubmit = view.findViewById(R.id.btn_submit_answer);
        TextView textTimer = view.findViewById(R.id.text_timer);
        ProgressBar progressTimer = view.findViewById(R.id.progress_timer);

        // Reset hint flags
        hintEngShown = false;
        hintViShown = false;
        hint50Shown = false;
        hint75Shown = false;
        currentPointsPossible = 5;

        // Base question: Use example sentence if available, else definition
        String word = safe(currentRiddleWord.getWord()).toLowerCase(Locale.US);
        String question = safe(currentRiddleWord.getExample_sentence());
        if (question.isEmpty()) question = safe(currentRiddleWord.getDefinition());
        if (question.isEmpty()) question = "Which English word matches this vocabulary riddle?";
        
        question = question.replaceAll("(?i)" + Pattern.quote(word), "_______");
        if (!question.contains("_______")) {
            question = "Từ tiếng Anh nào có nghĩa là: " + safe(currentRiddleWord.getDefinition());
        }
        textHint.setText(question);

        if (textEngHint != null) textEngHint.setVisibility(View.GONE);
        if (textViHint != null) textViHint.setVisibility(View.GONE);

        // Initial placeholders (0%)
        textPlaceholders.setText(getMaskedWord(word, 0));
        textLetterCount.setText("(" + word.length() + " ký tự)");

        btnSubmit.setOnClickListener(v -> {
            String ans = editAnswer.getText().toString().trim();
            if (ans.equalsIgnoreCase(word)) {
                if (riddleTimer != null) riddleTimer.cancel();
                aiRiddleScore += currentPointsPossible;
                updateScoreUI();
                playSoundEffect(true);
                UiFeedback.showSnack(binding.getRoot(), "Chính xác! +" + currentPointsPossible + " điểm");
                new Handler(Looper.getMainLooper()).postDelayed(this::fetchAndShowNextRiddle, 1000);
            } else {
                playSoundEffect(false);
                Toast.makeText(this, "Chưa đúng rồi, thử lại nhé!", Toast.LENGTH_SHORT).show();
            }
        });

        startRiddleTimer(textTimer, progressTimer, textEngHint, textViHint, textPlaceholders);
    }

    private String getMaskedWord(String word, int percent) {
        StringBuilder sb = new StringBuilder();
        int visibleCount = (int) Math.ceil(word.length() * (percent / 100.0));
        
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (!Character.isLetter(c)) {
                sb.append(c).append(" ");
            } else if (i < visibleCount) {
                sb.append(c).append(" ");
            } else {
                sb.append("_ ");
            }
        }
        return sb.toString().trim();
    }

    private void startRiddleTimer(TextView textTimer, ProgressBar progressTimer, TextView textEngHint, TextView textViHint, TextView textPlaceholders) {
        if (riddleTimer != null) riddleTimer.cancel();
        
        progressTimer.setMax(RIDDLE_TIME_LIMIT / 100);
        
        riddleTimer = new CountDownTimer(RIDDLE_TIME_LIMIT, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                textTimer.setText(String.valueOf(seconds));
                progressTimer.setProgress((int) (millisUntilFinished / 100));

                long elapsed = RIDDLE_TIME_LIMIT - millisUntilFinished;

                // 5s: English hint, +4 points
                if (elapsed >= 5000 && !hintEngShown) {
                    hintEngShown = true;
                    currentPointsPossible = 4;
                    if (textEngHint != null) {
                        textEngHint.setVisibility(View.VISIBLE);
                        textEngHint.setText("Hint (English): " + currentRiddleWord.getDefinition());
                    }
                    UiFeedback.showSnack(binding.getRoot(), "Gợi ý 1: Nghĩa tiếng Anh (+4 điểm)");
                }

                // 10s (5s more): Vietnamese hint, +3 points
                if (elapsed >= 10000 && !hintViShown) {
                    hintViShown = true;
                    currentPointsPossible = 3;
                    if (textViHint != null) {
                        textViHint.setVisibility(View.VISIBLE);
                        String vi = safe(currentRiddleWord.getVietnamese_translation());
                        if (vi.isEmpty()) vi = safe(currentRiddleWord.getVietnameseTranslation());
                        if (vi.isEmpty()) vi = "Chưa có bản dịch";
                        textViHint.setText("Gợi ý (Tiếng Việt): " + vi);
                    }
                    UiFeedback.showSnack(binding.getRoot(), "Gợi ý 2: Nghĩa tiếng Việt (+3 điểm)");
                }

                // 15s (5s more): 50% characters hint, +2 points
                if (elapsed >= 15000 && !hint50Shown) {
                    hint50Shown = true;
                    currentPointsPossible = 2;
                    textPlaceholders.setText(getMaskedWord(safe(currentRiddleWord.getWord()).toLowerCase(Locale.US), 50));
                    UiFeedback.showSnack(binding.getRoot(), "Gợi ý 3: 50% ký tự (+2 điểm)");
                }

                // 25s (10s more): 75% characters hint, +1 point
                if (elapsed >= 25000 && !hint75Shown) {
                    hint75Shown = true;
                    currentPointsPossible = 1;
                    textPlaceholders.setText(getMaskedWord(safe(currentRiddleWord.getWord()).toLowerCase(Locale.US), 75));
                    UiFeedback.showSnack(binding.getRoot(), "Gợi ý 4: 75% ký tự (+1 điểm)");
                }
            }

            @Override
            public void onFinish() {
                textTimer.setText("0");
                progressTimer.setProgress(0);
                finishAiRiddleGame();
            }
        }.start();
    }

    private void finishAiRiddleGame() {
        if (riddleTimer != null) riddleTimer.cancel();
        
        SharedPreferences prefs = getSharedPreferences("game_prefs", MODE_PRIVATE);
        int oldHighScore = prefs.getInt("ai_riddle_high_score", 0);
        boolean isNewRecord = aiRiddleScore > oldHighScore;
        
        if (isNewRecord) {
            prefs.edit().putInt("ai_riddle_high_score", aiRiddleScore).apply();
        }

        String history = prefs.getString("ai_riddle_history", "");
        String newEntry = "Điểm: " + aiRiddleScore + " - " + new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date());
        if (history.isEmpty() || history.equals("Chưa có dữ liệu")) history = newEntry;
        else {
            String[] lines = history.split("\n");
            StringBuilder sb = new StringBuilder(newEntry);
            for (int i = 0; i < Math.min(lines.length, 4); i++) {
                sb.append("\n").append(lines[i]);
            }
            history = sb.toString();
        }
        prefs.edit().putString("ai_riddle_history", history).apply();

        String msg = "Hết giờ! Bạn đạt được " + aiRiddleScore + " điểm.";
        if (isNewRecord && aiRiddleScore > 0) msg += "\n\nCHÚC MỪNG! BẠN ĐÃ PHÁ KỈ LỤC!";

        new AlertDialog.Builder(this)
                .setTitle("Game Over")
                .setMessage(msg)
                .setPositiveButton("Về sảnh", (dialog, which) -> showAiRiddleLobby())
                .setCancelable(false)
                .show();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return safe(value).isEmpty();
    }

    private void updateScoreUI() {
        if (isAiRiddleMode) {
            binding.textScore.setText("Điểm: " + aiRiddleScore);
        } else if (isSentenceScrambleMode) {
            binding.textScore.setText("Điểm: " + sentenceScrambleScore);
        } else if (isLetterScrambleMode) {
            binding.textScore.setText("Điểm: " + letterScrambleScore);
        } else if (isLightningMode) {
            binding.textScore.setText("Điểm: " + lightningScore);
        } else if (isFriendBattleMode) {
            int myScore = 0;
            for (Map<String, Object> player : friendPlayers) {
                if (safe((String) player.get("uid")).equals(currentUid)) {
                    myScore = getPlayerScore(player);
                    break;
                }
            }
            binding.textScore.setText("Điểm: " + myScore);
        } else {
            binding.textScore.setText("Minigames");
        }
    }

    private void handleBack() {
        if (riddleTimer != null) riddleTimer.cancel();
        if (sentenceScrambleTimer != null) sentenceScrambleTimer.cancel();
        if (letterScrambleTimer != null) letterScrambleTimer.cancel();
        if (lightningTimer != null) lightningTimer.cancel();
        if (friendBattleTimer != null) friendBattleTimer.cancel();
        
        if (binding.gameContainer.getVisibility() == View.VISIBLE) {
            if (isFriendBattleLobbyVisible) {
                stopFriendBattleSession(true);
                binding.gameContainer.removeAllViews();
                binding.gameContainer.setVisibility(View.GONE);
                binding.layoutModeSelection.setVisibility(View.VISIBLE);
                binding.textTitle.setText("Minigames");
                isFriendBattleMode = false;
                isFriendBattleLobbyVisible = false;
                updateScoreUI();
            } else if (isFriendBattleMode) {
                stopFriendBattleSession(true);
                showFriendBattleHome();
            } else if (isAiRiddleLobbyVisible || isSentenceScrambleLobbyVisible || isLetterScrambleLobbyVisible || isLightningLobbyVisible) {
                showAiModes();
            } else if (isLightningMode) {
                showLightningLobby();
            } else if (isLetterScrambleMode) {
                showLetterScrambleLobby();
            } else if (isSentenceScrambleMode) {
                showSentenceScrambleLobby();
            } else {
                showAiRiddleLobby();
            }
        } else if (binding.layoutAiModes.getVisibility() == View.VISIBLE) {
            binding.layoutAiModes.setVisibility(View.GONE);
            binding.layoutModeSelection.setVisibility(View.VISIBLE);
            binding.textTitle.setText("Minigames");
            isAiRiddleMode = false;
            isAiRiddleLobbyVisible = false;
            isSentenceScrambleMode = false;
            isSentenceScrambleLobbyVisible = false;
            isLetterScrambleMode = false;
            isLetterScrambleLobbyVisible = false;
            isLightningMode = false;
            isLightningLobbyVisible = false;
            isFriendBattleMode = false;
            isFriendBattleLobbyVisible = false;
            updateScoreUI();
        } else finish();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (riddleTimer != null) riddleTimer.cancel();
        if (sentenceScrambleTimer != null) sentenceScrambleTimer.cancel();
        if (letterScrambleTimer != null) letterScrambleTimer.cancel();
        if (lightningTimer != null) lightningTimer.cancel();
        stopFriendBattleSession(true);
        if (gamificationStatusBinder != null) gamificationStatusBinder.stop();
        super.onDestroy();
    }
}
