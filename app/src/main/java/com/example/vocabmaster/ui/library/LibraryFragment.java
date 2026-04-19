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

import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.databinding.FragmentLibraryBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.example.vocabmaster.ui.home.AllTopicsActivity;
import com.example.vocabmaster.ui.home.CreateTopicActivity;
import com.example.vocabmaster.ui.home.TopicWordListActivity;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class LibraryFragment extends Fragment {
    private FragmentLibraryBinding binding;
    private LibraryViewModel viewModel;
    private CourseAdapter roadmapAdapter;
    private final List<Course> roadmapItems = new ArrayList<>();
    private FirebaseFirestore db;
    private FirebaseAuth auth;

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
        // Nút New Topic (trên thanh tiêu đề Section)
        binding.btnCreateTopic.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), CreateTopicActivity.class);
            startActivity(intent);
        });

        // Nút New Card (trên thanh tiêu đề Section)
        binding.btnCreateFlashcard.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), CreateFlashcardActivity.class);
            startActivity(intent);
        });

        // Card "My Topics" (bên trái) - Hiển thị toàn bộ topic (Personal + Downloaded)
        binding.cardPersonalTopics.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AllTopicsActivity.class);
            // Có thể thêm flag để AllTopicsActivity biết hiển thị những gì nếu cần
            startActivity(intent);
        });

        // Card "New Topic" (bên phải - thay cho Downloaded cũ)
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

        binding.textTotalCups.setText("12");
        binding.textTotalTime.setText("45h");
    }

    private void loadLibraryData() {
        String uid = auth.getUid();
        if (uid == null) return;

        binding.progressSkeleton.setVisibility(View.VISIBLE);

        db.collection("users").document(uid).collection("personal_courses")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (binding == null) return;
                    binding.progressSkeleton.setVisibility(View.GONE);
                    roadmapItems.clear();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Course c = new Course();
                        c.setFirestoreId(doc.getId());
                        c.setTitle(doc.getString("title"));
                        c.setImageUrl(doc.getString("imageUrl"));
                        c.setTopic(false);
                        roadmapItems.add(c);
                    }
                    roadmapAdapter.submitList(new ArrayList<>(roadmapItems));
                });

        viewModel.getPersonalFlashcards().observe(getViewLifecycleOwner(), flashcards -> {
            if (binding != null) {
                binding.textFlashcardCount.setText(flashcards != null ? flashcards.size() + " cards" : "0 cards");
            }
        });
    }

    @Override
    public void onDestroyView() { super.onDestroyView(); binding = null; }
}
