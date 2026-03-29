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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.databinding.DialogAddCourseBinding;
import com.example.vocabmaster.databinding.FragmentLibraryBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.example.vocabmaster.ui.study.StudyActivity;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class LibraryFragment extends Fragment {
    private FragmentLibraryBinding binding;
    private LibraryViewModel viewModel;
    private CourseAdapter adapter;
    private final List<Course> allCourses = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(LibraryViewModel.class);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
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

        observeViewModel();
    }

    private void observeViewModel() {
        binding.progressSkeleton.setVisibility(View.VISIBLE);
        viewModel.getCoursesFromFirestore().observe(getViewLifecycleOwner(), courses -> {
            allCourses.clear();
            String uid = FirebaseAuth.getInstance().getUid();
            for (Course c : courses) {
                if (TextUtils.equals(c.getCreatorId(), uid) || c.isPublic()) {
                    allCourses.add(c);
                }
            }
            if (binding != null) {
                binding.progressSkeleton.setVisibility(View.GONE);
                filterCourses(binding.searchView.getQuery().toString());
            }
        });
    }

    private void showAddCourseDialog() {
        DialogAddCourseBinding dialogBinding = DialogAddCourseBinding.inflate(getLayoutInflater());
        new AlertDialog.Builder(requireContext())
                .setTitle("Create new study set")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Create", (dialog, which) -> {
                    String title = Objects.requireNonNull(dialogBinding.editCourseTitle.getText()).toString().trim();
                    String description = Objects.requireNonNull(dialogBinding.editCourseDescription.getText()).toString().trim();
                    int selectedChipId = dialogBinding.chipGroupThemes.getCheckedChipId();
                    
                    if (title.isEmpty()) {
                        UiFeedback.showSnack(binding.getRoot(), "Title cannot be empty");
                        return;
                    }
                    
                    String theme = "";
                    if (selectedChipId != View.NO_ID) {
                        Chip selectedChip = dialogBinding.chipGroupThemes.findViewById(selectedChipId);
                        theme = selectedChip.getText().toString();
                    }

                    Course course = new Course(title, description, theme, FirebaseAuth.getInstance().getUid(), true);
                    viewModel.addCourseToFirestore(course);
                    UiFeedback.showSnack(binding.getRoot(), "Study set created");
                    refreshData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshData() {
        // Since we are using a simple LiveData that fetches once, we might need to re-observe or use a more reactive approach
        // For now, let's just trigger another fetch by re-calling the observe method or updating the LiveData in VM
        observeViewModel();
    }

    private void filterCourses(String query) {
        if (binding == null) return;
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
        
        if (course.getTheme() != null) {
            for (int i = 0; i < dialogBinding.chipGroupThemes.getChildCount(); i++) {
                Chip chip = (Chip) dialogBinding.chipGroupThemes.getChildAt(i);
                if (chip.getText().toString().equals(course.getTheme())) {
                    chip.setChecked(true);
                    break;
                }
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Edit study set")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Save", (dialog, which) -> {
                    course.setTitle(Objects.requireNonNull(dialogBinding.editCourseTitle.getText()).toString().trim());
                    course.setDescription(Objects.requireNonNull(dialogBinding.editCourseDescription.getText()).toString().trim());
                    int selectedChipId = dialogBinding.chipGroupThemes.getCheckedChipId();
                    if (selectedChipId != View.NO_ID) {
                        Chip selectedChip = dialogBinding.chipGroupThemes.findViewById(selectedChipId);
                        course.setTheme(selectedChip.getText().toString());
                    }

                    viewModel.updateCourseInFirestore(course);
                    UiFeedback.showSnack(binding.getRoot(), "Study set updated");
                    refreshData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCourse(Course course) {
        viewModel.deleteCourseFromFirestore(course.getFirestoreId());
        UiFeedback.showSnack(binding.getRoot(), "Study set deleted");
        refreshData();
    }

    private void duplicateCourse(Course course) {
        Course copied = new Course(course.getTitle() + " (copy)", course.getDescription(), course.getTheme(), FirebaseAuth.getInstance().getUid(), false);
        copied.setFlashcardCount(course.getFlashcardCount());
        viewModel.addCourseToFirestore(copied);
        UiFeedback.showSnack(binding.getRoot(), "Study set duplicated");
        refreshData();
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
        FirebaseFirestore.getInstance().collection("users").document(uid).get().addOnSuccessListener(snapshot -> {
            Boolean premium = snapshot.getBoolean("premium");
            if (!Boolean.TRUE.equals(premium)) {
                UiFeedback.showErrorDialog(requireContext(), "Premium required", "Upgrade to Premium to use AI generation.");
                return;
            }
            Course ai = new Course("AI Set: TOEIC High-Frequency Vocabulary", "Auto-generated 60 cards for fast review", "Education", uid, false);
            ai.setFlashcardCount(60);
            viewModel.addCourseToFirestore(ai);
            UiFeedback.showSnack(binding.getRoot(), "AI generated a new study set");
            refreshData();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}