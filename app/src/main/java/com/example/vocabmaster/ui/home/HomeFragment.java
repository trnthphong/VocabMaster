package com.example.vocabmaster.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.local.VocabularyDao;
import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.Topic;
import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.databinding.FragmentHomeBinding;
import com.example.vocabmaster.ui.common.MotionSystem;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.example.vocabmaster.ui.library.CourseDetailActivity;
import com.example.vocabmaster.ui.study.MiniGameActivity;
import com.example.vocabmaster.ui.study.StudyActivity;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private FragmentHomeBinding binding;
    private FirebaseFirestore db;
    private VocabularyDao vocabularyDao;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private User currentUser;
    private Course activeCourse;
    private DocumentSnapshot nextLessonDoc;
    private ListenerRegistration userListener;
    private ListenerRegistration notificationListener;
    private TopicAdapter topicAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();
        vocabularyDao = AppDatabase.getDatabase(requireContext()).vocabularyDao();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        setupInitialAnimations();
        setupTopicRecyclerView();
        setupListeners();
        runEntranceAnimation();
        observeUserData();
        observeNotifications();
        loadTopicsPreview();
    }

    private void setupTopicRecyclerView() {
        topicAdapter = new TopicAdapter(new TopicAdapter.OnTopicClickListener() {
            @Override
            public void onTopicClick(Topic topic) {
                if (topic.isDownloaded()) {
                    startJourneyFlow(topic);
                } else {
                    UiFeedback.performHaptic(requireContext(), 50);
                    showDownloadRequiredDialog(topic);
                }
            }

            @Override
            public void onDownloadClick(Topic topic) {
                downloadTopic(topic);
            }
        });
        
        topicAdapter.setHorizontal(true);
        binding.recyclerTopicsPreview.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.recyclerTopicsPreview.setAdapter(topicAdapter);
    }

    private void showDownloadRequiredDialog(Topic topic) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cần tải dữ liệu")
                .setMessage("Bạn cần tải xuống bộ từ vựng \"" + topic.getName() + "\" trước khi bắt đầu học. Tải ngay?")
                .setPositiveButton("Tải về", (dialog, which) -> downloadTopic(topic))
                .setNegativeButton("Để sau", null)
                .show();
    }

    private void loadTopicsPreview() {
        db.collection("topics")
                .limit(5)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Topic> topics = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot) {
                        Topic t = doc.toObject(Topic.class);
                        if (t != null) {
                            t.setId(doc.getId());
                            checkIfDownloaded(t);
                            topics.add(t);
                        }
                    }
                    topicAdapter.setTopics(topics);
                });
    }

    private void checkIfDownloaded(Topic topic) {
        executorService.execute(() -> {
            int count = vocabularyDao.getCountByTopic(topic.getId().toLowerCase());
            if (count > 0) {
                if (isAdded()) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        topic.setDownloaded(true);
                        topicAdapter.notifyDataSetChanged();
                    });
                }
            }
        });
    }

    private void downloadTopic(Topic topic) {
        Toast.makeText(requireContext(), "Đang tải dữ liệu: " + topic.getName(), Toast.LENGTH_SHORT).show();
        
        db.collection("topics")
                .document(topic.getId())
                .collection("vocabularies")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Vocabulary> vocabs = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot) {
                        Vocabulary v = doc.toObject(Vocabulary.class);
                        if (v != null) {
                            v.setVocabularyId(doc.getId());
                            v.setTopic(topic.getId().toLowerCase());
                            vocabs.add(v);
                        }
                    }

                    if (!vocabs.isEmpty()) {
                        executorService.execute(() -> {
                            vocabularyDao.insertAll(vocabs);
                            new Handler(Looper.getMainLooper()).post(() -> {
                                topic.setDownloaded(true);
                                topicAdapter.notifyDataSetChanged();
                                Toast.makeText(requireContext(), "Tải thành công " + vocabs.size() + " từ!", Toast.LENGTH_SHORT).show();
                            });
                        });
                    } else {
                        Toast.makeText(requireContext(), "Chủ đề này hiện chưa có từ vựng.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Lỗi kết nối: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void setupInitialAnimations() {
        binding.headerSection.setAlpha(0f);
        binding.headerSection.setTranslationY(-50f);
        binding.statsGrid.setAlpha(0f);
        binding.statsGrid.setScaleX(0.9f);
        binding.labelActions.setAlpha(0f);
        binding.btnStartFlashcards.setTranslationX(100f);
        binding.btnStartFlashcards.setAlpha(0f);
        binding.btnPlayMiniGame.setTranslationX(100f);
        binding.btnPlayMiniGame.setAlpha(0f);
    }

    private void runEntranceAnimation() {
        binding.headerSection.animate().alpha(1f).translationY(0).setDuration(600).start();
        binding.statsGrid.animate().alpha(1f).scaleX(1f).setDuration(700).setStartDelay(200).setInterpolator(new DecelerateInterpolator()).start();
        binding.labelActions.animate().alpha(1f).setDuration(500).setStartDelay(400).start();
        
        binding.btnStartFlashcards.animate().translationX(0).alpha(1f).setDuration(600).setStartDelay(500).start();
        binding.btnPlayMiniGame.animate().translationX(0).alpha(1f).setDuration(600).setStartDelay(600).start();
    }

    private void setupListeners() {
        MotionSystem.applyPressState(binding.btnStartFlashcards);
        MotionSystem.applyPressState(binding.btnPlayMiniGame);

        binding.btnStartFlashcards.setOnClickListener(v -> {
            UiFeedback.performHaptic(requireContext(), 10);
            Intent intent = new Intent(requireContext(), CourseDetailActivity.class);
            startActivity(intent);
        });

        binding.btnPlayMiniGame.setOnClickListener(v -> openMiniGame());

        binding.btnViewCourse.setOnClickListener(v -> {
            UiFeedback.performHaptic(requireContext(), 10);
            if (nextLessonDoc != null) {
                Intent intent = new Intent(requireContext(), StudyActivity.class);
                intent.putExtra("lesson_id", nextLessonDoc.getId());
                intent.putExtra("lesson_title", nextLessonDoc.getString("title"));
                if (activeCourse != null) {
                    intent.putExtra("course_id", activeCourse.getFirestoreId());
                    intent.putExtra("is_personal", activeCourse.getCreatorId() != null);
                }
                startActivity(intent);
            } else {
                Intent intent = new Intent(requireContext(), CourseDetailActivity.class);
                if (activeCourse != null && activeCourse.getFirestoreId() != null) {
                    intent.putExtra("course_id", activeCourse.getFirestoreId());
                    intent.putExtra("course_title", activeCourse.getTitle());
                    intent.putExtra("is_personal", activeCourse.getCreatorId() != null);
                }
                startActivity(intent);
            }
        });

        binding.btnNotify.setOnClickListener(v -> {
            UiFeedback.performHaptic(requireContext(), 10);
            NavHostFragment.findNavController(this).navigate(R.id.action_home_to_notifications);
        });

        binding.btnSeeAllTopics.setOnClickListener(v -> {
            UiFeedback.performHaptic(requireContext(), 10);
            Intent intent = new Intent(requireContext(), AllTopicsActivity.class);
            startActivity(intent);
        });
    }

    private void openMiniGame() {
        UiFeedback.performHaptic(requireContext(), 10);
        Intent intent = new Intent(requireContext(), MiniGameActivity.class);
        startActivity(intent);
    }

    private void startJourneyFlow(Topic topic) {
        UiFeedback.performHaptic(requireContext(), 10);
        
        if (currentUser != null && currentUser.getHearts() <= 0 && !currentUser.isActivePremium()) {
            showHeartsModal();
            return;
        }

        String userLang = (currentUser != null) ? currentUser.getLanguage() : null;
        String userLevel = (currentUser != null) ? currentUser.getProficiencyLevel() : null;
        boolean hasValidLang = userLang != null && (userLang.equals("en") || userLang.equals("ru"));

        if (hasValidLang && userLevel != null) {
            Intent intent = new Intent(requireContext(), TopicWordListActivity.class);
            intent.putExtra("selected_topic", topic.getId());
            intent.putExtra("display_title", topic.getName());
            intent.putExtra("lang_code", userLang);
            intent.putExtra("selected_level", userLevel);
            startActivity(intent);
        } else {
            Intent intent = new Intent(requireContext(), JourneySetupActivity.class);
            intent.putExtra("selected_topic", topic.getId());
            intent.putExtra("display_title", topic.getName());
            intent.putExtra("is_change_only", true);
            startActivity(intent);
        }
    }

    private void showHeartsModal() {
        UiFeedback.showConfirmDialog(requireContext(), 
            "Out of Hearts!", 
            "Get unlimited hearts and learn without limits with VocabMaster Pro.",
            () -> {
                NavHostFragment.findNavController(this).navigate(R.id.navigation_premium);
            });
    }

    private void observeUserData() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        if (userListener != null) userListener.remove();

        userListener = db.collection("users").document(uid).addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                return;
            }

            if (snapshot != null && snapshot.exists() && isAdded() && binding != null) {
                currentUser = snapshot.toObject(User.class);
                if (currentUser != null) {
                    currentUser.setUid(uid);
                    loadActiveCourse(uid);
                    setupHeartTimer();
                    updatePremiumUI();
                }
            }
        });
    }

    private void observeNotifications() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        if (notificationListener != null) notificationListener.remove();

        notificationListener = db.collection("users").document(uid)
                .collection("notifications")
                .whereEqualTo("read", false)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || binding == null || !isAdded()) return;
                    
                    boolean hasUnread = snapshot != null && !snapshot.isEmpty();
                    binding.viewNotificationDot.setVisibility(hasUnread ? View.VISIBLE : View.GONE);
                });
    }

    private void updatePremiumUI() {
        if (currentUser == null) return;
        boolean isPro = currentUser.isActivePremium();
        
        binding.textPremiumBadge.setVisibility(isPro ? View.GONE : View.VISIBLE);
        if (!isPro) {
            binding.textPremiumBadge.setText("UPGRADE TODAY");
            binding.textPremiumBadge.setOnClickListener(v -> 
                NavHostFragment.findNavController(this).navigate(R.id.navigation_premium));
        }
        
        if (isPro) {
            binding.textHearts.setText("∞");
        }
    }

    private void loadActiveCourse(String uid) {
        if (currentUser != null && currentUser.getActiveCourseId() != null) {
            db.collection("users").document(uid).collection("personal_courses")
                    .document(currentUser.getActiveCourseId()).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) handleLoadedCourse(doc);
                        else searchAllUserCourses(uid);
                    });
        } else {
            searchAllUserCourses(uid);
        }
    }

    private void searchAllUserCourses(String uid) {
        db.collection("users").document(uid).collection("personal_courses")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) handleLoadedCourse(querySnapshot.getDocuments().get(0));
                    else updateProfileUI();
                });
    }

    private void handleLoadedCourse(DocumentSnapshot doc) {
        activeCourse = doc.toObject(Course.class);
        if (activeCourse != null) {
            activeCourse.setFirestoreId(doc.getId());
            findNextLesson(activeCourse.getFirestoreId(), doc.getReference().getParent().getPath().contains("personal_courses"));
        } else updateProfileUI();
    }

    private void findNextLesson(String courseId, boolean isPersonal) {
        String unitsPath = isPersonal ? 
                "users/" + FirebaseAuth.getInstance().getUid() + "/personal_courses/" + courseId + "/units" : 
                "units";
        
        db.collection(unitsPath).get().addOnSuccessListener(unitSnapshots -> {
            if (unitSnapshots.isEmpty()) {
                updateProfileUI();
                return;
            }
            updateProfileUI(); 
        });
    }

    private void updateProfileUI() {
        if (currentUser == null || binding == null) return;
        binding.textGreeting.setText(getGreeting());
        binding.textUserName.setText(currentUser.getName());
        if (currentUser.isActivePremium()) binding.textHearts.setText("∞");
        else binding.textHearts.setText(String.valueOf(currentUser.getHearts()));
        binding.tvXpCount.setText(String.valueOf(currentUser.getXp()));
        binding.tvStreakCount.setText(String.valueOf(currentUser.getStreak()));
    }

    private String getGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 12) return "Good Morning,";
        if (hour >= 12 && hour < 18) return "Good Afternoon,";
        return "Good Evening,";
    }

    private void setupHeartTimer() {
        if (currentUser == null || currentUser.isActivePremium() || currentUser.getHearts() >= 5) {
            stopTimer();
            return;
        }
        startTimer();
    }

    private void startTimer() {
        stopTimer();
        timerRunnable = () -> {
            updateTimerUI();
            timerHandler.postDelayed(timerRunnable, 1000);
        };
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
    }

    private void updateTimerUI() {
        // Logic timer...
    }

    @Override
    public void onResume() {
        super.onResume();
        observeUserData();
        observeNotifications();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopTimer();
        if (userListener != null) userListener.remove();
        if (notificationListener != null) notificationListener.remove();
        binding = null;
    }
}
