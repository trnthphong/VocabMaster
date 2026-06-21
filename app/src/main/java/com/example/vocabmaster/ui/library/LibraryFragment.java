package com.example.vocabmaster.ui.library;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.local.VocabularyDao;
import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.CourseScheduleDay;
import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.databinding.FragmentLibraryBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.example.vocabmaster.ui.home.CreateTopicActivity;
import com.example.vocabmaster.ui.library.MyTopicsActivity;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LibraryFragment extends Fragment {
    private FragmentLibraryBinding binding;
    private LibraryViewModel viewModel;
    private CourseAdapter roadmapAdapter;
    private final List<Course> roadmapItems = new ArrayList<>();
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private VocabularyDao vocabularyDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(LibraryViewModel.class);
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        setupRoadmapRecyclerView();
        setupClickListeners();
        loadStats();
        loadLibraryData();
        observeFlashcardCount();
    }

    private void setupRoadmapRecyclerView() {
        roadmapAdapter = new CourseAdapter(item -> {
            Intent intent = new Intent(requireContext(), CourseDetailActivity.class);
            intent.putExtra("course_id", item.getFirestoreId());
            intent.putExtra("course_title", item.getTitle());
            intent.putExtra("is_personal", true);
            startActivity(intent);
        }, item -> {}, count -> {});
        
        binding.recyclerCourses.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.recyclerCourses.setAdapter(roadmapAdapter);
    }

    private void setupClickListeners() {
        // Nút New Card
        binding.btnCreateFlashcard.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), CreateFlashcardActivity.class);
            startActivity(intent);
        });

        // Card "My Topics" - Hiển thị personal + downloaded topics
        binding.cardPersonalTopics.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), MyTopicsActivity.class);
            startActivity(intent);
        });

        // Card "New Topic" - Tạo bộ từ mới
        binding.cardDownloadedTopics.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), CreateTopicActivity.class);
            startActivity(intent);
        });

        binding.layoutPersonalFlashcards.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), PersonalCardsActivity.class);
            startActivity(intent);
        });
    }

    private void loadStats() {
        String uid = auth.getUid();
        if (uid == null) return;

        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                User user = doc.toObject(User.class);
                if (user != null && binding != null) {
                    binding.textTotalScore.setText(String.valueOf(user.getXp()));
                    binding.textTotalSessions.setText(String.valueOf(user.getStreak())); 
                }
            }
        });

        // Load số từ đã học từ local database
        if (vocabularyDao == null) {
            vocabularyDao = AppDatabase.getDatabase(requireContext()).vocabularyDao();
        }
        executor.execute(() -> {
            int learnedCount = vocabularyDao.getLearnedWordsCount();
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                if (binding != null) {
                    binding.textTotalCups.setText(String.valueOf(learnedCount));
                }
            });
        });

        binding.textTotalTime.setText("0m");
        binding.textTotalSessions.setText("0");
        loadLearningStats(uid);
    }

    private void loadLearningStats(String uid) {
        executor.execute(() -> {
            List<CourseScheduleDay> schedule = AppDatabase.getDatabase(requireContext())
                    .courseScheduleDayDao()
                    .getScheduleForCourseSync(uid);

            int totalMinutes = 0;
            int completedSessions = 0;
            Set<String> completedLessonIds = new HashSet<>();

            if (schedule != null) {
                for (CourseScheduleDay day : schedule) {
                    if (!"completed".equals(day.getStatus())) continue;
                    completedSessions++;
                    int minutes = day.getActualMinutesSpent() > 0 ? day.getActualMinutesSpent() : day.getDailyMinutesGoal();
                    totalMinutes += Math.max(0, minutes);
                    if (day.getLessonIds() != null) completedLessonIds.addAll(day.getLessonIds());
                }
            }

            int finalTotalMinutes = totalMinutes;
            int finalCompletedSessions = completedSessions;
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                if (binding != null) {
                    binding.textTotalTime.setText(formatStudyTime(finalTotalMinutes));
                    binding.textTotalSessions.setText(String.valueOf(finalCompletedSessions));
                }
                loadWordsLearned(uid, completedLessonIds);
            });
        });
    }

    private void loadWordsLearned(String uid, Set<String> completedLessonIds) {
        if (completedLessonIds.isEmpty()) {
            if (binding != null) binding.textTotalCups.setText("0");
            return;
        }

        db.collection("users").document(uid).collection("personal_courses")
                .get()
                .addOnSuccessListener(courseSnapshots -> {
                    List<Task<QuerySnapshot>> unitTasks = new ArrayList<>();
                    for (DocumentSnapshot courseDoc : courseSnapshots.getDocuments()) {
                        unitTasks.add(courseDoc.getReference().collection("units").get());
                    }

                    Tasks.whenAllSuccess(unitTasks).addOnSuccessListener(unitResults -> {
                        List<Task<QuerySnapshot>> lessonTasks = new ArrayList<>();
                        for (Object result : unitResults) {
                            QuerySnapshot unitSnapshot = (QuerySnapshot) result;
                            for (DocumentSnapshot unitDoc : unitSnapshot.getDocuments()) {
                                lessonTasks.add(unitDoc.getReference().collection("lessons").get());
                            }
                        }

                        if (lessonTasks.isEmpty()) {
                            if (binding != null) binding.textTotalCups.setText("0");
                            return;
                        }

                        Tasks.whenAllSuccess(lessonTasks).addOnSuccessListener(lessonResults -> {
                            Set<String> learnedWords = new HashSet<>();
                            for (Object result : lessonResults) {
                                QuerySnapshot lessonSnapshot = (QuerySnapshot) result;
                                for (DocumentSnapshot lessonDoc : lessonSnapshot.getDocuments()) {
                                    if (!completedLessonIds.contains(lessonDoc.getId())) continue;
                                    List<String> words = (List<String>) lessonDoc.get("vocabWords");
                                    if (words == null) continue;
                                    for (String word : words) {
                                        if (word != null && !word.trim().isEmpty()) {
                                            learnedWords.add(word.trim().toLowerCase());
                                        }
                                    }
                                }
                            }
                            if (binding != null) binding.textTotalCups.setText(String.valueOf(learnedWords.size()));
                        });
                    });
                });
    }

    private String formatStudyTime(int totalMinutes) {
        if (totalMinutes < 60) return totalMinutes + "m";
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        return minutes == 0 ? hours + "h" : hours + "h " + minutes + "m";
    }

    private void loadLibraryData() {
        String uid = auth.getUid();
        if (uid == null) return;

        binding.progressSkeleton.setVisibility(View.VISIBLE);

        db.collection("users").document(uid).collection("personal_courses")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        renderCourses(querySnapshot.getDocuments());
                    } else {
                        loadLibraryDataWithoutUpdatedAt(uid);
                    }
                });
    }

    private void loadLibraryDataWithoutUpdatedAt(String uid) {
        db.collection("users").document(uid).collection("personal_courses")
                .get()
                .addOnSuccessListener(querySnapshot -> renderCourses(querySnapshot.getDocuments()));
    }

    private void renderCourses(List<com.google.firebase.firestore.DocumentSnapshot> docs) {
        if (binding == null) return;
        binding.progressSkeleton.setVisibility(View.GONE);
        roadmapItems.clear();
        for (com.google.firebase.firestore.DocumentSnapshot doc : docs) {
            Course c = doc.toObject(Course.class);
            if (c == null) c = new Course();
            c.setFirestoreId(doc.getId());
            if (c.getTitle() == null) c.setTitle(doc.getString("title"));
            c.setImageUrl(doc.getString("imageUrl"));
            c.setTopic(false);
            roadmapItems.add(c);
        }
        roadmapAdapter.submitList(new ArrayList<>(roadmapItems));
        enrichCourseLessonCounts();
    }

    private void enrichCourseLessonCounts() {
        String uid = auth.getUid();
        if (uid == null || roadmapItems.isEmpty()) return;

        List<Task<Void>> countTasks = new ArrayList<>();
        for (Course course : roadmapItems) {
            if (course.getFirestoreId() == null) continue;
            Task<Void> task = db.collection("users").document(uid)
                    .collection("personal_courses").document(course.getFirestoreId())
                    .collection("units")
                    .get()
                    .continueWithTask(unitTask -> {
                        List<Task<QuerySnapshot>> lessonTasks = new ArrayList<>();
                        if (unitTask.isSuccessful()) {
                            for (DocumentSnapshot unitDoc : unitTask.getResult().getDocuments()) {
                                lessonTasks.add(unitDoc.getReference().collection("lessons").get());
                            }
                        }

                        if (lessonTasks.isEmpty()) {
                            course.setFlashcardCount(0);
                            return Tasks.forResult(null);
                        }

                        return Tasks.whenAllSuccess(lessonTasks).continueWith(done -> {
                            int lessonCount = 0;
                            for (Object result : done.getResult()) {
                                lessonCount += ((QuerySnapshot) result).size();
                            }
                            course.setFlashcardCount(lessonCount);
                            return null;
                        });
                    });
            countTasks.add(task);
        }

        Tasks.whenAllComplete(countTasks).addOnCompleteListener(done -> {
            if (binding != null) roadmapAdapter.submitList(new ArrayList<>(roadmapItems));
        });
    }

    private void observeFlashcardCount() {
        viewModel.getPersonalFlashcards().observe(getViewLifecycleOwner(), flashcards -> {
            if (binding != null) {
                binding.textFlashcardCount.setText(flashcards != null ? flashcards.size() + " cards" : "0 cards");
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            loadStats();
            loadLibraryData();
        }
    }

    @Override
    public void onDestroyView() { super.onDestroyView(); binding = null; }
}
