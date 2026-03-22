package com.example.vocabmaster.ui.library;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.databinding.DialogAddCourseBinding;
import com.example.vocabmaster.databinding.FragmentLibraryBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.example.vocabmaster.ui.study.StudyActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LibraryFragment extends Fragment {
    private FragmentLibraryBinding binding;
    private CourseAdapter adapter;
    private FirebaseFirestore db;
    private final List<Course> allCourses = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = FirebaseFirestore.getInstance();
        adapter = new CourseAdapter(course -> startActivity(new Intent(requireContext(), StudyActivity.class)), this::showCourseActionMenu);
        binding.recyclerCourses.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerCourses.setAdapter(adapter);
        binding.fabAddCourse.setOnClickListener(v -> showAddCourseDialog());
        binding.btnAiCreateCourse.setOnClickListener(v -> createCourseByAi());
        
        binding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterCourses(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterCourses(newText);
                return true;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCourses();
    }

    private void showAddCourseDialog() {
        DialogAddCourseBinding dialogBinding = DialogAddCourseBinding.inflate(getLayoutInflater());
        new AlertDialog.Builder(requireContext())
                .setTitle("Create new study set")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Create", (dialog, which) -> {
                    String title = dialogBinding.editCourseTitle.getText().toString().trim();
                    String description = dialogBinding.editCourseDescription.getText().toString().trim();
                    if (!title.isEmpty()) {
                        Course course = new Course(title, description, FirebaseAuth.getInstance().getUid(), true);
                        db.collection("courses").add(course).addOnSuccessListener(unused -> {
                            UiFeedback.showSnack(binding.getRoot(), "Study set created");
                            loadCourses();
                        });
                    } else {
                        UiFeedback.showSnack(binding.getRoot(), "Title cannot be empty");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadCourses() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        binding.progressSkeleton.setVisibility(View.VISIBLE);
        db.collection("courses").get().addOnSuccessListener(queryDocumentSnapshots -> {
            allCourses.clear();
            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                Course c = doc.toObject(Course.class);
                c.setFirestoreId(doc.getId());
                if (TextUtils.equals(c.getCreatorId(), uid) || c.isPublic()) {
                    allCourses.add(c);
                }
            }
            binding.progressSkeleton.setVisibility(View.GONE);
            filterCourses(binding.searchView.getQuery() == null ? "" : binding.searchView.getQuery().toString());
        });
    }

    private void filterCourses(String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<Course> result = new ArrayList<>();
        for (Course c : allCourses) {
            String t = c.getTitle() == null ? "" : c.getTitle().toLowerCase(Locale.ROOT);
            String d = c.getDescription() == null ? "" : c.getDescription().toLowerCase(Locale.ROOT);
            if (q.isEmpty() || t.contains(q) || d.contains(q)) result.add(c);
        }
        adapter.submitList(new ArrayList<>(result));
        binding.layoutEmptyState.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showCourseActionMenu(Course course) {
        String[] actions = {"Edit", "Delete", "Duplicate", "Share"};
        new AlertDialog.Builder(requireContext())
                .setTitle(course.getTitle())
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) showEditDialog(course);
                    if (which == 1) deleteCourse(course);
                    if (which == 2) duplicateCourse(course);
                    if (which == 3) shareCourse(course);
                })
                .show();
    }

    private void showEditDialog(Course course) {
        DialogAddCourseBinding dialogBinding = DialogAddCourseBinding.inflate(getLayoutInflater());
        dialogBinding.editCourseTitle.setText(course.getTitle());
        dialogBinding.editCourseDescription.setText(course.getDescription());
        new AlertDialog.Builder(requireContext())
                .setTitle("Edit study set")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Save", (dialog, which) -> db.collection("courses")
                        .document(course.getFirestoreId())
                        .update("title", dialogBinding.editCourseTitle.getText().toString().trim(),
                                "description", dialogBinding.editCourseDescription.getText().toString().trim())
                        .addOnSuccessListener(unused -> {
                            UiFeedback.showSnack(binding.getRoot(), "Study set updated");
                            loadCourses();
                        }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCourse(Course course) {
        db.collection("courses").document(course.getFirestoreId()).delete().addOnSuccessListener(unused -> {
            UiFeedback.showSnack(binding.getRoot(), "Study set deleted");
            loadCourses();
        });
    }

    private void duplicateCourse(Course course) {
        Course copied = new Course(course.getTitle() + " (copy)", course.getDescription(), FirebaseAuth.getInstance().getUid(), false);
        copied.setFlashcardCount(course.getFlashcardCount());
        db.collection("courses").add(copied).addOnSuccessListener(unused -> {
            UiFeedback.showSnack(binding.getRoot(), "Study set duplicated");
            loadCourses();
        });
    }

    private void shareCourse(Course course) {
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, "Study set: " + course.getTitle() + "\nDescription: " + course.getDescription());
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Share study set"));
    }

    private void createCourseByAi() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        db.collection("users").document(uid).get().addOnSuccessListener(snapshot -> {
            Boolean premium = snapshot.getBoolean("premium");
            if (!Boolean.TRUE.equals(premium)) {
                UiFeedback.showErrorDialog(requireContext(), "Premium required", "Upgrade to Premium to use AI generation.");
                return;
            }
            Course ai = new Course("AI Set: TOEIC High-Frequency Vocabulary", "Auto-generated 60 cards for fast review", uid, false);
            ai.setFlashcardCount(60);
            db.collection("courses").add(ai).addOnSuccessListener(unused ->
                    UiFeedback.showSnack(binding.getRoot(), "AI generated a new study set"));
            loadCourses();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
