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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.vocabmaster.R;

import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.databinding.FragmentHomeBinding;
import com.example.vocabmaster.ui.common.MotionSystem;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.example.vocabmaster.ui.library.CourseDetailActivity;
import com.example.vocabmaster.ui.study.MiniGameActivity;
import com.example.vocabmaster.ui.study.StudyActivity;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
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

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private FragmentHomeBinding binding;
    private FirebaseFirestore db;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private User currentUser;
    private Course activeCourse;
    private DocumentSnapshot nextLessonDoc;
    private ListenerRegistration userListener;

    private final String[] avatarValues = {"bear", "cat", "dog", "bird", "snake", "tiger", "rabbit"};
    private final int[] avatarResIds = {
            R.drawable.bear, R.drawable.cat, R.drawable.dog,
            R.drawable.bird, R.drawable.snake, R.drawable.tiger,
            R.drawable.rabbit
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        setupInitialAnimations();
        setupListeners();
        runEntranceAnimation();
        observeUserData();
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
                }
                startActivity(intent);
            } else {
                Intent intent = new Intent(requireContext(), CourseDetailActivity.class);
                if (activeCourse != null && activeCourse.getFirestoreId() != null) {
                    intent.putExtra("course_id", activeCourse.getFirestoreId());
                    intent.putExtra("course_title", activeCourse.getTitle());
                }
                startActivity(intent);
            }
        });

        setupTopicListener(binding.cardTopicCareer, "nghề nghiệp", "Career");
        setupTopicListener(binding.cardTopicTravel, "du lịch", "Travel");
        setupTopicListener(binding.cardTopicSchool, "trường học", "School");
        setupTopicListener(binding.cardTopicFood, "thức ăn", "Food");
        setupTopicListener(binding.cardTopicCulture, "văn hóa", "Culture");
        setupTopicListener(binding.cardTopicBrainSkill, "luyện trí não", "Brain Skill");
        setupTopicListener(binding.cardTopicOther, "khác", "Other");
    }

    private void setupTopicListener(View view, String topicValue, String displayTitle) {
        MotionSystem.applyPressState(view);
        view.setOnClickListener(v -> startJourneyFlow(topicValue, displayTitle));
    }

    private void startJourneyFlow(String topic, String displayTitle) {
        UiFeedback.performHaptic(requireContext(), 10);
        
        String userLang = (currentUser != null) ? currentUser.getLanguage() : null;
        boolean hasValidLang = userLang != null && (userLang.equals("en") || userLang.equals("ru"));

        if (hasValidLang) {
            Intent intent = new Intent(requireContext(), TopicWordListActivity.class);
            intent.putExtra("topic", topic);
            intent.putExtra("display_title", displayTitle);
            intent.putExtra("lang_code", userLang);
            startActivity(intent);
        } else {
            Intent intent = new Intent(requireContext(), JourneySetupActivity.class);
            intent.putExtra("selected_topic", topic);
            intent.putExtra("display_title", displayTitle);
            intent.putExtra("is_change_only", true);
            startActivity(intent);
        }
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
                }
            }
        });
    }

    private void loadActiveCourse(String uid) {
        db.collection("courses")
                .whereEqualTo("creatorId", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (binding == null || !isAdded()) return;
                    
                    activeCourse = null;
                    if (!querySnapshot.isEmpty()) {
                        List<DocumentSnapshot> docs = new ArrayList<>(querySnapshot.getDocuments());
                        Collections.sort(docs, (d1, d2) -> {
                            Date date1 = d1.getDate("updatedAt");
                            if (date1 == null) date1 = d1.getDate("createdAt");
                            Date date2 = d2.getDate("updatedAt");
                            if (date2 == null) date2 = d2.getDate("createdAt");
                            if (date1 == null || date2 == null) return 0;
                            return date2.compareTo(date1);
                        });

                        for (DocumentSnapshot doc : docs) {
                            Course c = doc.toObject(Course.class);
                            if (c != null && "active".equals(c.getStatus())) {
                                c.setFirestoreId(doc.getId());
                                activeCourse = c;
                                break; 
                            }
                        }
                    }

                    if (activeCourse != null) {
                        findNextLesson(activeCourse.getFirestoreId());
                    } else {
                        nextLessonDoc = null;
                        updateProfileUI();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading active course", e);
                    if (isAdded()) updateProfileUI();
                });
    }

    private void findNextLesson(String courseId) {
        db.collection("units")
                .whereEqualTo("courseId", courseId)
                .get()
                .addOnSuccessListener(unitSnapshots -> {
                    if (unitSnapshots.isEmpty()) {
                        updateProfileUI();
                        return;
                    }

                    List<DocumentSnapshot> units = new ArrayList<>(unitSnapshots.getDocuments());
                    Collections.sort(units, (u1, u2) -> {
                        long o1 = u1.getLong("orderNum") != null ? u1.getLong("orderNum") : 0;
                        long o2 = u2.getLong("orderNum") != null ? u2.getLong("orderNum") : 0;
                        return Long.compare(o1, o2);
                    });

                    List<Task<QuerySnapshot>> lessonTasks = new ArrayList<>();
                    for (DocumentSnapshot unit : units) {
                        lessonTasks.add(db.collection("lessons").whereEqualTo("unitId", unit.getId()).get());
                    }

                    Tasks.whenAllComplete(lessonTasks).addOnCompleteListener(t -> {
                        if (binding == null || !isAdded()) return;
                        nextLessonDoc = null;
                        for (int i = 0; i < units.size(); i++) {
                            Task<QuerySnapshot> task = lessonTasks.get(i);
                            if (task.isSuccessful() && !task.getResult().isEmpty()) {
                                List<DocumentSnapshot> lessons = new ArrayList<>(task.getResult().getDocuments());
                                Collections.sort(lessons, (l1, l2) -> {
                                    long o1 = l1.getLong("orderNum") != null ? l1.getLong("orderNum") : 0;
                                    long o2 = l2.getLong("orderNum") != null ? l2.getLong("orderNum") : 0;
                                    return Long.compare(o1, o2);
                                });
                                for (DocumentSnapshot lesson : lessons) {
                                    if (!Boolean.TRUE.equals(lesson.getBoolean("completed"))) {
                                        nextLessonDoc = lesson;
                                        break;
                                    }
                                }
                            }
                            if (nextLessonDoc != null) break;
                        }
                        updateProfileUI();
                    });
                });
    }

    private void updateProfileUI() {
        if (currentUser == null || binding == null) return;

        binding.textGreeting.setText(getGreeting());
        binding.textUserName.setText(currentUser.getName() != null ? currentUser.getName() : "Student");
        updateAvatarUI(currentUser.getAvatar());

        binding.textHearts.setText(String.valueOf(currentUser.getHearts()));
        binding.tvXpCount.setText(String.valueOf(currentUser.getXp()));
        binding.tvStreakCount.setText(String.valueOf(currentUser.getStreak()));

        String courseTitle;
        String unitTitle;
        int flagRes = -1;

        if (activeCourse != null) {
            courseTitle = activeCourse.getTitle();
            if (courseTitle == null || courseTitle.isEmpty() || courseTitle.equalsIgnoreCase("en") || courseTitle.equalsIgnoreCase("english")) {
                courseTitle = "Tiếng Anh";
            } else if (courseTitle.equalsIgnoreCase("ru") || courseTitle.equalsIgnoreCase("russian")) {
                courseTitle = "Tiếng Nga";
            }
            
            if (nextLessonDoc != null) {
                unitTitle = nextLessonDoc.getString("title");
                binding.btnViewCourse.setText("View Course");
            } else {
                unitTitle = "Đã hoàn thành khóa học!";
                binding.btnViewCourse.setText("Review Course");
            }

            int progress = (int) activeCourse.getProgressPercentage();
            binding.progressCourseCircular.setProgress(progress, true);
            binding.textCoursePercent.setText(progress + "%");
            binding.textCourseProgress.setText("Tiến độ:");

            flagRes = getFlagForLanguageId(activeCourse.getTargetLanguageId());
            if (flagRes == R.drawable.vietnam || flagRes == -1) {
                flagRes = guessFlagFromText(courseTitle);
            }
        } else {
            String lang = currentUser.getLanguage();
            if (lang != null && !lang.isEmpty()) {
                courseTitle = "en".equals(lang) ? "Tiếng Anh" : "Tiếng Nga";
                unitTitle = currentUser.getCurrentUnitTitle() != null ? 
                        currentUser.getCurrentUnitTitle() : "Hành trình khởi đầu";
                flagRes = "en".equals(lang) ? R.drawable.eng : R.drawable.russia;
                binding.btnViewCourse.setText("Khởi tạo khóa học");
            } else {
                courseTitle = "Chưa khởi tạo";
                unitTitle = "Chọn chủ đề học tập bên dưới";
                flagRes = R.drawable.vietnam;
                binding.btnViewCourse.setText("Xem thư viện");
            }
            binding.progressCourseCircular.setProgress(0, true);
            binding.textCoursePercent.setText("0%");
            binding.textCourseProgress.setText("Bắt đầu ngay");
        }

        binding.textCurrentCourseTitle.setText(courseTitle);
        binding.textCurrentUnitTitle.setText(unitTitle);
        binding.imgCurrentCourseFlag.setImageResource(flagRes == -1 ? R.drawable.vietnam : flagRes);
    }

    private int getFlagForLanguageId(int langId) {
        switch (langId) {
            case 1: return R.drawable.eng;
            case 5: return R.drawable.russia;
            default: return R.drawable.vietnam;
        }
    }

    private int guessFlagFromText(String text) {
        if (text == null) return -1;
        String lower = text.toLowerCase();
        if (lower.contains("en") || lower.contains("anh") || lower.contains("english")) return R.drawable.eng;
        if (lower.contains("ru") || lower.contains("nga") || lower.contains("russia") || lower.contains("russian")) return R.drawable.russia;
        return -1;
    }

    private String getGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 12) return "Good Morning,";
        if (hour >= 12 && hour < 18) return "Good Afternoon,";
        if (hour >= 18 && hour < 22) return "Good Evening,";
        return "Good Night,";
    }

    private void setupHeartTimer() {
        if (currentUser == null || currentUser.getHearts() >= 5) {
            binding.cardHeartRegen.setVisibility(View.GONE);
            stopTimer();
            return;
        }
        binding.cardHeartRegen.setVisibility(View.VISIBLE);
        startTimer();
    }

    private void startTimer() {
        stopTimer();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                updateTimerUI();
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    private void updateTimerUI() {
        if (currentUser == null || currentUser.getLastHeartRegen() == null || binding == null) return;

        long REGEN_TIME_MS = 5 * 60 * 1000;
        long now = System.currentTimeMillis();
        long lastRegen = currentUser.getLastHeartRegen().toDate().getTime();
        long diff = now - lastRegen;
        
        if (diff >= REGEN_TIME_MS) {
            int heartsToRegen = (int) (diff / REGEN_TIME_MS);
            int newHearts = Math.min(5, currentUser.getHearts() + heartsToRegen);
            long remainingMs = diff % REGEN_TIME_MS;
            long newLastRegenTime = now - remainingMs;

            currentUser.setHearts(newHearts);
            currentUser.setLastHeartRegen(new Timestamp(new Date(newLastRegenTime)));
            updateUserHeartsInFirestore(newHearts, currentUser.getLastHeartRegen());
            binding.textHearts.setText(String.valueOf(newHearts));
            
            if (newHearts == 5) {
                binding.cardHeartRegen.setVisibility(View.GONE);
                stopTimer();
                return;
            }
            diff = remainingMs;
        }

        long timeRemaining = REGEN_TIME_MS - (diff % REGEN_TIME_MS);
        int minutes = (int) (timeRemaining / 1000) / 60;
        int seconds = (int) (timeRemaining / 1000) % 60;
        binding.textHeartTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
        int progress = (int) ((diff * 100) / REGEN_TIME_MS);
        binding.regenProgress.setProgress(progress, true);
    }

    private void updateUserHeartsInFirestore(int hearts, Timestamp lastRegen) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        db.collection("users").document(uid)
                .update("hearts", hearts, "lastHeartRegen", lastRegen)
                .addOnFailureListener(e -> Log.e(TAG, "Error updating hearts", e));
    }

    private void updateAvatarUI(String avatarValue) {
        int resId = R.drawable.bear;
        if (avatarValue != null) {
            for (int i = 0; i < avatarValues.length; i++) {
                if (avatarValues[i].equals(avatarValue)) {
                    resId = avatarResIds[i];
                    break;
                }
            }
        }
        binding.imgProfile.setImageResource(resId);
    }

    private void openMiniGame() {
        if (currentUser != null && currentUser.isPremium()) {
            MotionSystem.startScreen(requireActivity(), new Intent(requireContext(), MiniGameActivity.class));
        } else {
            UiFeedback.showErrorDialog(requireContext(), "Upgrade to Pro", "Mini games are a Pro feature.");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        observeUserData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopTimer();
        if (userListener != null) userListener.remove();
        binding = null;
    }
}
