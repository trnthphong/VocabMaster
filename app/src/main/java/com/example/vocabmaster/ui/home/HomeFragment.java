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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
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

        binding.btnPlayMiniGame.setOnClickListener(v -> showTopicSelectionDialog());

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

    private void showTopicSelectionDialog() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.Widget_VocabMaster_Card);
        View view = getLayoutInflater().inflate(R.layout.dialog_topic_selection, null);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_topics);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        db.collection("courses")
                .whereEqualTo("creatorId", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Course> courses = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot) {
                        Course c = doc.toObject(Course.class);
                        if (c != null) {
                            c.setFirestoreId(doc.getId());
                            courses.add(c);
                        }
                    }
                    
                    if (courses.isEmpty()) {
                        Toast.makeText(requireContext(), "Bạn chưa học chủ đề nào để ôn tập!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    recyclerView.setAdapter(new TopicSelectionAdapter(courses, course -> {
                        dialog.dismiss();
                        Intent intent = new Intent(requireContext(), MiniGameActivity.class);
                        intent.putExtra("course_id", course.getFirestoreId());
                        intent.putExtra("course_theme", course.getTheme());
                        startActivity(intent);
                    }));
                    dialog.setContentView(view);
                    dialog.show();
                });
    }

    private void startJourneyFlow(String topic, String displayTitle) {
        UiFeedback.performHaptic(requireContext(), 10);
        if (currentUser != null && currentUser.getHearts() <= 0 && !currentUser.isActivePremium()) {
            showHeartsModal();
            return;
        }

        String userLang = (currentUser != null) ? currentUser.getLanguage() : null;
        boolean hasValidLang = userLang != null && (userLang.equals("en") || userLang.equals("ru"));

        if (hasValidLang) {
            Intent intent = new Intent(requireContext(), CourseDetailActivity.class);
            intent.putExtra("course_theme", topic);
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

    private void showHeartsModal() {
        UiFeedback.showConfirmDialog(requireContext(), 
            "Out of Hearts!", 
            "Get unlimited hearts and learn without limits with VocabMaster Pro.",
            () -> NavHostFragment.findNavController(this).navigate(R.id.navigation_premium));
    }

    private void observeUserData() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        if (userListener != null) userListener.remove();
        userListener = db.collection("users").document(uid).addSnapshotListener((snapshot, e) -> {
            if (e != null) return;
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

    private void updatePremiumUI() {
        if (currentUser == null) return;
        boolean isPro = currentUser.isActivePremium();
        binding.textPremiumBadge.setVisibility(isPro ? View.GONE : View.VISIBLE);
        if (isPro) {
            binding.textHearts.setText("∞");
            binding.cardHeartRegen.setVisibility(View.GONE);
        }
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
                    if (activeCourse != null) findNextLesson(activeCourse.getFirestoreId());
                    else updateProfileUI();
                });
    }

    private void findNextLesson(String courseId) {
        db.collection("units")
                .whereEqualTo("courseId", courseId)
                .get()
                .addOnSuccessListener(unitSnapshots -> {
                    if (unitSnapshots.isEmpty()) { updateProfileUI(); return; }
                    List<DocumentSnapshot> units = new ArrayList<>(unitSnapshots.getDocuments());
                    Collections.sort(units, (u1, u2) -> Long.compare(u1.getLong("orderNum") != null ? u1.getLong("orderNum") : 0, u2.getLong("orderNum") != null ? u2.getLong("orderNum") : 0));

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
                                List<DocumentSnapshot> lessons = task.getResult().getDocuments();
                                for (DocumentSnapshot lesson : lessons) {
                                    if (!Boolean.TRUE.equals(lesson.getBoolean("completed"))) {
                                        nextLessonDoc = lesson; break;
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
        binding.textUserName.setText(currentUser.getName());
        updateAvatarUI(currentUser.getAvatar());

        if (currentUser.isActivePremium()) binding.textHearts.setText("∞");
        else binding.textHearts.setText(String.valueOf(currentUser.getHearts()));
        
        binding.tvXpCount.setText(String.valueOf(currentUser.getXp()));
        binding.tvStreakCount.setText(String.valueOf(currentUser.getStreak()));

        if (activeCourse != null) {
            String title = activeCourse.getTitle();
            binding.textCurrentCourseTitle.setText(title != null ? title : "Course");
            binding.textCurrentUnitTitle.setText(nextLessonDoc != null ? nextLessonDoc.getString("title") : "Hoàn thành!");
            int progress = (int) activeCourse.getProgressPercentage();
            binding.progressCourseCircular.setProgress(progress, true);
            binding.textCoursePercent.setText(progress + "%");
        }
    }

    private int getFlagForLanguageId(int langId) {
        return langId == 1 ? R.drawable.eng : R.drawable.russia;
    }

    private String getGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return "Good Morning,";
        if (hour < 18) return "Good Afternoon,";
        return "Good Evening,";
    }

    private void setupHeartTimer() {
        if (currentUser == null || currentUser.isActivePremium() || currentUser.getHearts() >= 5) {
            binding.cardHeartRegen.setVisibility(View.GONE); stopTimer(); return;
        }
        binding.cardHeartRegen.setVisibility(View.VISIBLE); startTimer();
    }

    private void startTimer() {
        stopTimer();
        timerRunnable = new Runnable() {
            @Override public void run() { updateTimerUI(); timerHandler.postDelayed(this, 1000); }
        };
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() { if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable); }

    private void updateTimerUI() {
        if (currentUser == null || currentUser.getLastHeartRegen() == null || binding == null) return;
        long diff = System.currentTimeMillis() - currentUser.getLastHeartRegen().toDate().getTime();
        if (diff >= 5 * 60 * 1000) {
            int newHearts = Math.min(5, currentUser.getHearts() + (int)(diff / (5 * 60 * 1000)));
            currentUser.setHearts(newHearts);
            currentUser.setLastHeartRegen(Timestamp.now());
            updateProfileUI();
        }
    }

    private void updateAvatarUI(String avatarValue) {
        int resId = R.drawable.bear;
        for (int i = 0; i < avatarValues.length; i++) {
            if (avatarValues[i].equals(avatarValue)) { resId = avatarResIds[i]; break; }
        }
        binding.imgProfile.setImageResource(resId);
    }

    @Override public void onResume() { super.onResume(); observeUserData(); }
    @Override public void onDestroyView() { super.onDestroyView(); stopTimer(); if (userListener != null) userListener.remove(); binding = null; }

    // Adapter nội bộ cho dialog chọn chủ đề
    private static class TopicSelectionAdapter extends RecyclerView.Adapter<TopicSelectionAdapter.ViewHolder> {
        private final List<Course> courses;
        private final OnTopicClickListener listener;

        interface OnTopicClickListener { void onTopicClick(Course course); }

        TopicSelectionAdapter(List<Course> courses, OnTopicClickListener listener) {
            this.courses = courses; this.listener = listener;
        }

        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_topic_selection, parent, false);
            return new ViewHolder(view);
        }

        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Course c = courses.get(position);
            holder.title.setText(c.getTitle());
            holder.itemView.setOnClickListener(v -> listener.onTopicClick(c));
        }

        @Override public int getItemCount() { return courses.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title; ViewHolder(View itemView) { super(itemView); title = itemView.findViewById(R.id.text_topic_name); }
        }
    }
}
